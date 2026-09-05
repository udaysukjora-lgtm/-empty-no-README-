from decimal import Decimal

from rest_framework import serializers

from .models import Transaction


class RazorpayOrderCreateSerializer(serializers.Serializer):
    amount = serializers.DecimalField(max_digits=12, decimal_places=2, min_value=Decimal("0.01"))
    currency = serializers.CharField(max_length=3, default="INR")
    auto_capture = serializers.BooleanField(default=True)
    customer_email = serializers.EmailField(required=False, allow_blank=True, default="")
    customer_reference = serializers.CharField(required=False, allow_blank=True, default="")
    description = serializers.CharField(required=False, allow_blank=True, default="")
    metadata = serializers.JSONField(required=False, default=dict)


class RazorpayVerifySerializer(serializers.Serializer):
    razorpay_payment_id = serializers.CharField()
    razorpay_order_id = serializers.CharField()
    razorpay_signature = serializers.CharField()


class RazorpayOrderResponseSerializer(serializers.Serializer):
    transaction_id = serializers.UUIDField(source="id")
    razorpay_order_id = serializers.CharField(source="provider_order_id")
    razorpay_key_id = serializers.SerializerMethodField()
    amount = serializers.SerializerMethodField()
    currency = serializers.CharField()
    status = serializers.CharField()

    def get_razorpay_key_id(self, obj):
        from django.conf import settings
        return settings.RAZORPAY_KEY_ID

    def get_amount(self, obj: Transaction):
        return obj.amount_in_subunits()
