"""Real test-mode integration with Razorpay.

Unlike transactions/services.py (which simulates a processor with
deterministic test rules), this module talks to Razorpay's actual API using
test-mode credentials (`rzp_test_...` key id + matching key secret from the
Razorpay dashboard). Card/UPI/etc. details are never seen by this backend —
Razorpay Checkout collects them directly in the browser and hands back a
payment id, which is all this module ever deals with.

Flow:
  1. create_order()      -> POST /orders on Razorpay, store the order id.
  2. (browser opens Razorpay Checkout with that order id, user pays)
  3. verify_and_process() -> checks the signature Checkout returns, fetches
     the payment, and marks the transaction authorized/captured/failed.
  4. Razorpay also calls our webhook endpoint independently for the same
     events (handle_webhook_event) — kept idempotent so whichever arrives
     first wins and the second is a no-op.
"""
import hashlib
import hmac
import logging

import razorpay
from django.conf import settings

from webhooks.services import dispatch_event

from .models import Transaction

logger = logging.getLogger(__name__)


class RazorpayError(Exception):
    pass


def get_client() -> razorpay.Client:
    if not settings.RAZORPAY_KEY_ID or not settings.RAZORPAY_KEY_SECRET:
        raise RazorpayError(
            "Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET "
            "(test-mode keys from the Razorpay dashboard) in your environment."
        )
    return razorpay.Client(auth=(settings.RAZORPAY_KEY_ID, settings.RAZORPAY_KEY_SECRET))


def create_order(transaction: Transaction, auto_capture: bool = True) -> None:
    """Creates a Razorpay order for the transaction and stores its id.
    The transaction stays in `created` status until the browser completes
    checkout and verify_and_process() (or the webhook) runs."""
    client = get_client()
    order = client.order.create({
        "amount": transaction.amount_in_subunits(),
        "currency": transaction.currency,
        "receipt": str(transaction.id),
        "payment_capture": 1 if auto_capture else 0,
        "notes": {"transaction_id": str(transaction.id), "merchant_id": str(transaction.merchant_id)},
    })
    transaction.provider_order_id = order["id"]
    transaction.save(update_fields=["provider_order_id", "updated_at"])


def verify_and_process(transaction: Transaction, payment_id: str, order_id: str, signature: str) -> None:
    if transaction.provider_order_id != order_id:
        raise RazorpayError("order_id does not match this transaction")

    client = get_client()
    try:
        client.utility.verify_payment_signature({
            "razorpay_order_id": order_id,
            "razorpay_payment_id": payment_id,
            "razorpay_signature": signature,
        })
    except razorpay.errors.SignatureVerificationError as exc:
        raise RazorpayError(f"Signature verification failed: {exc}") from exc

    payment = client.payment.fetch(payment_id)
    _apply_payment_state(transaction, payment)


def capture_payment(transaction: Transaction) -> None:
    """Captures a previously authorized (payment_capture=0) Razorpay payment."""
    client = get_client()
    payment = client.payment.capture(
        transaction.provider_payment_id, transaction.amount_in_subunits()
    )
    _apply_payment_state(transaction, payment)


def refund_payment(transaction, amount, reason: str = "") -> str:
    """Issues a refund via Razorpay and returns its refund id."""
    client = get_client()
    refund = client.payment.refund(transaction.provider_payment_id, {
        "amount": transaction.amount_in_subunits(amount),
        "notes": {"reason": reason} if reason else {},
    })
    return refund["id"]


