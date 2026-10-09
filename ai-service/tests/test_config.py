from unittest.mock import Mock

import pytest

from app.config import Settings
from app.providers.google_cloud_vision import GoogleCloudVisionOcrProvider
from app.providers.registry import create_provider


def test_default_provider_creation_does_not_use_adc():
    provider = create_provider(Settings.from_env({}))
    assert isinstance(provider, GoogleCloudVisionOcrProvider)
    assert provider._client is None


def test_switching_config_selects_registered_replacement_without_service_changes():
    first, second = Mock(), Mock()
    registry = {"fixture-a": lambda config: first, "fixture-b": lambda config: second}
    assert create_provider(Settings.from_env({"OCR_PROVIDER": "fixture-a"}), registry) is first
    assert create_provider(Settings.from_env({"OCR_PROVIDER": "fixture-b"}), registry) is second


def test_unknown_provider_fails_explicitly_instead_of_falling_back():
    with pytest.raises(ValueError, match="Unsupported OCR_PROVIDER"):
        create_provider(Settings(provider="google-document-ai"))


def test_environment_controls_provider_settings_and_operational_limits():
    settings = Settings.from_env({
        "OCR_ALLOWED_IMAGE_HOSTS": "documents.example, Storage.Example",
        "GOOGLE_CLOUD_VISION_TIMEOUT_SECONDS": "12.5",
        "GOOGLE_CLOUD_VISION_ENDPOINT": "eu-vision.googleapis.com",
        "GOOGLE_CLOUD_VISION_LANGUAGE_HINTS": "en,ur",
        "OCR_RETRY_DELAYS_SECONDS": "0.1,0.2", "OCR_MAX_PDF_PAGES": "2",
        "OCR_MAX_DOCUMENT_BYTES": "12345", "OCR_MAX_CONCURRENT_REQUESTS": "2",
    })
    assert settings.allowed_image_hosts == ("documents.example", "storage.example")
    assert settings.provider_timeout_seconds == 12.5
    assert settings.vision_endpoint == "eu-vision.googleapis.com"
    assert settings.vision_language_hints == ("en", "ur")
    assert settings.retry_delays_seconds == (.1, .2)
    assert settings.max_pdf_pages == 2 and settings.max_document_bytes == 12345


@pytest.mark.parametrize("environment", [
    {"OCR_RETRY_DELAYS_SECONDS": "1"}, {"OCR_RETRY_DELAYS_SECONDS": "-2,4"},
    {"OCR_RETRY_DELAYS_SECONDS": "nan,4"},
    {"OCR_MAX_CONCURRENT_REQUESTS": "0"}, {"GOOGLE_CLOUD_VISION_TIMEOUT_SECONDS": "inf"},
    {"OCR_MAX_DOCUMENT_BYTES": "-1"},
])
def test_invalid_configuration_rejected(environment):
    with pytest.raises(ValueError):
        Settings.from_env(environment)


def test_pdf_capability_belongs_to_adapter_not_shared_config():
    settings = Settings(max_pdf_pages=10)
    with pytest.raises(ValueError, match="five PDF pages"):
        create_provider(settings)
    replacement = Mock()
    assert create_provider(settings, {"google-cloud-vision": lambda _: replacement}) is replacement


def test_secret_loaded_from_file(tmp_path):
    secret_path = tmp_path / "secret.txt"
    secret_path.write_text("my-super-secret-key-123\n", encoding="utf-8")
    settings = Settings.from_env({"OCR_SERVICE_SECRET_FILE": str(secret_path)})
    assert settings.ocr_service_key == "my-super-secret-key-123"
    assert settings.internal_service_key == "my-super-secret-key-123"

