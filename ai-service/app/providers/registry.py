from collections.abc import Callable, Mapping

from app.config import Settings
from app.providers.base import OcrProvider
from app.providers.google_cloud_vision import GoogleCloudVisionOcrProvider

ProviderFactory = Callable[[Settings], OcrProvider]
PROVIDERS: Mapping[str, ProviderFactory] = {
    "google-cloud-vision": GoogleCloudVisionOcrProvider,
}


def create_provider(settings: Settings, registry: Mapping[str, ProviderFactory] | None = None) -> OcrProvider:
    factories = PROVIDERS if registry is None else registry
    if settings.provider not in factories:
        raise ValueError("Unsupported OCR_PROVIDER; register a provider adapter first")
    return factories[settings.provider](settings)
