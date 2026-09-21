from unittest.mock import Mock

import pytest
from google.api_core import exceptions
from google.cloud import vision

from app.config import Settings
from app.errors import PermanentProviderError, TransientProviderError
from app.models import OcrDocument
from app.providers.google_cloud_vision import GoogleCloudVisionOcrProvider


def annotation(text="Total: 12.50", confidences=(.95, .97)):
    words = [
        vision.Word(symbols=[vision.Symbol(text=c) for c in word], confidence=score)
        for word, score in zip(text.split(), confidences)
    ]
    return vision.AnnotateImageResponse(full_text_annotation=vision.TextAnnotation(
        text=text, pages=[vision.Page(blocks=[vision.Block(paragraphs=[vision.Paragraph(words=words)])])],
    ))


def test_google_sdk_normalized_and_sdk_retry_disabled():
    client = Mock()
    client.document_text_detection.return_value = annotation()
    provider = GoogleCloudVisionOcrProvider(Settings(provider_timeout_seconds=7), client=client)
    result = provider.extract(OcrDocument(b"controlled fixture", "image/png"))
    assert result.text == "Total: 12.50"
    assert result.confidence == pytest.approx(.95)
    assert result.lines[0].confidence == pytest.approx(.95)
    kwargs = client.document_text_detection.call_args.kwargs
    assert kwargs["retry"] is None and kwargs["timeout"] == 7
    assert kwargs["image"].content == b"controlled fixture"
    assert not hasattr(result, "full_text_annotation")


@pytest.mark.parametrize("error, expected", [
    (exceptions.ServiceUnavailable("private-message"), TransientProviderError),
    (exceptions.DeadlineExceeded("private-message"), TransientProviderError),
    (exceptions.InternalServerError("private-message"), TransientProviderError),
    (exceptions.InvalidArgument("private-message"), PermanentProviderError),
    (exceptions.PermissionDenied("private-message"), PermanentProviderError),
    (exceptions.Unauthenticated("private-message"), PermanentProviderError),
    (exceptions.ResourceExhausted("private-message"), PermanentProviderError),
])
def test_transport_exceptions_translated_without_leaking_messages(error, expected):
    client = Mock()
    client.document_text_detection.side_effect = error
    with pytest.raises(expected) as failure:
        GoogleCloudVisionOcrProvider(Settings(), client=client).extract(OcrDocument(b"x", "image/png"))
    assert "private-message" not in str(failure.value)
    client.document_text_detection.assert_called_once()


@pytest.mark.parametrize("code,expected", [(14, TransientProviderError), (4, TransientProviderError),
                                          (13, TransientProviderError), (3, PermanentProviderError),
                                          (7, PermanentProviderError), (8, PermanentProviderError)])
def test_embedded_errors_are_not_treated_as_success(code, expected):
    client = Mock()
    client.document_text_detection.return_value = vision.AnnotateImageResponse(error={"code": code})
    with pytest.raises(expected):
        GoogleCloudVisionOcrProvider(Settings(), client=client).extract(OcrDocument(b"x", "image/png"))


def test_pdf_requests_all_validated_pages_and_combines_them():
    client = Mock()
    first, second = annotation("Invoice date: 2026-09-19"), annotation()
    first.context.page_number = 1
    second.context.page_number = 2
    client.batch_annotate_files.return_value = vision.BatchAnnotateFilesResponse(responses=[
        vision.AnnotateFileResponse(responses=[second, first], total_pages=2),
    ])
    result = GoogleCloudVisionOcrProvider(Settings(), client=client).extract(OcrDocument(b"%PDF-fixture", "application/pdf", 2))
    assert "Invoice date" in result.text and "Total: 12.50" in result.text
    assert result.text.startswith("Invoice date")
    kwargs = client.batch_annotate_files.call_args.kwargs
    assert list(kwargs["requests"][0].pages) == [1, 2]
    assert kwargs["retry"] is None and kwargs["timeout"] == 20
    client.document_text_detection.assert_not_called()


def test_incomplete_pdf_response_fails_instead_of_creating_complete_draft():
    client = Mock()
    client.batch_annotate_files.return_value = vision.BatchAnnotateFilesResponse(responses=[
        vision.AnnotateFileResponse(responses=[annotation()], total_pages=2),
    ])
    with pytest.raises(PermanentProviderError, match="could not process"):
        GoogleCloudVisionOcrProvider(Settings(), client=client).extract(OcrDocument(b"pdf", "application/pdf", 2))


def test_empty_document_has_no_fabricated_confidence():
    client = Mock()
    client.document_text_detection.return_value = vision.AnnotateImageResponse()
    result = GoogleCloudVisionOcrProvider(Settings(), client=client).extract(OcrDocument(b"x", "image/png"))
    assert result.text == "" and result.confidence is None


def test_uncertain_contact_does_not_lower_independent_total_line():
    client = Mock()
    client.document_text_detection.return_value = annotation("Vendor: unclear\nTotal: 12.50", (.99, .30, .99, .99))
    result = GoogleCloudVisionOcrProvider(Settings(), client=client).extract(OcrDocument(b"x", "image/png"))
    assert result.lines[0].confidence == pytest.approx(.30)
    assert result.lines[1].confidence == pytest.approx(.99)


def test_provider_output_size_limit():
    client = Mock()
    client.document_text_detection.return_value = annotation()
    with pytest.raises(PermanentProviderError):
        GoogleCloudVisionOcrProvider(Settings(max_text_chars=3), client=client).extract(OcrDocument(b"x", "image/png"))


def test_duplicate_pdf_pages_are_not_mistaken_for_complete_extraction():
    client = Mock()
    first = annotation()
    first.context.page_number = 1
    client.batch_annotate_files.return_value = vision.BatchAnnotateFilesResponse(responses=[
        vision.AnnotateFileResponse(responses=[first, first], total_pages=2),
    ])
    with pytest.raises(PermanentProviderError):
        GoogleCloudVisionOcrProvider(Settings(), client=client).extract(OcrDocument(b"pdf", "application/pdf", 2))


def test_unmatched_punctuation_does_not_hide_low_confidence_amount():
    result = GoogleCloudVisionOcrProvider._line_evidence("Total: 12.50", [("Total", .99), ("12.50", .3)])
    assert result[0].confidence == .3
