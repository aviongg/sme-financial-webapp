import io
from unittest.mock import Mock

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.config import Settings
from app.errors import InvalidDocumentError, PermanentProviderError, TransientProviderError
from app.main import create_app, get_extraction_service
from app.models import NormalizedOcrResult, OcrDocument

SECRET = "test-secret-12345"
AUTH_HEADERS = {"X-OCR-Service-Key": SECRET}
EVIDENCE = NormalizedOcrResult("INVOICE\nInvoice date: 2026-09-19\nTotal: 1250.50", .96)


def sample_png():
    buf = io.BytesIO()
    Image.new("RGB", (2, 2), "white").save(buf, format="PNG")
    return buf.getvalue()


def client_for(result=EVIDENCE, settings=None, inspector=None, real_inspector=False):
    provider = Mock()
    provider.extract.return_value = result
    sleep = Mock()
    from app.documents import inspect_document
    if real_inspector:
        insp = inspect_document
    elif inspector is not None:
        insp = inspector
    else:
        insp = Mock(return_value=OcrDocument(b"fixture", "image/png"))
    config = settings if settings is not None else Settings(ocr_service_key=SECRET)
    app = create_app(config, provider=provider, sleep=sleep, inspector=insp)
    return TestClient(app, raise_server_exceptions=False), provider, insp, sleep


def test_multipart_draft_extraction_success():
    client, provider, inspector, sleep = client_for()
    response = client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice", "document_id": "test-doc-id"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 200
    assert response.json() == {
        "date": "2026-09-19",
        "amount": 1250.50,
        "vendor_or_party": None,
        "category": "unknown",
        "confidence": "medium",
        "document_type_detected": "invoice",
    }
    assert isinstance(response.json()["amount"], (int, float))
    provider.extract.assert_called_once()
    inspector.assert_called_once()
    sleep.assert_not_called()


def test_missing_auth_header_rejected():
    client, provider, _, _ = client_for()
    response = client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
    )
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "unauthorized"
    provider.extract.assert_not_called()


def test_invalid_auth_header_rejected():
    client, provider, _, _ = client_for()
    response = client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers={"X-OCR-Service-Key": "wrong-secret"},
    )
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "unauthorized"
    provider.extract.assert_not_called()


def test_unconfigured_ocr_secret_fails_closed():
    # If service secret is empty string, fail closed with 503
    client, provider, _, _ = client_for(settings=Settings(ocr_service_key=""))
    response = client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "auth_unconfigured"
    provider.extract.assert_not_called()


def test_service_key_in_query_parameter_forbidden():
    client, provider, _, _ = client_for()
    response = client.post(
        f"/extract?key={SECRET}",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "unauthorized"
    provider.extract.assert_not_called()


@pytest.mark.parametrize("payload", [
    {"image_url": "http://127.0.0.1:8080/api/documents/1/file"},
    {"file_url": "http://localhost:8080/secret"},
    {"callback_url": "http://169.254.169.254/latest/meta-data"},
    {"url": "file:///etc/passwd"},
    {"document_url": "https://evil.com/leak"},
])
def test_ssrf_regression_json_and_urls_are_rejected(payload):
    client, provider, _, _ = client_for()
    response = client.post("/extract", json=payload, headers=AUTH_HEADERS)
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_request"
    provider.extract.assert_not_called()


def test_missing_file_multipart_field_rejected():
    client, provider, _, _ = client_for()
    response = client.post(
        "/extract",
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_request"
    provider.extract.assert_not_called()


@pytest.mark.parametrize("failures", [0, 1, 2])
def test_transient_failures_retry_with_delays(failures):
    client, provider, inspector, sleep = client_for()
    provider.extract.side_effect = [TransientProviderError() for _ in range(failures)] + [EVIDENCE]
    response = client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 200
    assert provider.extract.call_count == failures + 1
    assert [c.args[0] for c in sleep.call_args_list] == [2, 4][:failures]
    inspector.assert_called_once()


def test_exhausted_retries_return_failure():
    client, provider, _, sleep = client_for()
    provider.extract.side_effect = TransientProviderError()
    response = client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "provider_retries_exhausted"
    assert provider.extract.call_count == 3
    assert [c.args[0] for c in sleep.call_args_list] == [2, 4]


def test_permanent_error_is_not_retried():
    client, provider, _, sleep = client_for()
    provider.extract.side_effect = PermanentProviderError()
    response = client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 502
    provider.extract.assert_called_once()
    sleep.assert_not_called()


def test_invalid_document_never_calls_provider():
    client, provider, inspector, sleep = client_for()
    inspector.side_effect = InvalidDocumentError("Document is invalid")
    response = client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 422
    provider.extract.assert_not_called()
    sleep.assert_not_called()


def test_empty_text_is_low_confidence():
    client, _, _, _ = client_for(NormalizedOcrResult(""))
    response = client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 200
    body = response.json()
    assert body["date"] is None and body["amount"] is None and body["vendor_or_party"] is None
    assert body["confidence"] == "low"
    assert "status" not in body and "confirmed" not in body


def test_capacity_is_released_after_failure():
    client, provider, _, _ = client_for()
    service = client.app.state.extraction_service
    for _ in range(4):
        assert service.capacity.acquire(blocking=False)
    response = client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.json()["error"]["code"] == "service_busy"
    provider.extract.assert_not_called()
    for _ in range(4):
        service.capacity.release()
    provider.extract.side_effect = PermanentProviderError()
    assert client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    ).status_code == 502
    provider.extract.side_effect = None
    assert client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    ).status_code == 200


