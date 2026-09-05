from django.utils import timezone
from rest_framework import authentication, exceptions

from .models import APIKey


class AuthenticatedMerchant:
    """Wraps a Merchant so it satisfies DRF's IsAuthenticated check
    (`request.user.is_authenticated`) without needing a Django User model."""

    is_authenticated = True

    def __init__(self, merchant):
        self.merchant = merchant

    def __getattr__(self, item):
        return getattr(self.merchant, item)


class APIKeyAuthentication(authentication.BaseAuthentication):
    """Authenticates requests carrying `Authorization: Bearer sk_<mode>_<token>`.

    On success, request.user is an AuthenticatedMerchant wrapper and
    request.merchant / request.auth are set to the authenticated Merchant
    and APIKey respectively.
    """

    keyword = "Bearer"

    def authenticate(self, request):
        auth_header = authentication.get_authorization_header(request).decode("utf-8")
        if not auth_header:
            return None

        parts = auth_header.split()
        if len(parts) != 2 or parts[0] != self.keyword:
            raise exceptions.AuthenticationFailed("Invalid Authorization header format. Expected: Bearer <api_key>")

        raw_key = parts[1]
        prefix = raw_key[:12]

        try:
            api_key = APIKey.objects.select_related("merchant").get(prefix=prefix, is_active=True)
        except APIKey.DoesNotExist:
            raise exceptions.AuthenticationFailed("Invalid API key")

        if not api_key.matches(raw_key):
            raise exceptions.AuthenticationFailed("Invalid API key")

        if not api_key.merchant.is_active:
            raise exceptions.AuthenticationFailed("Merchant account is inactive")

        APIKey.objects.filter(pk=api_key.pk).update(last_used_at=timezone.now())

        request.merchant = api_key.merchant
        return (AuthenticatedMerchant(api_key.merchant), api_key)
