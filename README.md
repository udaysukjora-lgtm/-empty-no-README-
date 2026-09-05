# Payment Gateway (Django)

A Django + Django REST Framework backend that models the core building
blocks of a payment gateway: merchant accounts, API-key auth, payment
intents (authorize/capture), refunds, a basic fraud-screening rule set,
and webhook delivery to merchant endpoints.

The default flow (`transactions/services.py`) simulates authorization using
test-mode rules and never talks to a real bank — useful for fast local
testing without any third-party account. A real, PCI-compliant integration
with **Razorpay's test mode** is also included (`transactions/razorpay_service.py`)
for when you have actual test API keys; see [Razorpay integration](#razorpay-integration).

## Features

- **Merchants & API keys** — each merchant gets a secret key (`sk_test_...` /
  `sk_live_...`); only its SHA-256 hash is stored, the raw key is shown once.
- **Payment intents** — create, authorize, capture (immediate or deferred),
  list, and retrieve, scoped per merchant.
- **Refunds** — full or partial, tracked against the original transaction.
- **Fraud screening** — high-value and velocity-based rules flag a
  transaction as `requires_review` instead of processing it.
- **Idempotency** — pass an `Idempotency-Key` header on payment intent
  creation to safely retry a request.
- **Webhooks** — register an endpoint per merchant, events are delivered
  with an HMAC-SHA256 signature (`X-Gateway-Signature`) and every delivery
  attempt is recorded.
- **Razorpay test-mode integration** — real, PCI-compliant checkout via
  Razorpay Checkout (card details never touch this backend), signature
  verification, and Razorpay's own webhooks for capture/refund events.

## Setup

```bash
pip install -r requirements.txt
cp .env.example .env
python manage.py migrate
python manage.py create_merchant "Acme Inc" acme@example.com
python manage.py runserver
```

`create_merchant` prints a test-mode API key — save it, it isn't shown
again.

## API

All endpoints below require `Authorization: Bearer <api_key>`.

### Create a payment intent

```bash
curl -X POST http://localhost:8000/api/v1/payment-intents/ \
  -H "Authorization: Bearer sk_test_..." \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-123" \
  -d '{
        "amount": "499.00",
        "currency": "INR",
        "payment_method": "card",
        "card": {"number": "4242424242424242", "expiry_month": 12, "expiry_year": 2030, "cvv": "123"},
        "customer_email": "buyer@example.com"
      }'
```

Test-mode cards ending in `0002`, `0069`, or `0127` are always declined
(configurable via `TEST_CARD_DECLINE_SUFFIXES` in settings); any other
number succeeds. A UPI `vpa` containing "fail" is always declined.

### Other endpoints

- `GET /api/v1/payment-intents/` — list this merchant's transactions
- `GET /api/v1/payment-intents/{id}/` — retrieve one
- `POST /api/v1/payment-intents/{id}/capture/` — capture a previously
  authorized (uncaptured) intent
- `POST /api/v1/payment-intents/{id}/refund/` — full or partial refund
  (`{"amount": "100.00", "reason": "..."}`, amount optional)
- `GET|POST /api/v1/webhook-endpoints/` — register/list webhook endpoints
- `GET /api/v1/health/` — unauthenticated health check

## Razorpay integration

This backend never sees a real card number for Razorpay-provider
transactions — Razorpay Checkout collects card/UPI/netbanking details
directly in the browser and hands back a payment id, keeping this
service out of PCI-DSS card-data scope (SAQ-A level).

**Setup:**
1. Sign up at [razorpay.com](https://razorpay.com) and grab your **test
   mode** API key id + secret from *Settings → API Keys*.
2. Set `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` in `.env`.
3. In *Settings → Webhooks*, add `https://<your-host>/api/v1/razorpay/webhook/`
   subscribed to `payment.captured`, `payment.failed`, and `refund.processed`;
   copy the signing secret it gives you into `RAZORPAY_WEBHOOK_SECRET`.

**Flow:**

```bash
# 1. Create an order (server-side, with your merchant API key)
curl -X POST http://localhost:8000/api/v1/razorpay/orders/ \
  -H "Authorization: Bearer sk_test_..." -H "Content-Type: application/json" \
  -d '{"amount": "499.00", "currency": "INR", "customer_email": "buyer@example.com"}'
# -> {"transaction_id": "...", "razorpay_order_id": "order_...", "razorpay_key_id": "rzp_test_...", "amount": 49900, "currency": "INR"}
```

2. Hand `razorpay_order_id`, `razorpay_key_id`, and `amount` to Razorpay
   Checkout in the browser (see `transactions/templates/transactions/razorpay_checkout_test.html`
   for a minimal working example, served at `/razorpay/checkout-test/` for
   manual testing — [Razorpay's Checkout docs](https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/)
   cover the client-side integration).
3. After the user pays, Checkout's success handler receives
   `razorpay_payment_id` / `razorpay_order_id` / `razorpay_signature`. POST
   those to `/api/v1/razorpay/orders/{transaction_id}/verify/` — this
   verifies the signature, fetches the payment from Razorpay, and marks the
   transaction captured (or authorized, if the order was created with
   `auto_capture: false`, in which case use the regular
   `/payment-intents/{id}/capture/` endpoint afterwards).
4. `/payment-intents/{id}/refund/` works the same for Razorpay-provider
   transactions as simulated ones — it calls Razorpay's refund API instead
   of faking a reference.
5. `/api/v1/razorpay/webhook/` independently keeps a transaction in sync if
   it's still `created` (e.g. the browser closed before your frontend could
   call `/verify/`) — every handler is idempotent so whichever path
   (verify call or webhook) arrives first wins.

Test-suite coverage for this flow (`transactions/tests_razorpay.py`) mocks
the Razorpay SDK, since it runs without real credentials or network access.

## Project layout

```
config/           settings, root urls, DRF exception formatting
merchants/        Merchant + APIKey models, header-based auth
transactions/     Transaction/Refund models, simulated + Razorpay processors, fraud rules
webhooks/         WebhookEndpoint/WebhookDelivery models, signed delivery
```

## Suggested next steps

- Add more real PSPs alongside Razorpay (Stripe, PayU, Cashfree)
- Move webhook delivery to a background task queue (Celery) with retries
- Add merchant settlement/payout tracking and reconciliation reports
- Add rate limiting and a proper admin-facing merchant dashboard
