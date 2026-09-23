"""Fetch a single trusted document URL. This does not implement file uploads."""
import io
import ipaddress
import socket
import time
import warnings
from collections.abc import Callable
from typing import Protocol
from urllib.parse import urlsplit

import httpx
from PIL import Image, UnidentifiedImageError
from pypdf import PdfReader

from app.config import Settings
from app.errors import InvalidDocumentError, OcrError
from app.models import OcrDocument, validate_url


class DocumentLoader(Protocol):
    def load(self, url: str) -> OcrDocument: ...


class SafeDocumentLoader:
    def __init__(self, settings: Settings, *, transport: httpx.BaseTransport | None = None,
                 resolve: Callable = socket.getaddrinfo, clock: Callable = time.monotonic):
        self.settings = settings
        self.transport = transport
        self.resolve = resolve
        self.clock = clock

    def load(self, url: str) -> OcrDocument:
        validate_url(url)
        settings = self.settings
        if len(url) > settings.max_url_length:
            raise InvalidDocumentError("Document URL exceeds the configured length limit")
        parsed = urlsplit(url)
        host = parsed.hostname.encode("idna").decode("ascii").lower().rstrip(".")
        # Exact operator-configured hosts only. No wildcards, redirects or proxies.
        if not settings.allowed_image_hosts:
            raise OcrError("source_not_configured", "Configure trusted document hosts before extraction", 503)
        if host not in settings.allowed_image_hosts:
            raise InvalidDocumentError("Document host is not allowed")
        port = parsed.port or (443 if parsed.scheme == "https" else 80)
        try:
            addresses = self.resolve(host, port, type=socket.SOCK_STREAM)
            if not addresses or any(not ipaddress.ip_address(a[4][0]).is_global for a in addresses):
                raise InvalidDocumentError("Document host must resolve to public addresses")
        except (OSError, ValueError):
            raise InvalidDocumentError("Document host could not be resolved safely") from None
        start = self.clock()
        # Pin the checked address. Host and TLS SNI retain the original hostname;
        # the HTTP client must not perform a second DNS lookup of that hostname.
        target = httpx.URL(url).copy_with(host=addresses[0][4][0])
        request_headers = {"Accept-Encoding": "identity", "Host": parsed.netloc}
        if settings.internal_service_key:
            request_headers["X-Internal-Service-Key"] = settings.internal_service_key
        try:
            with httpx.Client(transport=self.transport, timeout=settings.download_timeout_seconds,
                              follow_redirects=False, trust_env=False) as client:
                with client.stream("GET", target,
                                   headers=request_headers,
                                   extensions={"sni_hostname": host}) as response:
                    if response.status_code != 200:
                        raise OcrError("source_unavailable", "Document URL did not return a file", 422)
                    if response.headers.get("content-encoding", "identity").lower() != "identity":
                        raise InvalidDocumentError("Compressed HTTP responses are not supported")
                    length = response.headers.get("content-length")
                    if length is not None:
                        try:
                            declared_size = int(length)
                        except ValueError:
                            raise InvalidDocumentError("Invalid document size header") from None
                        if declared_size < 0 or declared_size > settings.max_document_bytes:
                            raise InvalidDocumentError("Document exceeds the configured byte limit", 413)
                    body = bytearray()
                    for chunk in response.iter_raw():
                        if self.clock() - start > settings.download_timeout_seconds:
                            raise OcrError("source_timeout", "Document download timed out", 504)
                        body.extend(chunk)
                        if len(body) > settings.max_document_bytes:
                            raise InvalidDocumentError("Document exceeds the configured byte limit", 413)
        except httpx.TimeoutException:
            raise OcrError("source_timeout", "Document download timed out", 504) from None
        except httpx.HTTPError:
            raise OcrError("source_unavailable", "Document could not be downloaded", 502) from None
        return inspect_document(bytes(body), settings)


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
