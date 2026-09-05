from rest_framework.routers import DefaultRouter

from .views import WebhookEndpointViewSet

router = DefaultRouter()
router.register("webhook-endpoints", WebhookEndpointViewSet, basename="webhook-endpoint")

urlpatterns = router.urls
