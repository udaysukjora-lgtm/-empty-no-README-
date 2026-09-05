import hashlib
import secrets
import uuid

from django.db import models


class Merchant(models.Model):
    class KYCStatus(models.TextChoices):
        PENDING = "pending", "Pending"
        VERIFIED = "verified", "Verified"
        REJECTED = "rejected", "Rejected"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    business_name = models.CharField(max_length=255)
    email = models.EmailField(unique=True)
    kyc_status = models.CharField(max_length=20, choices=KYCStatus.choices, default=KYCStatus.PENDING)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return self.business_name


def _generate_raw_key(mode: str) -> str:
    return f"sk_{mode}_{secrets.token_urlsafe(32)}"


def _hash_key(raw_key: str) -> str:
    return hashlib.sha256(raw_key.encode()).hexdigest()


class APIKey(models.Model):
    class Mode(models.TextChoices):
        TEST = "test", "Test"
        LIVE = "live", "Live"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    merchant = models.ForeignKey(Merchant, on_delete=models.CASCADE, related_name="api_keys")
    mode = models.CharField(max_length=10, choices=Mode.choices, default=Mode.TEST)
    prefix = models.CharField(max_length=16, unique=True, db_index=True)
    hashed_key = models.CharField(max_length=64)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    last_used_at = models.DateTimeField(null=True, blank=True)

    def __str__(self):
        return f"{self.prefix}... ({self.merchant.business_name})"

    @classmethod
    def create_for_merchant(cls, merchant: "Merchant", mode: str = Mode.TEST):
        """Creates a new key and returns (APIKey instance, raw_key). The raw
        key is only ever available at creation time, matching how real
        payment gateways issue secret keys."""
        raw_key = _generate_raw_key(mode)
        api_key = cls.objects.create(
            merchant=merchant,
            mode=mode,
            prefix=raw_key[:12],
            hashed_key=_hash_key(raw_key),
        )
        return api_key, raw_key

    def matches(self, raw_key: str) -> bool:
        return secrets.compare_digest(self.hashed_key, _hash_key(raw_key))
