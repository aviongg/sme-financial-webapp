import time
from collections.abc import Callable
from threading import BoundedSemaphore

from app.config import Settings
from app.documents import inspect_document
from app.errors import OcrError, PermanentProviderError, TransientProviderError
from app.models import DocumentType, ExtractResponse, OcrDocument
from app.parser import parse_document
from app.providers.base import OcrProvider


class ExtractionService:
    def __init__(self, provider: OcrProvider, settings: Settings,
                 *, sleep: Callable[[float], None] = time.sleep,
                 inspector: Callable[[bytes, Settings], OcrDocument] = inspect_document):
        self.provider = provider
        self.settings = settings
        self.sleep = sleep
        self.inspector = inspector
        self.capacity = BoundedSemaphore(settings.max_concurrent_requests)

    def extract(self, content: bytes, document_type_hint: DocumentType = "unknown",
                document_id: str | None = None) -> ExtractResponse:
        if not self.capacity.acquire(blocking=False):
            raise OcrError("service_busy", "OCR service is busy; try again later", 503)
        try:
            document = self.inspector(content, self.settings)
            for attempt in range(3):
                try:
                    result = self.provider.extract(document)
                    break
                except TransientProviderError:
                    if attempt == 2:
                        raise OcrError("provider_retries_exhausted", "OCR processing failed after retries", 503) from None
                    self.sleep(self.settings.retry_delays_seconds[attempt])
            if len(result.text) > self.settings.max_text_chars:
                raise PermanentProviderError("ocr_text_limit_exceeded")
            return parse_document(result, document_type_hint)
        finally:
            self.capacity.release()
