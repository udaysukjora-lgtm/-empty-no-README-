import secrets
import uuid

from django.db import models

from merchants.models import Merchant

EVENT_CHOICES = [
    ("payment.captured", "Payment captured"),
    ("payment.failed", "Payment failed"),
    ("payment.requires_review", "Payment requires review"),
    ("refund.succeeded", "Refund succeeded"),
]


class WebhookEndpoint(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    merchant = models.ForeignKey(Merchant, on_delete=models.CASCADE, related_name="webhook_endpoints")
    url = models.URLField()
    secret = models.CharField(max_length=64, default=secrets.token_hex, editable=False)
    event_types = models.JSONField(default=list, blank=True, help_text="Empty list means subscribe to all events")
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.url} ({self.merchant.business_name})"

    def is_subscribed_to(self, event_type: str) -> bool:
        return not self.event_types or event_type in self.event_types


class WebhookDelivery(models.Model):
    class Status(models.TextChoices):
        PENDING = "pending", "Pending"
        SUCCEEDED = "succeeded", "Succeeded"
        FAILED = "failed", "Failed"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    endpoint = models.ForeignKey(WebhookEndpoint, on_delete=models.CASCADE, related_name="deliveries")
    event_type = models.CharField(max_length=64)
    payload = models.JSONField()
    status = models.CharField(max_length=20, choices=Status.choices, default=Status.PENDING)
    response_status_code = models.IntegerField(null=True, blank=True)
    attempts = models.PositiveIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)
    delivered_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["-created_at"]

    def __str__(self):
        return f"{self.event_type} -> {self.endpoint.url} ({self.status})"
