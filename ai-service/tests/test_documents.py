import io
import socket
from unittest.mock import Mock

import httpx
import pytest
from PIL import Image
from pypdf import PdfWriter

from app.config import Settings
from app.documents import SafeDocumentLoader, inspect_document
from app.errors import InvalidDocumentError, OcrError


def png():
    buffer = io.BytesIO()
    Image.new("RGB", (2, 2), "white").save(buffer, format="PNG")
    return buffer.getvalue()


def pdf(pages=1, password=None):
    writer = PdfWriter()
    for _ in range(pages):
        writer.add_blank_page(width=100, height=100)
    if password:
        writer.encrypt(password)
    buffer = io.BytesIO()
    writer.write(buffer)
    return buffer.getvalue()


def public_dns(*args, **kwargs):
    return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", 443))]


def loader_for(handler, settings=None, resolve=public_dns, **kwargs):
    return SafeDocumentLoader(
        settings or Settings(allowed_image_hosts=("documents.example",)),
        transport=httpx.MockTransport(handler), resolve=resolve, **kwargs,
    )


class Chunks(httpx.SyncByteStream):
    def __init__(self, *chunks):
        self.chunks = chunks

    def __iter__(self):
        yield from self.chunks


def test_fetch_pins_checked_address_and_preserves_host_tls_and_signed_query():
    seen = []

    def handler(request):
        seen.append(request)
        return httpx.Response(200, stream=Chunks(png()))

    loader = loader_for(handler)
    doc = loader.load("https://documents.example/invoice.png?signature=a%2Fb%2Bc")
    assert doc.mime_type == "image/png"
    assert str(seen[0].url) == "https://93.184.216.34/invoice.png?signature=a%2Fb%2Bc"
    assert seen[0].headers["host"] == "documents.example"
    assert seen[0].extensions["sni_hostname"] == "documents.example"


@pytest.mark.parametrize("host", ["localhost", "evil.example", "documents.example.evil.example"])
def test_untrusted_host_is_rejected_before_network(host):
    transport, resolver = Mock(), Mock()
    loader = loader_for(transport, resolve=resolver)
    with pytest.raises(InvalidDocumentError):
        loader.load(f"https://{host}/a")
    resolver.assert_not_called()
    transport.assert_not_called()


@pytest.mark.parametrize("ip", ["127.0.0.1", "169.254.169.254", "10.0.0.1", "::1", "fc00::1"])
def test_allowlisted_host_with_private_address_rejected(ip):
    handler = Mock()
    resolver = lambda *args, **kwargs: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", (ip, 443))]
    with pytest.raises(InvalidDocumentError):
        loader_for(handler, resolve=resolver).load("https://documents.example/a")
    handler.assert_not_called()


def test_redirects_are_rejected_without_following_them():
    handler = Mock(return_value=httpx.Response(302, headers={"Location": "http://127.0.0.1/secret"}))
    with pytest.raises(OcrError):
        loader_for(handler).load("https://documents.example/a")
    handler.assert_called_once()


def test_configured_host_required():
    with pytest.raises(OcrError, match="Configure trusted"):
        loader_for(Mock(), settings=Settings()).load("https://documents.example/a")


@pytest.mark.parametrize("headers, chunks", [
    ({"Content-Length": "101"}, (b"a",)), ({}, (b"a" * 60, b"b" * 60)),
])
def test_byte_limit_enforced_with_or_without_content_length(headers, chunks):
    settings = Settings(allowed_image_hosts=("documents.example",), max_document_bytes=100)
    loader = loader_for(lambda _: httpx.Response(200, headers=headers, stream=Chunks(*chunks)), settings)
    with pytest.raises(InvalidDocumentError) as failure:
        loader.load("https://documents.example/a")
    assert failure.value.status_code == 413


def test_slow_trickle_hits_deadline_without_waiting_for_large_buffer():
    clock = Mock(side_effect=[0, 1, 11])
    loader = loader_for(lambda _: httpx.Response(200, stream=Chunks(b"a", b"b", b"c")), clock=clock)
    with pytest.raises(OcrError) as failure:
        loader.load("https://documents.example/a")
    assert failure.value.code == "source_timeout"


@pytest.mark.parametrize("timeout_type", [httpx.ConnectTimeout, httpx.ReadTimeout])
def test_network_timeouts_use_safe_timeout_response(timeout_type):
    def handler(request):
        raise timeout_type(f"Timeout fetching {request.url}", request=request)

    with pytest.raises(OcrError) as failure:
        loader_for(handler).load("https://documents.example/a?signature=fixture-secret")
    assert failure.value.status_code == 504
    assert failure.value.code == "source_timeout"
    assert failure.value.message == "Document download timed out"
    assert "fixture-secret" not in str(failure.value)


def test_pdf_pages_counted_before_provider_and_not_silently_truncated():
    assert inspect_document(pdf(2), Settings()).page_count == 2
    with pytest.raises(InvalidDocumentError, match="page count"):
        inspect_document(pdf(6), Settings())
    with pytest.raises(InvalidDocumentError, match="page count"):
        inspect_document(pdf(2), Settings(max_pdf_pages=1))


def test_encrypted_pdf_and_bad_content_rejected():
    with pytest.raises(InvalidDocumentError, match="Encrypted"):
        inspect_document(pdf(password="fixture-only"), Settings())
    for content in (b"", b"<html>not an invoice</html>", b"%PDF-invalid"):
        with pytest.raises(InvalidDocumentError):
            inspect_document(content, Settings())


def test_content_is_inspected_instead_of_trusting_url_extension():
    doc = loader_for(lambda _: httpx.Response(200, stream=Chunks(pdf()))).load("https://documents.example/file")
    assert doc.mime_type == "application/pdf"


def test_image_pixel_limit():
    with pytest.raises(InvalidDocumentError, match="pixel limit"):
        inspect_document(png(), Settings(max_image_pixels=3))


@pytest.mark.parametrize("trim", [2, 10, 20])
def test_truncated_jpeg_data_is_rejected_before_provider(trim):
    buffer = io.BytesIO()
    Image.new("RGB", (32, 32), "white").save(buffer, format="JPEG")
    content = buffer.getvalue()
    assert inspect_document(content, Settings()).mime_type == "image/jpeg"
    with pytest.raises(InvalidDocumentError):
        inspect_document(content[:-trim], Settings())


def test_internal_service_key_header_included_when_configured():
    seen_headers = []

    def handler(request):
        seen_headers.append(request.headers)
        return httpx.Response(200, stream=Chunks(png()))

    settings = Settings(
        allowed_image_hosts=("documents.example",),
        internal_service_key="secret-key-12345",
    )
    loader = loader_for(handler, settings=settings)
    doc = loader.load("https://documents.example/file.png")
    assert doc.mime_type == "image/png"
    assert len(seen_headers) == 1
    assert seen_headers[0].get("X-Internal-Service-Key") == "secret-key-12345"
