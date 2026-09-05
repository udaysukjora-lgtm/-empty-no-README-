import hashlib
import hmac
import json
import logging

import requests
from django.utils import timezone

from .models import WebhookDelivery, WebhookEndpoint

logger = logging.getLogger(__name__)

REQUEST_TIMEOUT_SECONDS = 5


def _sign(secret: str, body: bytes) -> str:
    return hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()


def dispatch_event(merchant, event_type: str, payload: dict) -> None:
    """Fans an event out to every active endpoint the merchant has
    subscribed for it, recording a WebhookDelivery per attempt."""
    endpoints = WebhookEndpoint.objects.filter(merchant=merchant, is_active=True)
    for endpoint in endpoints:
        if endpoint.is_subscribed_to(event_type):
            _deliver(endpoint, event_type, payload)


def _deliver(endpoint: WebhookEndpoint, event_type: str, payload: dict) -> WebhookDelivery:
    delivery = WebhookDelivery.objects.create(
        endpoint=endpoint,
        event_type=event_type,
        payload=payload,
    )

    body = json.dumps({"event": event_type, "data": payload}, default=str).encode()
    signature = _sign(endpoint.secret, body)

    try:
        response = requests.post(
            endpoint.url,
            data=body,
            headers={
                "Content-Type": "application/json",
                "X-Gateway-Signature": signature,
            },
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        delivery.response_status_code = response.status_code
        delivery.status = (
            WebhookDelivery.Status.SUCCEEDED if response.ok else WebhookDelivery.Status.FAILED
        )
    except requests.RequestException as exc:
        logger.warning("Webhook delivery to %s failed: %s", endpoint.url, exc)
        delivery.status = WebhookDelivery.Status.FAILED

    delivery.attempts += 1
    delivery.delivered_at = timezone.now()
    delivery.save(update_fields=["response_status_code", "status", "attempts", "delivered_at"])
    return delivery
