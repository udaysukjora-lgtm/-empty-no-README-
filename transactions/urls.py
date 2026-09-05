from django.urls import path
from rest_framework.routers import DefaultRouter

from .razorpay_views import RazorpayOrderCreateView, RazorpayVerifyView, RazorpayWebhookView
from .views import PaymentIntentViewSet

router = DefaultRouter()
router.register("payment-intents", PaymentIntentViewSet, basename="payment-intent")

urlpatterns = router.urls + [
    path("razorpay/orders/", RazorpayOrderCreateView.as_view(), name="razorpay-order-create"),
    path("razorpay/orders/<uuid:pk>/verify/", RazorpayVerifyView.as_view(), name="razorpay-order-verify"),
    path("razorpay/webhook/", RazorpayWebhookView.as_view(), name="razorpay-webhook"),
]
