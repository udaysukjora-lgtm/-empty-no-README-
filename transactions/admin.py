from django.contrib import admin

from .models import Refund, Transaction


@admin.register(Transaction)
class TransactionAdmin(admin.ModelAdmin):
    list_display = ("id", "merchant", "amount", "currency", "status", "provider", "payment_method", "created_at")
    list_filter = ("status", "provider", "payment_method", "currency")
    search_fields = ("id", "customer_email", "gateway_reference", "provider_order_id", "provider_payment_id")


@admin.register(Refund)
class RefundAdmin(admin.ModelAdmin):
    list_display = ("id", "transaction", "amount", "status", "created_at")
    list_filter = ("status",)
