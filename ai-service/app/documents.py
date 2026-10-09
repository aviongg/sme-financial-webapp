"""Direct byte inspection and validation for PDF and image documents."""
import io
import warnings

from PIL import Image, UnidentifiedImageError
from pypdf import PdfReader

from app.config import Settings
from app.errors import InvalidDocumentError
from app.models import OcrDocument


def inspect_document(content: bytes, settings: Settings) -> OcrDocument:
    if not content:
        raise InvalidDocumentError("Document is empty")
    if len(content) > settings.max_document_bytes:
        raise InvalidDocumentError("Document exceeds the configured byte limit", 413)
    if content.startswith(b"%PDF-"):
        try:
            reader = PdfReader(io.BytesIO(content), strict=True)
            if reader.is_encrypted:
                raise InvalidDocumentError("Encrypted PDFs are not supported")
            count = len(reader.pages)
            if count < 1 or count > settings.max_pdf_pages:
                raise InvalidDocumentError("PDF page count is outside the configured limit")
        except InvalidDocumentError:
            raise
        except Exception:
            raise InvalidDocumentError("PDF could not be read") from None
        return OcrDocument(content, "application/pdf", count)
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(content)) as img:
                mime = {"JPEG": "image/jpeg", "PNG": "image/png", "WEBP": "image/webp"}.get(img.format)
                if mime is None or getattr(img, "n_frames", 1) != 1:
                    raise InvalidDocumentError("Use a single PNG, JPEG, WebP image or a PDF")
                if img.width * img.height > settings.max_image_pixels:
                    raise InvalidDocumentError("Image exceeds the configured pixel limit", 413)
                img.verify()
            # JPEG.verify() only checks the header. Decode the bounded image too
            # so truncated pixel data fails locally, before a paid provider call.
            with Image.open(io.BytesIO(content)) as decoded:
                decoded.load()
        return OcrDocument(content, mime)
    except InvalidDocumentError:
        raise
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError,
            Image.DecompressionBombWarning):
        raise InvalidDocumentError("Image is invalid, unsupported or too large") from None
