import io
import pytest
from PIL import Image
from pypdf import PdfWriter

from app.config import Settings
from app.documents import inspect_document
from app.errors import InvalidDocumentError


def png(size=(2, 2)):
    buffer = io.BytesIO()
    Image.new("RGB", size, "white").save(buffer, format="PNG")
    return buffer.getvalue()


def jpeg(size=(2, 2)):
    buffer = io.BytesIO()
    Image.new("RGB", size, "white").save(buffer, format="JPEG")
    return buffer.getvalue()


def webp(size=(2, 2)):
    buffer = io.BytesIO()
    Image.new("RGB", size, "white").save(buffer, format="WEBP")
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


def test_supported_image_formats_and_pdf():
    settings = Settings()
    assert inspect_document(png(), settings).mime_type == "image/png"
    assert inspect_document(jpeg(), settings).mime_type == "image/jpeg"
    assert inspect_document(webp(), settings).mime_type == "image/webp"
    doc_pdf = inspect_document(pdf(1), settings)
    assert doc_pdf.mime_type == "application/pdf"
    assert doc_pdf.page_count == 1


@pytest.mark.parametrize("pages", [1, 2, 3, 4, 5])
def test_valid_pdf_page_counts_pass(pages):
    settings = Settings()
    doc = inspect_document(pdf(pages), settings)
    assert doc.page_count == pages
    assert doc.mime_type == "application/pdf"


@pytest.mark.parametrize("pages", [6, 7, 10])
def test_pdf_pages_exceeding_max_rejected(pages):
    settings = Settings(max_pdf_pages=5)
    with pytest.raises(InvalidDocumentError, match="page count"):
        inspect_document(pdf(pages), settings)


def test_pdf_max_page_custom_limit():
    with pytest.raises(InvalidDocumentError, match="page count"):
        inspect_document(pdf(2), Settings(max_pdf_pages=1))


def test_encrypted_pdf_rejected():
    with pytest.raises(InvalidDocumentError, match="Encrypted"):
        inspect_document(pdf(password="fixture-only"), Settings())


@pytest.mark.parametrize("bad_pdf", [
    b"",
    b"%PDF-",
    b"%PDF-1.4",
    b"%PDF-1.4\n%%EOF",
    b"%PDF-invalid-bytes-stream-broken",
    b"%PDF-1.7\r\n\x00\x00\x00",
])
def test_malformed_pdf_rejected(bad_pdf):
    with pytest.raises(InvalidDocumentError):
        inspect_document(bad_pdf, Settings())


@pytest.mark.parametrize("disguised_content", [
    b"MZ\x90\x00\x03\x00\x00\x00",                # Windows PE executable
    b"\x7fELF\x02\x01\x01\x00",                    # Linux ELF executable
    b"PK\x03\x04\x14\x00\x00\x00",                # Zip archive
    b"\x1f\x8b\x08\x00\x00\x00",                  # Gzip archive
    b"<!DOCTYPE html><html><body>test</body></html>", # HTML
    b"<?xml version=\"1.0\"?><svg></svg>",        # SVG / XML
    b"{\"type\": \"invoice\", \"amount\": 100}",   # JSON
    b"#!/bin/bash\necho hello",                   # Shell script
    b"RIFF\x00\x00\x00\x00AVI LIST",              # AVI video
])
def test_unsupported_or_executable_signatures_rejected(disguised_content):
    with pytest.raises(InvalidDocumentError):
        inspect_document(disguised_content, Settings())


def test_oversized_bytes_rejected():
    settings = Settings(max_document_bytes=100)
    with pytest.raises(InvalidDocumentError) as exc_info:
        inspect_document(b"a" * 101, settings)
    assert exc_info.value.status_code == 413


def test_empty_content_rejected():
    with pytest.raises(InvalidDocumentError, match="empty"):
        inspect_document(b"", Settings())


def test_image_pixel_limit():
    with pytest.raises(InvalidDocumentError, match="pixel limit"):
        inspect_document(png((10, 10)), Settings(max_image_pixels=50))


@pytest.mark.parametrize("trim", [2, 10, 20])
def test_truncated_jpeg_data_is_rejected_before_provider(trim):
    buffer = io.BytesIO()
    Image.new("RGB", (32, 32), "white").save(buffer, format="JPEG")
    content = buffer.getvalue()
    assert inspect_document(content, Settings()).mime_type == "image/jpeg"
    with pytest.raises(InvalidDocumentError):
        inspect_document(content[:-trim], Settings())


def test_animated_or_multi_frame_image_rejected():
    buffer = io.BytesIO()
    img1 = Image.new("RGB", (10, 10), "red")
    img2 = Image.new("RGB", (10, 10), "blue")
    img1.save(buffer, format="GIF", save_all=True, append_images=[img2])
    with pytest.raises(InvalidDocumentError):
        inspect_document(buffer.getvalue(), Settings())
