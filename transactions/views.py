from django.shortcuts import get_object_or_404
from rest_framework import status, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response

from . import services
from .models import Transaction
from .serializers import (
    PaymentIntentCreateSerializer,
    RefundCreateSerializer,
    RefundSerializer,
    TransactionSerializer,
)


class PaymentIntentViewSet(viewsets.ViewSet):
    """Payment intents: create, retrieve, list, capture, and refund."""

    def get_queryset(self):
        return Transaction.objects.filter(merchant=self.request.merchant)

    def list(self, request):
        queryset = self.get_queryset()
        return Response(TransactionSerializer(queryset, many=True).data)

    def retrieve(self, request, pk=None):
        transaction = self._get_transaction(pk)
        return Response(TransactionSerializer(transaction).data)

    def create(self, request):
        idempotency_key = request.headers.get("Idempotency-Key")
        if idempotency_key:
            existing = Transaction.objects.filter(
                merchant=request.merchant, idempotency_key=idempotency_key
            ).first()
            if existing:
                return Response(TransactionSerializer(existing).data, status=status.HTTP_200_OK)

        serializer = PaymentIntentCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        transaction = Transaction.objects.create(
            merchant=request.merchant,
            amount=data["amount"],
            currency=data["currency"],
            payment_method=data["payment_method"],
            payment_method_details=serializer.to_masked_details(),
            customer_email=data["customer_email"],
            customer_reference=data["customer_reference"],
            description=data["description"],
            metadata=data["metadata"],
            idempotency_key=idempotency_key,
        )

        services.authorize(transaction, data)
        if data["capture"] and transaction.status == Transaction.Status.AUTHORIZED:
            services.capture(transaction)

        return Response(TransactionSerializer(transaction).data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["post"])
    def capture(self, request, pk=None):
        transaction = self._get_transaction(pk)
        try:
            services.capture(transaction)
        except ValueError as exc:
            return Response({"error": {"message": str(exc)}}, status=status.HTTP_409_CONFLICT)
        return Response(TransactionSerializer(transaction).data)

    @action(detail=True, methods=["post"])
    def refund(self, request, pk=None):
        transaction = self._get_transaction(pk)
        serializer = RefundCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        amount = serializer.validated_data.get("amount") or transaction.amount_refundable

        try:
            refund_obj = services.refund(transaction, amount, serializer.validated_data.get("reason", ""))
        except ValueError as exc:
            return Response({"error": {"message": str(exc)}}, status=status.HTTP_409_CONFLICT)
        return Response(RefundSerializer(refund_obj).data, status=status.HTTP_201_CREATED)

    def _get_transaction(self, pk):
        return get_object_or_404(self.get_queryset(), pk=pk)
