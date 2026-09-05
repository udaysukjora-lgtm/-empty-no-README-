from rest_framework import serializers

from .models import WebhookEndpoint


class WebhookEndpointSerializer(serializers.ModelSerializer):
    class Meta:
        model = WebhookEndpoint
        fields = ["id", "url", "secret", "event_types", "is_active", "created_at"]
        read_only_fields = ["id", "secret", "created_at"]
