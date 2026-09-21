from unittest.mock import Mock

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.errors import InvalidDocumentError, PermanentProviderError, TransientProviderError
from app.main import create_app, get_extraction_service
from app.models import NormalizedOcrResult, OcrDocument


REQUEST = {"image_url": "https://documents.example/invoice.png", "document_type_hint": "invoice"}
EVIDENCE = NormalizedOcrResult("INVOICE\nInvoice date: 2026-09-19\nTotal: 1250.50", .96)


def client_for(result=EVIDENCE):
    provider = Mock()
    provider.extract.return_value = result
    loader = Mock()
    loader.load.return_value = OcrDocument(b"fixture", "image/png")
    sleep = Mock()
    app = create_app(Settings(), provider=provider, loader=loader, sleep=sleep)
    return TestClient(app, raise_server_exceptions=False), provider, loader, sleep


def test_partial_draft_keeps_supported_fields_and_explicit_null_contact():
    client, provider, loader, sleep = client_for()
    response = client.post("/extract", json=REQUEST)
    assert response.status_code == 200
    assert response.json() == {
        "date": "2026-09-19", "amount": 1250.50, "vendor_or_party": None,
        "category": "unknown", "confidence": "medium", "document_type_detected": "invoice",
    }
    assert isinstance(response.json()["amount"], (int, float))
    provider.extract.assert_called_once()
    loader.load.assert_called_once_with(REQUEST["image_url"])
    sleep.assert_not_called()


@pytest.mark.parametrize("change", [
    {"image_url": ""}, {"image_url": "not a url"}, {"image_url": "file:///C:/secret"},
    {"image_url": "ftp://documents.example/a"}, {"image_url": "https://user:pass@documents.example/a"},
    {"image_url": "https://documents.example/a#fragment"}, {"image_url": "https://documents.example:99999/a"},
    {"image_url": "https://documents.example/a b"}, {"image_url": 42},
    {"document_type_hint": "INVOICE"}, {"document_type_hint": None}, {"additional": "field"},
])
def test_invalid_request_never_calls_loader_or_provider(change):
    client, provider, loader, sleep = client_for()
    response = client.post("/extract", json={**REQUEST, **change})
    assert response.status_code == 422
    provider.extract.assert_not_called()
    loader.load.assert_not_called()
    sleep.assert_not_called()


@pytest.mark.parametrize("payload", [{}, {"image_url": REQUEST["image_url"]}, {"document_type_hint": "unknown"}])
def test_both_contract_fields_are_required(payload):
    client, provider, _, _ = client_for()
    assert client.post("/extract", json=payload).status_code == 422
    provider.extract.assert_not_called()


def test_validation_errors_do_not_echo_signed_url_or_input():
    client, _, _, _ = client_for()
    response = client.post("/extract", json={**REQUEST, "image_url": "bad?signature=fixture-secret"})
    assert "fixture-secret" not in response.text
    assert response.json()["error"]["code"] == "invalid_request"


@pytest.mark.parametrize("failures", [0, 1, 2])
def test_transient_failures_retry_exactly_with_configured_delays(failures):
    client, provider, loader, sleep = client_for()
    provider.extract.side_effect = [TransientProviderError() for _ in range(failures)] + [EVIDENCE]
    assert client.post("/extract", json=REQUEST).status_code == 200
    assert provider.extract.call_count == failures + 1
    assert [c.args[0] for c in sleep.call_args_list] == [2, 4][:failures]
    loader.load.assert_called_once()


def test_exhausted_retries_return_failure_not_empty_success():
    client, provider, _, sleep = client_for()
    provider.extract.side_effect = TransientProviderError()
    response = client.post("/extract", json=REQUEST)
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "provider_retries_exhausted"
    assert provider.extract.call_count == 3
    assert [c.args[0] for c in sleep.call_args_list] == [2, 4]


def test_permanent_error_is_not_retried():
    client, provider, _, sleep = client_for()
    provider.extract.side_effect = PermanentProviderError()
    assert client.post("/extract", json=REQUEST).status_code == 502
    provider.extract.assert_called_once()
    sleep.assert_not_called()


def test_invalid_document_never_calls_provider():
    client, provider, loader, sleep = client_for()
    loader.load.side_effect = InvalidDocumentError("Document is invalid")
    assert client.post("/extract", json=REQUEST).status_code == 422
    provider.extract.assert_not_called()
    sleep.assert_not_called()


def test_empty_text_is_an_unconfirmed_low_confidence_result():
    client, _, _, _ = client_for(NormalizedOcrResult(""))
    body = client.post("/extract", json=REQUEST).json()
    assert body["date"] is None and body["amount"] is None and body["vendor_or_party"] is None
    assert body["confidence"] == "low"
    assert "status" not in body and "confirmed" not in body


def test_dependency_can_be_replaced_without_initializing_adc():
    client, _, _, _ = client_for()
    replacement = Mock()
    replacement.extract.return_value = {
        "date": None, "amount": None, "vendor_or_party": "Fixture vendor", "category": "unknown",
        "confidence": "low", "document_type_detected": "unknown",
    }
    client.app.dependency_overrides[get_extraction_service] = lambda: replacement
    assert client.post("/extract", json=REQUEST).json()["vendor_or_party"] == "Fixture vendor"


def test_capacity_is_released_after_terminal_failure():
    client, provider, _, _ = client_for()
    service = client.app.state.extraction_service
    for _ in range(4):
        assert service.capacity.acquire(blocking=False)
    response = client.post("/extract", json=REQUEST)
    assert response.json()["error"]["code"] == "service_busy"
    provider.extract.assert_not_called()
    for _ in range(4):
        service.capacity.release()
    provider.extract.side_effect = PermanentProviderError()
    assert client.post("/extract", json=REQUEST).status_code == 502
    provider.extract.side_effect = None
    assert client.post("/extract", json=REQUEST).status_code == 200


def test_amount_that_would_lose_precision_is_blank_with_other_fields_preserved():
    evidence = NormalizedOcrResult("Invoice\nDate: 2026-09-19\nTotal: 1234567890123456.78", .99)
    client, _, _, _ = client_for(evidence)
    response = client.post("/extract", json=REQUEST)
    assert response.status_code == 200
    assert response.json()["amount"] is None
    assert response.json()["date"] == "2026-09-19"
    assert response.json()["confidence"] == "low"
