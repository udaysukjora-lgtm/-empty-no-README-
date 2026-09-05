from decimal import Decimal

from django.test import TestCase
from rest_framework.test import APIClient

from merchants.models import APIKey, Merchant
from transactions.models import Transaction

CARD_SUCCESS = {"number": "4242424242424242", "expiry_month": 12, "expiry_year": 2030, "cvv": "123"}
CARD_DECLINE = {"number": "4000000000000002", "expiry_month": 12, "expiry_year": 2030, "cvv": "123"}


class PaymentIntentTests(TestCase):
    def setUp(self):
        self.merchant = Merchant.objects.create(business_name="Test Co", email="test@example.com")
        self.api_key, self.raw_key = APIKey.create_for_merchant(self.merchant)
        self.client = APIClient()
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.raw_key}")

    def _create_payment(self, **overrides):
        payload = {
            "amount": "499.00",
            "currency": "INR",
            "payment_method": "card",
            "card": CARD_SUCCESS,
        }
        payload.update(overrides)
        return self.client.post("/api/v1/payment-intents/", payload, format="json")

    def test_requires_authentication(self):
        client = APIClient()
        response = client.get("/api/v1/payment-intents/")
        self.assertEqual(response.status_code, 403)

    def test_rejects_invalid_api_key(self):
        client = APIClient()
        client.credentials(HTTP_AUTHORIZATION="Bearer sk_test_invalid")
        response = client.get("/api/v1/payment-intents/")
        self.assertEqual(response.status_code, 403)

    def test_successful_card_payment_is_captured(self):
        response = self._create_payment()
        self.assertEqual(response.status_code, 201)
        self.assertEqual(response.data["status"], Transaction.Status.CAPTURED)
        self.assertEqual(response.data["payment_method_details"]["card"]["last4"], "4242")

    def test_declined_card_fails(self):
        response = self._create_payment(card=CARD_DECLINE)
        self.assertEqual(response.status_code, 201)
        self.assertEqual(response.data["status"], Transaction.Status.FAILED)
        self.assertEqual(response.data["failure_reason"], "card_declined")

    def test_high_value_payment_requires_review(self):
        response = self._create_payment(amount="250000.00")
        self.assertEqual(response.status_code, 201)
        self.assertEqual(response.data["status"], Transaction.Status.REQUIRES_REVIEW)

    def test_deferred_capture(self):
        response = self._create_payment(capture=False)
        self.assertEqual(response.data["status"], Transaction.Status.AUTHORIZED)

        txn_id = response.data["id"]
        capture_response = self.client.post(f"/api/v1/payment-intents/{txn_id}/capture/")
        self.assertEqual(capture_response.status_code, 200)
        self.assertEqual(capture_response.data["status"], Transaction.Status.CAPTURED)

    def test_idempotency_key_returns_existing_transaction(self):
        headers = {"HTTP_IDEMPOTENCY_KEY": "order-42"}
        first = self.client.post("/api/v1/payment-intents/", {
            "amount": "10.00", "currency": "INR", "payment_method": "card", "card": CARD_SUCCESS,
        }, format="json", **headers)
        second = self.client.post("/api/v1/payment-intents/", {
            "amount": "10.00", "currency": "INR", "payment_method": "card", "card": CARD_SUCCESS,
        }, format="json", **headers)
        self.assertEqual(first.data["id"], second.data["id"])
        self.assertEqual(Transaction.objects.count(), 1)

    def test_partial_refund(self):
        created = self._create_payment(amount="200.00")
        txn_id = created.data["id"]

        refund_response = self.client.post(
            f"/api/v1/payment-intents/{txn_id}/refund/", {"amount": "50.00"}, format="json"
        )
        self.assertEqual(refund_response.status_code, 201)

        txn = Transaction.objects.get(pk=txn_id)
        self.assertEqual(txn.status, Transaction.Status.PARTIALLY_REFUNDED)
        self.assertEqual(txn.amount_refunded, Decimal("50.00"))

    def test_refund_cannot_exceed_remaining_balance(self):
        created = self._create_payment(amount="50.00")
        txn_id = created.data["id"]

        response = self.client.post(
            f"/api/v1/payment-intents/{txn_id}/refund/", {"amount": "1000.00"}, format="json"
        )
        self.assertEqual(response.status_code, 409)

    def test_merchant_cannot_see_other_merchants_transactions(self):
        self._create_payment()

        other_merchant = Merchant.objects.create(business_name="Other Co", email="other@example.com")
        _, other_raw_key = APIKey.create_for_merchant(other_merchant)
        other_client = APIClient()
        other_client.credentials(HTTP_AUTHORIZATION=f"Bearer {other_raw_key}")

        response = other_client.get("/api/v1/payment-intents/")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 0)
