"""The only module that knows Google's SDK. ADC/client creation is lazy."""
import math
import threading
from typing import Any

from app.config import Settings
from app.errors import PermanentProviderError, TransientProviderError
from app.models import NormalizedOcrResult, OcrDocument, OcrLine


class GoogleCloudVisionOcrProvider:
    def __init__(self, settings: Settings, *, client: Any = None):
        if settings.max_pdf_pages > 5:
            raise ValueError("Vision's synchronous file API supports at most five PDF pages")
        self.settings = settings
        self._client = client
        self._client_lock = threading.Lock()

    def _get_client(self):
        from google.cloud import vision

        with self._client_lock:
            if self._client is None:
                self._client = vision.ImageAnnotatorClient(
                    client_options={"api_endpoint": self.settings.vision_endpoint}
                )
        return self._client

    def extract(self, document: OcrDocument) -> NormalizedOcrResult:
        from google.api_core import exceptions
        from google.auth.exceptions import GoogleAuthError
        from google.cloud import vision

        try:
            client = self._get_client()
            context = vision.ImageContext(language_hints=list(self.settings.vision_language_hints))
            if document.mime_type == "application/pdf":
                result = client.batch_annotate_files(
                    requests=[vision.AnnotateFileRequest(
                        input_config=vision.InputConfig(content=document.content, mime_type="application/pdf"),
                        features=[vision.Feature(type_=vision.Feature.Type.DOCUMENT_TEXT_DETECTION)],
                        image_context=context,
                        pages=list(range(1, document.page_count + 1)),
                    )],
                    retry=None, timeout=self.settings.provider_timeout_seconds,
                )
                if len(result.responses) != 1:
                    raise PermanentProviderError("invalid_provider_response")
                file_result = result.responses[0]
                # Failed pages may omit their page-number metadata. Classify
                # errors before completeness so transient page failures retry.
                self._check_errors([file_result.error.code, *(r.error.code for r in file_result.responses)])
                expected_pages = set(range(1, document.page_count + 1))
                if (len(file_result.responses) != document.page_count
                        or file_result.total_pages != document.page_count
                        or {r.context.page_number for r in file_result.responses} != expected_pages):
                    raise PermanentProviderError("incomplete_provider_response")
                return self._normalize(sorted(file_result.responses, key=lambda r: r.context.page_number))
            result = client.document_text_detection(
                image=vision.Image(content=document.content), image_context=context,
                retry=None, timeout=self.settings.provider_timeout_seconds,
            )
            return self._normalize([result])
        except (exceptions.ServiceUnavailable, exceptions.DeadlineExceeded, exceptions.InternalServerError):
            raise TransientProviderError() from None
        except (GoogleAuthError, exceptions.GoogleAPICallError):
            # Includes invalid arguments, permission/auth errors and exhausted quota.
            # Quota exhaustion is not presumed to be transient rate throttling.
            raise PermanentProviderError() from None

    @staticmethod
    def _check_error(code: int) -> None:
        GoogleCloudVisionOcrProvider._check_errors([code])

    @staticmethod
    def _check_errors(codes: list[int]) -> None:
        transient_codes = {4, 13, 14}  # DEADLINE_EXCEEDED, INTERNAL, UNAVAILABLE
        # A permanently invalid page cannot recover by retrying the whole file.
        if any(code and code not in transient_codes for code in codes):
            raise PermanentProviderError()
        if any(code in transient_codes for code in codes):
            raise TransientProviderError()

    def _normalize(self, responses) -> NormalizedOcrResult:
        texts: list[str] = []
        confidences: list[float] = []
        lines: list[OcrLine] = []
        count = 0
        for response in responses:
            self._check_error(response.error.code)
            annotation = response.full_text_annotation
            text = annotation.text
            if not text and response.text_annotations:
                text = response.text_annotations[0].description
            count += len(text)
            if count > self.settings.max_text_chars:
                raise PermanentProviderError("ocr_text_limit_exceeded")
            texts.append(text)
            words: list[tuple[str, float]] = []
            for page in annotation.pages:
                for block in page.blocks:
                    for paragraph in block.paragraphs:
                        for word in paragraph.words:
                            value = float(word.confidence)
                            if math.isfinite(value) and 0 <= value <= 1:
                                confidences.append(value)
                                words.append(("".join(s.text for s in word.symbols), value))
            lines.extend(self._line_evidence(text, words))
        confidence = min(confidences) if confidences else None
        return NormalizedOcrResult(text="\n".join(texts), confidence=confidence, lines=tuple(lines))

    @staticmethod
    def _line_evidence(text: str, words: list[tuple[str, float]]) -> list[OcrLine]:
        # Match SDK words to actual text offsets in order. Preserve low confidence
        # even when punctuation differs; otherwise an unmatched colon could turn
        # a known unreadable amount into unknown-confidence, accepted evidence.
        evidence: list[float | None] = [None] * len(text)
        cursor = 0
        for token, confidence in words:
            if not token:
                continue
            position = text.find(token, cursor)
            if position < 0:
                continue
            end = position + len(token)
            evidence[position:end] = [confidence] * len(token)
            cursor = end
        lines: list[OcrLine] = []
        offset = 0
        fallback = min((score for _, score in words), default=None)
        for line in text.splitlines(keepends=True):
            scores = [evidence[offset + i] for i, c in enumerate(line) if not c.isspace()]
            known_scores = [score for score in scores if score is not None]
            # Matching just a confident label is insufficient when its numeric
            # value did not align. Fall back conservatively for that line only.
            unmatched_value = any(c.isalnum() and evidence[offset + i] is None for i, c in enumerate(line))
            if unmatched_value and fallback is not None:
                known_scores.append(fallback)
            lines.append(OcrLine(line.rstrip("\r\n"), min(known_scores) if known_scores else fallback))
            offset += len(line)
        return lines
