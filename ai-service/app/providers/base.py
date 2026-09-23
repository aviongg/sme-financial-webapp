from typing import Protocol

from app.models import NormalizedOcrResult, OcrDocument


class OcrProvider(Protocol):
    def extract(self, document: OcrDocument) -> NormalizedOcrResult:
        """Make one attempt; convert all SDK types/errors into domain objects."""
        ...
