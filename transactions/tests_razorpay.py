"""Tests for the Razorpay integration, using mocks for the Razorpay SDK
since these run without real network access or live test-mode credentials.
See README.md for how to point this at a real Razorpay test account."""
import hashlib
import hmac
from decimal import Decimal
from unittest.mock import MagicMock, patch

from django.test import TestCase, override_settings
from rest_framework.test import APIClient

from merchants.models import APIKey, Merchant
from transactions.models import Transaction

RAZORPAY_SETTINGS = dict(
    RAZORPAY_KEY_ID="rzp_test_fake",
    RAZORPAY_KEY_SECRET="fake_secret",
    RAZORPAY_WEBHOOK_SECRET="fake_webhook_secret",
)


@override_settings(**RAZORPAY_SETTINGS)
class RazorpayOrderTests(TestCase):
    def setUp(self):
        self.merchant = Merchant.objects.create(business_name="Test Co", email="test@example.com")
        _, raw_key = APIKey.create_for_merchant(self.merchant)
        self.client = APIClient()
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {raw_key}")

    @patch("transactions.razorpay_service.get_client")
    def test_create_order(self, get_client):
        get_client.return_value.order.create.return_value = {"id": "order_fake123"}

        response = self.client.post("/api/v1/razorpay/orders/", {
            "amount": "499.00", "currency": "INR", "customer_email": "buyer@example.com",
        }, format="json")

        self.assertEqual(response.status_code, 201)
        self.assertEqual(response.data["razorpay_order_id"], "order_fake123")
        self.assertEqual(response.data["razorpay_key_id"], "rzp_test_fake")
        self.assertEqual(response.data["amount"], 49900)

        transaction = Transaction.objects.get(pk=response.data["transaction_id"])
        self.assertEqual(transaction.provider, Transaction.Provider.RAZORPAY)
        self.assertEqual(transaction.provider_order_id, "order_fake123")
        self.assertEqual(transaction.status, Transaction.Status.CREATED)

    @patch("transactions.razorpay_service.get_client")
    def test_high_value_order_skips_razorpay_and_requires_review(self, get_client):
        response = self.client.post("/api/v1/razorpay/orders/", {
            "amount": "250000.00", "currency": "INR",
        }, format="json")

        self.assertEqual(response.status_code, 201)
        self.assertEqual(response.data["status"], Transaction.Status.REQUIRES_REVIEW)
        get_client.return_value.order.create.assert_not_called()

    @patch("transactions.razorpay_service.get_client")
    def test_verify_marks_transaction_captured(self, get_client):
        mock_client = get_client.return_value
        mock_client.order.create.return_value = {"id": "order_fake123"}
        mock_client.utility.verify_payment_signature.return_value = True
        mock_client.payment.fetch.return_value = {
            "id": "pay_fake456",
            "status": "captured",
            "method": "card",
            "card": {"last4": "4242", "network": "Visa"},
        }

        create_response = self.client.post("/api/v1/razorpay/orders/", {"amount": "499.00"}, format="json")
        txn_id = create_response.data["transaction_id"]

        verify_response = self.client.post(f"/api/v1/razorpay/orders/{txn_id}/verify/", {
            "razorpay_payment_id": "pay_fake456",
            "razorpay_order_id": "order_fake123",
            "razorpay_signature": "fakesignature",
        }, format="json")

        self.assertEqual(verify_response.status_code, 200)
        self.assertEqual(verify_response.data["status"], Transaction.Status.CAPTURED)
        self.assertEqual(verify_response.data["payment_method"], "card")
        self.assertEqual(verify_response.data["payment_method_details"]["card"]["last4"], "4242")

    @patch("transactions.razorpay_service.get_client")
    def test_verify_rejects_mismatched_order_id(self, get_client):
        get_client.return_value.order.create.return_value = {"id": "order_fake123"}
        create_response = self.client.post("/api/v1/razorpay/orders/", {"amount": "499.00"}, format="json")
        txn_id = create_response.data["transaction_id"]

        response = self.client.post(f"/api/v1/razorpay/orders/{txn_id}/verify/", {
            "razorpay_payment_id": "pay_fake456",
            "razorpay_order_id": "order_WRONG",
            "razorpay_signature": "whatever",
        }, format="json")

        self.assertEqual(response.status_code, 400)

    @patch("transactions.razorpay_service.get_client")
    def test_refund_uses_razorpay_provider(self, get_client):
        mock_client = get_client.return_value
        mock_client.order.create.return_value = {"id": "order_fake123"}
        mock_client.utility.verify_payment_signature.return_value = True
        mock_client.payment.fetch.return_value = {
            "id": "pay_fake456", "status": "captured", "method": "card", "card": {"last4": "4242"},
        }
        mock_client.payment.refund.return_value = {"id": "rfnd_fake789"}

        create_response = self.client.post("/api/v1/razorpay/orders/", {"amount": "500.00"}, format="json")
        txn_id = create_response.data["transaction_id"]
        self.client.post(f"/api/v1/razorpay/orders/{txn_id}/verify/", {
            "razorpay_payment_id": "pay_fake456",
            "razorpay_order_id": "order_fake123",
            "razorpay_signature": "fakesignature",
        }, format="json")

        refund_response = self.client.post(f"/api/v1/payment-intents/{txn_id}/refund/", {
            "amount": "200.00",
        }, format="json")

        self.assertEqual(refund_response.status_code, 201)
        self.assertEqual(refund_response.data["gateway_reference"], "rfnd_fake789")
        mock_client.payment.refund.assert_called_once_with("pay_fake456", {"amount": 20000, "notes": {}})


@override_settings(**RAZORPAY_SETTINGS)
class RazorpayWebhookTests(TestCase):
    def setUp(self):
        self.merchant = Merchant.objects.create(business_name="Test Co", email="test2@example.com")
        self.client = APIClient()

    def _sign(self, body: bytes) -> str:
        return hmac.new(b"fake_webhook_secret", body, hashlib.sha256).hexdigest()

    def test_rejects_invalid_signature(self):
        response = self.client.post(
            "/api/v1/razorpay/webhook/", data=b'{"event": "payment.captured"}',
            content_type="application/json", HTTP_X_RAZORPAY_SIGNATURE="bad-signature",
        )
        self.assertEqual(response.status_code, 400)

    def test_valid_webhook_updates_pending_transaction(self):
        transaction = Transaction.objects.create(
            merchant=self.merchant,
            amount=Decimal("300.00"),
            provider=Transaction.Provider.RAZORPAY,
            provider_order_id="order_webhook1",
        )

        import json
        body = json.dumps({
            "event": "payment.captured",
            "payload": {
                "payment": {
                    "entity": {
                        "id": "pay_webhook1",
                        "order_id": "order_webhook1",
                        "status": "captured",
                        "method": "upi",
                        "vpa": "buyer@upi",
                    }
                }
            },
        }).encode()

        response = self.client.post(
            "/api/v1/razorpay/webhook/", data=body,
            content_type="application/json", HTTP_X_RAZORPAY_SIGNATURE=self._sign(body),
        )

        self.assertEqual(response.status_code, 200)
        transaction.refresh_from_db()
        self.assertEqual(transaction.status, Transaction.Status.CAPTURED)
        self.assertEqual(transaction.provider_payment_id, "pay_webhook1")
