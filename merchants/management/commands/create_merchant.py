from django.core.management.base import BaseCommand, CommandError

from merchants.models import APIKey, Merchant


class Command(BaseCommand):
    help = "Creates a merchant account and prints a test-mode API key for it."

    def add_arguments(self, parser):
        parser.add_argument("business_name")
        parser.add_argument("email")
        parser.add_argument("--mode", choices=["test", "live"], default="test")

    def handle(self, *args, **options):
        email = options["email"]
        if Merchant.objects.filter(email=email).exists():
            raise CommandError(f"Merchant with email {email} already exists")

        merchant = Merchant.objects.create(
            business_name=options["business_name"],
            email=email,
        )
        api_key, raw_key = APIKey.create_for_merchant(merchant, mode=options["mode"])

        self.stdout.write(self.style.SUCCESS(f"Created merchant '{merchant.business_name}' ({merchant.id})"))
        self.stdout.write(self.style.WARNING(f"API key (shown once): {raw_key}"))
