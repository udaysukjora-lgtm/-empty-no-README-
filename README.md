# Payment Gateway (Django)

A Django + Django REST Framework backend that models the core building
blocks of a payment gateway: merchant accounts, API-key auth, payment
intents (authorize/capture), refunds, a basic fraud-screening rule set,
and webhook delivery to merchant endpoints.

There is no real bank/card-network integration — `transactions/services.py`
simulates authorization using test-mode rules (similar to how Stripe/Razorpay
test cards work), so this is meant as a foundation to build on, not a
PCI-compliant production system.

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

## Project layout

```
config/           settings, root urls, DRF exception formatting
merchants/        Merchant + APIKey models, header-based auth
transactions/     Transaction/Refund models, simulated processor, fraud rules
webhooks/         WebhookEndpoint/WebhookDelivery models, signed delivery
```

## Suggested next steps

- Swap the simulated processor for a real acquirer/PSP integration
- Move webhook delivery to a background task queue (Celery) with retries
- Add merchant settlement/payout tracking and reconciliation reports
- Add rate limiting and a proper admin-facing merchant dashboard
