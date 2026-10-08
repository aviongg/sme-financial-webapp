"""Environment-only settings; limits are operational safeguards, not pricing quotas."""
import os
from dataclasses import dataclass
from typing import Mapping


@dataclass(frozen=True)
class Settings:
    provider: str = "google-cloud-vision"
    allowed_image_hosts: tuple[str, ...] = ()
    download_timeout_seconds: float = 10
    provider_timeout_seconds: float = 20
    retry_delays_seconds: tuple[float, float] = (2, 4)
    max_document_bytes: int = 10 * 1024 * 1024
    max_pdf_pages: int = 5
    max_image_pixels: int = 25_000_000
    max_url_length: int = 8192
    max_text_chars: int = 100_000
    max_concurrent_requests: int = 4
    vision_endpoint: str = "vision.googleapis.com"
    vision_language_hints: tuple[str, ...] = ()
    ocr_service_key: str = ""
    internal_service_key: str = ""

    def __post_init__(self):
        import math
        for name in ("provider_timeout_seconds", "max_document_bytes",
                     "max_pdf_pages", "max_image_pixels", "max_text_chars",
                     "max_concurrent_requests"):
            value = getattr(self, name)
            if not math.isfinite(value) or value <= 0:
                raise ValueError(f"{name} must be positive and finite")
        if len(self.retry_delays_seconds) != 2 or any(
            not math.isfinite(v) or v < 0 for v in self.retry_delays_seconds
        ):
            raise ValueError("Configure exactly two finite, non-negative retry delays")
        if not self.provider or not self.vision_endpoint:
            raise ValueError("Provider and Vision endpoint must be non-empty")
        if not self.ocr_service_key and self.internal_service_key:
            object.__setattr__(self, "ocr_service_key", self.internal_service_key)

    @classmethod
    def from_env(cls, environ: Mapping[str, str] | None = None) -> "Settings":
        env = os.environ if environ is None else environ

        def csv(name: str, default: str = "") -> tuple[str, ...]:
            return tuple(v.strip() for v in env.get(name, default).split(",") if v.strip())

        secret = env.get("OCR_SERVICE_SECRET") or env.get("INTERNAL_SERVICE_SECRET", "")
        if not secret:
            secret_file = env.get("OCR_SERVICE_SECRET_FILE", "/run/secrets/ocr_service_key")
            if os.path.isfile(secret_file):
                try:
                    with open(secret_file, "r", encoding="utf-8") as f:
                        secret = f.read().strip()
                except Exception:
                    pass
        return cls(
            provider=env.get("OCR_PROVIDER", "google-cloud-vision"),
            allowed_image_hosts=tuple(h.lower().rstrip(".") for h in csv("OCR_ALLOWED_IMAGE_HOSTS")),
            download_timeout_seconds=float(env.get("OCR_DOWNLOAD_TIMEOUT_SECONDS", "10")),
            provider_timeout_seconds=float(env.get("GOOGLE_CLOUD_VISION_TIMEOUT_SECONDS", "20")),
            retry_delays_seconds=tuple(float(v) for v in csv("OCR_RETRY_DELAYS_SECONDS", "2,4")),
            max_document_bytes=int(env.get("OCR_MAX_DOCUMENT_BYTES", str(10 * 1024 * 1024))),
            max_pdf_pages=int(env.get("OCR_MAX_PDF_PAGES", "5")),
            max_image_pixels=int(env.get("OCR_MAX_IMAGE_PIXELS", "25000000")),
            max_url_length=int(env.get("OCR_MAX_URL_LENGTH", "8192")),
            max_text_chars=int(env.get("OCR_MAX_TEXT_CHARS", "100000")),
            max_concurrent_requests=int(env.get("OCR_MAX_CONCURRENT_REQUESTS", "4")),
            vision_endpoint=env.get("GOOGLE_CLOUD_VISION_ENDPOINT", "vision.googleapis.com"),
            vision_language_hints=csv("GOOGLE_CLOUD_VISION_LANGUAGE_HINTS"),
            ocr_service_key=secret,
            internal_service_key=secret,
        )
