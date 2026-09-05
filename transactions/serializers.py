from decimal import Decimal

from rest_framework import serializers

from .models import Refund, Transaction


class CardDetailsSerializer(serializers.Serializer):
    number = serializers.CharField()
    expiry_month = serializers.IntegerField(min_value=1, max_value=12)
    expiry_year = serializers.IntegerField()
    cvv = serializers.CharField()


class UPIDetailsSerializer(serializers.Serializer):
    vpa = serializers.CharField()


class PaymentIntentCreateSerializer(serializers.Serializer):
    amount = serializers.DecimalField(max_digits=12, decimal_places=2, min_value=Decimal("0.01"))
    currency = serializers.CharField(max_length=3, default="INR")
    payment_method = serializers.ChoiceField(choices=Transaction.PaymentMethod.choices)
    card = CardDetailsSerializer(required=False)
    upi = UPIDetailsSerializer(required=False)
    capture = serializers.BooleanField(default=True)
    customer_email = serializers.EmailField(required=False, allow_blank=True, default="")
    customer_reference = serializers.CharField(required=False, allow_blank=True, default="")
    description = serializers.CharField(required=False, allow_blank=True, default="")
    metadata = serializers.JSONField(required=False, default=dict)

    def validate(self, attrs):
        method = attrs["payment_method"]
        if method == Transaction.PaymentMethod.CARD and "card" not in attrs:
            raise serializers.ValidationError({"card": "This field is required for card payments."})
        if method == Transaction.PaymentMethod.UPI and "upi" not in attrs:
            raise serializers.ValidationError({"upi": "This field is required for UPI payments."})
        return attrs

    def to_masked_details(self) -> dict:
        data = self.validated_data
        if data["payment_method"] == Transaction.PaymentMethod.CARD:
            card = data["card"]
            return {"card": {"last4": card["number"][-4:], "expiry_month": card["expiry_month"], "expiry_year": card["expiry_year"]}}
        if data["payment_method"] == Transaction.PaymentMethod.UPI:
            return {"upi": {"vpa": data["upi"]["vpa"]}}
        return {}


class RefundSerializer(serializers.ModelSerializer):
    class Meta:
        model = Refund
        fields = ["id", "transaction", "amount", "reason", "status", "gateway_reference", "created_at"]
        read_only_fields = fields


class TransactionSerializer(serializers.ModelSerializer):
    refunds = RefundSerializer(many=True, read_only=True)

    class Meta:
        model = Transaction
        fields = [
            "id",
            "amount",
            "amount_refunded",
            "currency",
            "status",
            "payment_method",
            "payment_method_details",
            "customer_email",
            "customer_reference",
            "description",
            "metadata",
            "gateway_reference",
            "failure_reason",
            "created_at",
            "updated_at",
            "refunds",
        ]
        read_only_fields = fields


class RefundCreateSerializer(serializers.Serializer):
    amount = serializers.DecimalField(max_digits=12, decimal_places=2, min_value=Decimal("0.01"), required=False)
    reason = serializers.CharField(required=False, allow_blank=True, default="")
