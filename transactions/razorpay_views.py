from django.shortcuts import get_object_or_404
from rest_framework import permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView

from . import razorpay_service, services
from .models import Transaction
from .razorpay_serializers import (
    RazorpayOrderCreateSerializer,
    RazorpayOrderResponseSerializer,
    RazorpayVerifySerializer,
)
from .serializers import TransactionSerializer


class RazorpayOrderCreateView(APIView):
    """Creates a local transaction plus a matching Razorpay order. The
    response is what a checkout page needs to open Razorpay Checkout
    (order id + key id + amount in paise)."""

    def post(self, request):
        serializer = RazorpayOrderCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        transaction = Transaction.objects.create(
            merchant=request.merchant,
            amount=data["amount"],
            currency=data["currency"],
            provider=Transaction.Provider.RAZORPAY,
            customer_email=data["customer_email"],
            customer_reference=data["customer_reference"],
            description=data["description"],
            metadata=data["metadata"],
        )

        review_reason = services.run_fraud_checks(
            transaction.merchant, transaction.amount, transaction.customer_email
        )
        if review_reason:
            transaction.status = Transaction.Status.REQUIRES_REVIEW
            transaction.failure_reason = review_reason
            transaction.save(update_fields=["status", "failure_reason", "updated_at"])
            return Response(TransactionSerializer(transaction).data, status=status.HTTP_201_CREATED)

        try:
            razorpay_service.create_order(transaction, auto_capture=data["auto_capture"])
        except razorpay_service.RazorpayError as exc:
            transaction.delete()
            return Response({"error": {"message": str(exc)}}, status=status.HTTP_502_BAD_GATEWAY)

        return Response(RazorpayOrderResponseSerializer(transaction).data, status=status.HTTP_201_CREATED)


class RazorpayVerifyView(APIView):
    """Called by your frontend after Razorpay Checkout succeeds, with the
    payment id/order id/signature Checkout's success handler receives."""

    def post(self, request, pk=None):
        transaction = get_object_or_404(
            Transaction.objects.filter(merchant=request.merchant, provider=Transaction.Provider.RAZORPAY),
            pk=pk,
        )
        serializer = RazorpayVerifySerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        try:
            razorpay_service.verify_and_process(
                transaction,
                payment_id=data["razorpay_payment_id"],
                order_id=data["razorpay_order_id"],
                signature=data["razorpay_signature"],
            )
        except razorpay_service.RazorpayError as exc:
            return Response({"error": {"message": str(exc)}}, status=status.HTTP_400_BAD_REQUEST)

        return Response(TransactionSerializer(transaction).data)


class RazorpayWebhookView(APIView):
    """Public endpoint Razorpay's servers call directly — not authenticated
    with a merchant API key, only with Razorpay's own webhook signature."""

    authentication_classes = []
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        signature = request.headers.get("X-Razorpay-Signature", "")
        try:
            valid = razorpay_service.verify_webhook_signature(request.body, signature)
        except razorpay_service.RazorpayError as exc:
            return Response({"error": {"message": str(exc)}}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)

        if not valid:
            return Response({"error": {"message": "Invalid webhook signature"}}, status=status.HTTP_400_BAD_REQUEST)

        razorpay_service.handle_webhook_event(request.data)
        return Response({"status": "ok"})