def _apply_payment_state(transaction: Transaction, payment: dict) -> None:
    transaction.provider_payment_id = payment["id"]
    transaction.payment_method = _map_method(payment.get("method"))
    transaction.payment_method_details = _mask_payment(payment)

    razorpay_status = payment.get("status")
    if razorpay_status == "captured":
        transaction.status = Transaction.Status.CAPTURED
        transaction.gateway_reference = payment["id"]
        event = "payment.captured"
    elif razorpay_status == "authorized":
        transaction.status = Transaction.Status.AUTHORIZED
        transaction.gateway_reference = payment["id"]
        event = None
    else:
        transaction.status = Transaction.Status.FAILED
        transaction.failure_reason = payment.get("error_description") or razorpay_status or "payment_failed"
        event = "payment.failed"

    transaction.save(update_fields=[
        "provider_payment_id", "payment_method", "payment_method_details",
        "status", "gateway_reference", "failure_reason", "updated_at",
    ])

    if event:
        dispatch_event(transaction.merchant, event, {
            "transaction_id": str(transaction.id),
            "amount": str(transaction.amount),
            "currency": transaction.currency,
            "status": transaction.status,
            "provider_payment_id": transaction.provider_payment_id,
        })


def _map_method(razorpay_method: str | None) -> str:
    return {
        "card": Transaction.PaymentMethod.CARD,
        "upi": Transaction.PaymentMethod.UPI,
        "netbanking": Transaction.PaymentMethod.NETBANKING,
        "wallet": Transaction.PaymentMethod.WALLET,
    }.get(razorpay_method, "")


def _mask_payment(payment: dict) -> dict:
    """Keeps only non-sensitive display fields from Razorpay's payment object."""
    method = payment.get("method")
    if method == "card" and payment.get("card"):
        card = payment["card"]
        return {"card": {"last4": card.get("last4"), "network": card.get("network")}}
    if method == "upi" and payment.get("vpa"):
        return {"upi": {"vpa": payment["vpa"]}}
    if method == "bank":
        return {"netbanking": {"bank": payment.get("bank")}}
    if method == "wallet":
        return {"wallet": {"name": payment.get("wallet")}}
    return {}


def verify_webhook_signature(raw_body: bytes, signature: str) -> bool:
    if not settings.RAZORPAY_WEBHOOK_SECRET:
        raise RazorpayError("RAZORPAY_WEBHOOK_SECRET is not configured")
    expected = hmac.new(settings.RAZORPAY_WEBHOOK_SECRET.encode(), raw_body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature or "")


def handle_webhook_event(event: dict) -> None:
    """Processes a Razorpay webhook payload. Idempotent: if verify_and_process
    already moved the transaction out of `created`, this is a no-op."""
    event_type = event.get("event", "")
    payload = event.get("payload", {})

    if event_type.startswith("payment."):
        payment_entity = payload.get("payment", {}).get("entity", {})
        order_id = payment_entity.get("order_id")
        if not order_id:
            return
        transaction = Transaction.objects.filter(
            provider=Transaction.Provider.RAZORPAY, provider_order_id=order_id
        ).first()
        if transaction and transaction.status == Transaction.Status.CREATED:
            _apply_payment_state(transaction, payment_entity)

    elif event_type == "refund.processed":
        refund_entity = payload.get("refund", {}).get("entity", {})
        payment_id = refund_entity.get("payment_id")
        transaction = Transaction.objects.filter(
            provider=Transaction.Provider.RAZORPAY, provider_payment_id=payment_id
        ).first()
        if not transaction:
            return
        from .models import Refund as RefundModel
        already_recorded = transaction.refunds.filter(gateway_reference=refund_entity.get("id")).exists()
        if already_recorded:
            return
        from decimal import Decimal
        amount = Decimal(refund_entity.get("amount", 0)) / 100
        RefundModel.objects.create(
            transaction=transaction,
            amount=amount,
            status=RefundModel.Status.SUCCEEDED,
            gateway_reference=refund_entity.get("id", ""),
        )
        transaction.amount_refunded += amount
        transaction.status = (
            Transaction.Status.REFUNDED
            if transaction.amount_refunded >= transaction.amount
            else Transaction.Status.PARTIALLY_REFUNDED
        )
        transaction.save(update_fields=["amount_refunded", "status", "updated_at"])
