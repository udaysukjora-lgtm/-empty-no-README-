from rest_framework import viewsets

from .models import WebhookEndpoint
from .serializers import WebhookEndpointSerializer


class WebhookEndpointViewSet(viewsets.ModelViewSet):
    serializer_class = WebhookEndpointSerializer
    http_method_names = ["get", "post", "patch", "delete"]

    def get_queryset(self):
        return WebhookEndpoint.objects.filter(merchant=self.request.merchant)

    def perform_create(self, serializer):
        serializer.save(merchant=self.request.merchant)