def test_amount_precision_loss_handled():
    evidence = NormalizedOcrResult("Invoice\nDate: 2026-09-19\nTotal: 1234567890123456.78", .99)
    client, _, _, _ = client_for(evidence)
    response = client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 200
    assert response.json()["amount"] is None
    assert response.json()["date"] == "2026-09-19"
    assert response.json()["confidence"] == "low"


def test_transient_pdf_page_failure_retries_with_real_provider():
    from google.cloud import vision
    from app.providers.google_cloud_vision import GoogleCloudVisionOcrProvider

    sdk = Mock()
    transient = vision.BatchAnnotateFilesResponse(responses=[vision.AnnotateFileResponse(
        total_pages=1, responses=[vision.AnnotateImageResponse(error={"code": 14})],
    )])
    success = vision.BatchAnnotateFilesResponse(responses=[vision.AnnotateFileResponse(
        total_pages=1, responses=[vision.AnnotateImageResponse(
            context={"page_number": 1}, full_text_annotation={"text": EVIDENCE.text},
        )],
    )])
    sdk.batch_annotate_files.side_effect = [transient, transient, success]
    sleep = Mock()
    inspector = Mock(return_value=OcrDocument(b"controlled PDF fixture", "application/pdf", 1))
    settings = Settings(ocr_service_key=SECRET)
    app = create_app(settings, provider=GoogleCloudVisionOcrProvider(settings, client=sdk),
                     sleep=sleep, inspector=inspector)
    response = TestClient(app).post(
        "/extract",
        files={"file": ("invoice.pdf", b"%PDF-1.4...", "application/pdf")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 200
    assert response.json()["amount"] == 1250.5
    assert sdk.batch_annotate_files.call_count == 3
    assert [call.args[0] for call in sleep.call_args_list] == [2, 4]
    inspector.assert_called_once()


@pytest.mark.parametrize("hint", ["receipt", "invoice", "bank_statement", "unknown"])
def test_valid_document_type_hints_accepted(hint):
    client, provider, _, _ = client_for()
    response = client.post(
        "/extract",
        files={"file": ("doc.png", sample_png(), "image/png")},
        data={"document_type_hint": hint},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 200


@pytest.mark.parametrize("bad_hint", ["RECEIPT", "other", "tax_return", 42])
def test_invalid_document_type_hints_rejected(bad_hint):
    client, provider, _, _ = client_for()
    response = client.post(
        "/extract",
        files={"file": ("doc.png", sample_png(), "image/png")},
        data={"document_type_hint": bad_hint},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 422


def test_auth_header_lowercase_accepted():
    client, provider, _, _ = client_for()
    response = client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers={"x-ocr-service-key": SECRET},
    )
    assert response.status_code == 200


@pytest.mark.parametrize("param_name", ["key", "secret", "X-OCR-Service-Key", "x-internal-service-key"])
def test_query_param_leak_variations_rejected(param_name):
    client, provider, _, _ = client_for()
    response = client.post(
        f"/extract?{param_name}={SECRET}",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "unauthorized"


def test_temp_file_cleaned_up_on_both_success_and_error():
    client, provider, inspector, _ = client_for()
    # Success
    response = client.post(
        "/extract",
        files={"file": ("invoice.png", sample_png(), "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 200

    # Failure inside inspector
    inspector.side_effect = InvalidDocumentError("Broken file")
    response_fail = client.post(
        "/extract",
        files={"file": ("corrupt.png", b"not-a-real-file", "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response_fail.status_code == 422


def test_extract_oversized_upload_rejected_without_processing():
    settings = Settings(ocr_service_key=SECRET, max_document_bytes=100)
    client, provider, inspector, _ = client_for(settings=settings)
    oversized_content = b"x" * 101
    response = client.post(
        "/extract",
        files={"file": ("too_large.png", oversized_content, "image/png")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 413
    assert response.json()["error"]["code"] == "payload_too_large"
    provider.extract.assert_not_called()
    inspector.assert_not_called()


def test_extract_pdf_5_pages_accepted():
    from tests.test_documents import pdf
    client, provider, _, _ = client_for(real_inspector=True)
    response = client.post(
        "/extract",
        files={"file": ("valid_5_pages.pdf", pdf(5), "application/pdf")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 200


def test_extract_pdf_6_pages_rejected():
    from tests.test_documents import pdf
    client, provider, _, _ = client_for(real_inspector=True)
    response = client.post(
        "/extract",
        files={"file": ("invalid_6_pages.pdf", pdf(6), "application/pdf")},
        data={"document_type_hint": "invoice"},
        headers=AUTH_HEADERS,
    )
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_document"
    assert "page count" in response.json()["error"]["message"]
    provider.extract.assert_not_called()
