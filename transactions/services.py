"""Simulated payment processing.

There is no real bank/card-network integration here. This module models
the shape of one (authorization, fraud screening, capture, refunds) using
deterministic test rules, the same way real gateways provide "test mode"
card numbers that always succeed or always decline.
"""
import uuid
from datetime import timedelta
from decimal import Decimal

from django.conf import settings
from django.utils import timezone

from webhooks.services import dispatch_event

from .models import Refund, Transaction

HIGH_VALUE_THRESHOLD = Decimal("200000")
VELOCITY_WINDOW_MINUTES = 5
VELOCITY_MAX_ATTEMPTS = 5


def _fake_gateway_reference() -> str:
    return f"gw_{uuid.uuid4().hex[:20]}"


def run_fraud_checks(merchant, amount: Decimal, customer_email: str) -> str | None:
    """Returns a reason string if the transaction should be flagged for
    review, otherwise None."""
    if amount >= HIGH_VALUE_THRESHOLD:
        return "amount exceeds high-value threshold"

    window_start = timezone.now() - timedelta(minutes=VELOCITY_WINDOW_MINUTES)
    recent_count = Transaction.objects.filter(
        merchant=merchant,
        customer_email=customer_email,
        created_at__gte=window_start,
    ).exclude(customer_email="").count()

    if customer_email and recent_count >= VELOCITY_MAX_ATTEMPTS:
        return "too many transaction attempts from this customer"

    return None


def _authorize_card(card: dict) -> tuple[bool, str | None]:
    number = card.get("number", "")
    for suffix in settings.TEST_CARD_DECLINE_SUFFIXES:
        if number.endswith(suffix):
            return False, "card_declined"
    return True, None


def _authorize_upi(upi: dict) -> tuple[bool, str | None]:
    vpa = upi.get("vpa", "")
    if "fail" in vpa.lower():
        return False, "upi_authorization_failed"
    return True, None


def authorize(transaction: Transaction, payment_details: dict) -> None:
    """Runs fraud checks then attempts authorization, mutating and saving
    the transaction's status in place."""
    review_reason = run_fraud_checks(
        transaction.merchant, transaction.amount, transaction.customer_email
    )
    if review_reason:
        transaction.status = Transaction.Status.REQUIRES_REVIEW
        transaction.failure_reason = review_reason
        transaction.save(update_fields=["status", "failure_reason", "updated_at"])
        dispatch_event(transaction.merchant, "payment.requires_review", _transaction_payload(transaction))
        return

    if transaction.payment_method == Transaction.PaymentMethod.CARD:
        success, reason = _authorize_card(payment_details.get("card", {}))
    elif transaction.payment_method == Transaction.PaymentMethod.UPI:
        success, reason = _authorize_upi(payment_details.get("upi", {}))
    else:
        success, reason = True, None

    if success:
        transaction.status = Transaction.Status.AUTHORIZED
        transaction.gateway_reference = _fake_gateway_reference()
    else:
        transaction.status = Transaction.Status.FAILED
        transaction.failure_reason = reason

    transaction.save(update_fields=["status", "gateway_reference", "failure_reason", "updated_at"])

    if transaction.status == Transaction.Status.FAILED:
        dispatch_event(transaction.merchant, "payment.failed", _transaction_payload(transaction))


def capture(transaction: Transaction) -> None:
    if transaction.status != Transaction.Status.AUTHORIZED:
        raise ValueError(f"Cannot capture a transaction in status '{transaction.status}'")
    transaction.status = Transaction.Status.CAPTURED
    transaction.save(update_fields=["status", "updated_at"])
    dispatch_event(transaction.merchant, "payment.captured", _transaction_payload(transaction))


def refund(transaction: Transaction, amount: Decimal, reason: str = "") -> Refund:
    if transaction.status not in (Transaction.Status.CAPTURED, Transaction.Status.PARTIALLY_REFUNDED):
        raise ValueError(f"Cannot refund a transaction in status '{transaction.status}'")
    if amount <= 0 or amount > transaction.amount_refundable:
        raise ValueError("Refund amount exceeds the refundable balance")

    refund_obj = Refund.objects.create(
        transaction=transaction,
        amount=amount,
        reason=reason,
        status=Refund.Status.SUCCEEDED,
        gateway_reference=_fake_gateway_reference(),
    )

    transaction.amount_refunded += amount
    transaction.status = (
        Transaction.Status.REFUNDED
        if transaction.amount_refunded >= transaction.amount
        else Transaction.Status.PARTIALLY_REFUNDED
    )
    transaction.save(update_fields=["amount_refunded", "status", "updated_at"])
    dispatch_event(
        transaction.merchant,
        "refund.succeeded",
        {
            "refund_id": str(refund_obj.id),
            "transaction_id": str(transaction.id),
            "amount": str(refund_obj.amount),
            "reason": refund_obj.reason,
        },
    )
    return refund_obj


def _transaction_payload(transaction: Transaction) -> dict:
    return {
        "transaction_id": str(transaction.id),
        "amount": str(transaction.amount),
        "currency": transaction.currency,
        "status": transaction.status,
        "failure_reason": transaction.failure_reason,
        "gateway_reference": transaction.gateway_reference,
    }
