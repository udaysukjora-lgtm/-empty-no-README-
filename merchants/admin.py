from django.contrib import admin

from .models import APIKey, Merchant


@admin.register(Merchant)
class MerchantAdmin(admin.ModelAdmin):
    list_display = ("business_name", "email", "kyc_status", "is_active", "created_at")
    list_filter = ("kyc_status", "is_active")
    search_fields = ("business_name", "email")


@admin.register(APIKey)
class APIKeyAdmin(admin.ModelAdmin):
    list_display = ("prefix", "merchant", "mode", "is_active", "created_at", "last_used_at")
    list_filter = ("mode", "is_active")
    readonly_fields = ("prefix", "hashed_key")
