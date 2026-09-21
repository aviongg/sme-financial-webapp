from datetime import date
from decimal import Decimal
import json
from pathlib import Path

import pytest

from app.models import FieldCandidate, NormalizedOcrResult, OcrLine
from app.parser import parse_document


def ocr(text: str, confidence: float | None = 0.97, candidates: tuple = ()) -> NormalizedOcrResult:
    return NormalizedOcrResult(text=text, confidence=confidence, candidates=candidates)


def test_partial_invoice_keeps_known_fields_and_leaves_contact_blank():
    fixture = json.loads((Path(__file__).parent / "fixtures" / "partial_invoice.json").read_text())
    result = parse_document(ocr(fixture["text"], fixture["confidence"]))
    expected = fixture["expected"]
    assert result.date == date.fromisoformat(expected["date"])
    assert result.amount == Decimal(expected["amount"])
    assert result.vendor_or_party is None
    assert result.category == expected["category"]
    assert result.confidence == expected["confidence"]
    assert result.document_type_detected == expected["document_type_detected"]


@pytest.mark.parametrize("hint", ["unknown", "invoice", "receipt", "bank_statement"])
def test_empty_text_is_blank_even_when_type_is_hinted(hint):
    result = parse_document(ocr(""), hint)
    assert result.date is None
    assert result.amount is None
    assert result.vendor_or_party is None
    assert result.category == "unknown"
    assert result.document_type_detected == "unknown"
    assert result.confidence == "low"


@pytest.mark.parametrize("raw", ["09/08/2026", "08-09-2026", "09.08.2026", "09/08/26", "2026-02-30"])
def test_ambiguous_or_invalid_dates_stay_blank(raw):
    result = parse_document(ocr(f"Invoice\nInvoice Date: {raw}\nTotal: 10.00"))
    assert result.date is None
    assert result.amount == Decimal("10.00")
    assert result.confidence == "low"


@pytest.mark.parametrize("raw", ["2026-09-18", "2026/09/18", "18 September 2026", "September 18, 2026", "18 Sep. 2026", "18/09/2026", "09/18/2026", "2026-09-18 10:30"])
def test_unambiguous_labelled_dates_are_preserved(raw):
    result = parse_document(ocr(f"Invoice Date: {raw}\nDue Date: 2026-10-01"))
    assert result.date == date(2026, 9, 18)


def test_competing_issue_dates_are_not_selected_arbitrarily():
    result = parse_document(ocr("Invoice Date: 2026-09-18\nDate: 2026-09-19"))
    assert result.date is None
    assert result.confidence == "low"


def test_due_date_is_not_used_as_invoice_date():
    result = parse_document(ocr("Invoice\nDue Date: 2026-09-18\nTotal: 10.00"))
    assert result.date is None


def test_competing_totals_are_blank_and_other_fields_are_preserved():
    result = parse_document(ocr("Invoice\nVendor: Example Supplies\nInvoice Date: 2026-09-18\nGrand Total: 120.00\nAmount Due: 20.00"))
    assert result.amount is None
    assert result.date == date(2026, 9, 18)
    assert result.vendor_or_party == "Example Supplies"
    assert result.confidence == "low"


def test_duplicate_matching_totals_are_not_a_conflict():
    result = parse_document(ocr("Receipt\nTotal: 12.50\nGrand Total: 12.50"))
    assert result.amount == Decimal("12.50")
    assert result.confidence == "medium"


@pytest.mark.parametrize("raw,expected", [("PKR 1,380.00", "1380.00"), ("$12.50", "12.50"), ("1.380,00 EUR", "1380.00"), ("12,50 EUR", "12.50"), ("1 380.00", "1380.00"), ("1380", "1380"), ("(12.50)", "-12.50")])
def test_labelled_currency_amounts_are_normalized(raw, expected):
    assert parse_document(ocr(f"Grand Total: {raw}")).amount == Decimal(expected)


@pytest.mark.parametrize("raw", ["1,380", "1.380", "NaN", "Infinity", "12O.OO", "invoice 123", "12.50 25.00", "3 items", "10%"])
def test_ambiguous_or_malformed_totals_stay_blank(raw):
    assert parse_document(ocr(f"Total: {raw}")).amount is None


def test_absurd_amount_tokens_cannot_produce_infinite_wire_numbers():
    result = parse_document(ocr("Total: " + "9" * 400))
    assert result.amount is None
    assert json.loads(result.model_dump_json())["amount"] is None
    largest_token = parse_document(ocr("Total: " + "9" * 64))
    assert largest_token.amount is None
    assert json.loads(largest_token.model_dump_json())["amount"] is None


def test_invoice_ids_subtotals_tax_cash_and_balances_do_not_become_amounts():
    result = parse_document(ocr("Invoice No: 123456\nAccount: 12345678\nSubtotal: 100.00\nTax: 15.00\nCash: 200.00\nChange: 85.00\nClosing Balance: 500.00\nTotal items: 3"))
    assert result.amount is None
    assert result.document_type_detected == "invoice"


def test_bank_statement_does_not_choose_an_arbitrary_statement_amount():
    result = parse_document(ocr("BANK STATEMENT\nDate: 2026-09-18\nTotal: 1500.00\nClosing Balance: 2000.00", candidates=(FieldCandidate(field="amount", value="1500.00", confidence=0.99),)))
    assert result.document_type_detected == "bank_statement"
    assert result.amount is None
    assert result.date == date(2026, 9, 18)


def test_conflicting_bank_evidence_still_prevents_an_arbitrary_amount():
    result = parse_document(ocr("Bank Statement\nTotal: 100.00", candidates=(FieldCandidate(field="document_type_detected", value="invoice", confidence=0.99),)))
    assert result.document_type_detected == "unknown"
    assert result.amount is None


def test_generic_customer_statement_is_not_called_a_bank_statement():
    assert parse_document(ocr("Statement of Account\nVendor: Example Supplies")).document_type_detected == "unknown"


@pytest.mark.parametrize("label", ["Vendor", "Merchant", "Supplier", "Seller", "Sold by", "From"])
def test_explicit_vendor_labels_preserve_names(label):
    assert parse_document(ocr(f"{label}: Example Supplies Ltd.")).vendor_or_party == "Example Supplies Ltd."


def test_explicit_vendor_name_label_omits_the_label_from_the_name():
    assert parse_document(ocr("Vendor Name: Example Supplies Ltd.")).vendor_or_party == "Example Supplies Ltd."


@pytest.mark.parametrize("line", ["Vendor ID: A123", "Vendor Address: Street 1", "Vendor Email: accounts@example.test", "From: September 18, 2026", "Vendor:\nDate: 2026-09-18"])
def test_other_party_metadata_is_not_used_as_a_name(line):
    assert parse_document(ocr(line)).vendor_or_party is None


@pytest.mark.parametrize("party", ["No Contact", "Unknown", "N/A", "Not available", "123456", "accounts@example.test"])
def test_missing_or_non_name_party_values_remain_null(party):
    assert parse_document(ocr(f"Vendor: {party}")).vendor_or_party is None


def test_unlabelled_headers_and_bill_to_names_do_not_guess_the_party_role():
    result = parse_document(ocr("Example Supplies Ltd.\nINVOICE\nBill To: Example Buyer\nCustomer: Example Buyer"))
    assert result.vendor_or_party is None
    assert result.category == "unknown"


def test_conflicting_vendors_remain_blank():
    result = parse_document(ocr("Vendor: Example Supplies\nSupplier: Another Company"))
    assert result.vendor_or_party is None
    assert result.confidence == "low"


def test_financial_category_is_not_inferred_from_document_type():
    result = parse_document(ocr("Receipt\nVendor: Example Shop\nDate: 2026-09-18\nTotal: 20.00"))
    assert result.category == "unknown"
    assert result.confidence == "medium"


def test_fully_supported_fields_with_explicit_business_role_can_be_high_confidence():
    result = parse_document(ocr("Receipt\nMerchant: Example Shop\nDate: 2026-09-18\nTotal: 20.00\nTransaction category: expense"))
    assert result.category == "expense"
    assert result.confidence == "high"


def test_missing_provider_confidence_caps_extraction_at_medium():
    result = parse_document(ocr("Invoice\nVendor: Example Shop\nDate: 2026-09-18\nTotal: 20.00\nDocument role: purchase", confidence=None))
    assert result.date == date(2026, 9, 18)
    assert result.amount == Decimal("20.00")
    assert result.confidence == "medium"


def test_low_confidence_ocr_does_not_fill_unreliable_text_fields():
    result = parse_document(ocr("Invoice\nVendor: Example Shop\nDate: 2026-09-18\nTotal: 20.00", confidence=0.5))
    assert result.date is None
    assert result.amount is None
    assert result.vendor_or_party is None
    assert result.document_type_detected == "unknown"
    assert result.confidence == "low"


def test_high_confidence_structured_field_can_survive_low_document_confidence():
    result = parse_document(ocr("unreadable", confidence=0.5, candidates=(FieldCandidate(field="amount", value="20.00", confidence=0.98),)))
    assert result.amount == Decimal("20.00")
    assert result.vendor_or_party is None
    assert result.confidence == "medium"


def test_low_confidence_structured_candidate_does_not_override_clear_text():
    result = parse_document(ocr("Total: 20.00", candidates=(FieldCandidate(field="amount", value="30.00", confidence=0.4),)))
    assert result.amount == Decimal("20.00")


def test_conflicting_structured_candidate_and_text_remain_blank():
    result = parse_document(ocr("Total: 20.00", candidates=(FieldCandidate(field="amount", value="30.00", confidence=0.99),)))
    assert result.amount is None
    assert result.confidence == "low"


def test_provider_candidates_use_the_same_normalization_and_null_rules():
    result = parse_document(ocr("", candidates=(
        FieldCandidate(field="date", value="09/08/2026", confidence=0.99),
        FieldCandidate(field="amount", value="PKR 1,380.00", confidence=0.99),
        FieldCandidate(field="vendor_or_party", value="No Contact", confidence=0.99),
        FieldCandidate(field="document_type_detected", value="invoice", confidence=0.99),
        FieldCandidate(field="unrelated_sdk_property", value="ignored", confidence=0.99),
    )))
    assert result.date is None
    assert result.amount == Decimal("1380.00")
    assert result.vendor_or_party is None
    assert result.document_type_detected == "invoice"


def test_hint_does_not_override_observed_document_type():
    assert parse_document(ocr("Receipt\nTotal: 20.00"), "invoice").document_type_detected == "receipt"


def test_receipt_with_invoice_reference_is_still_detected_as_receipt():
    assert parse_document(ocr("Receipt\nInvoice No: TEST-0001\nTotal: 20.00")).document_type_detected == "receipt"


def test_conflicting_document_titles_stay_unknown():
    result = parse_document(ocr("Invoice\nReceipt\nTotal: 20.00"), "invoice")
    assert result.document_type_detected == "unknown"
    assert result.confidence == "low"


def test_values_on_next_line_are_supported_for_explicit_labels():
    result = parse_document(ocr("Invoice\nVendor:\nExample Supplies\nInvoice Date:\n2026-09-18\nGrand Total:\n12.50"))
    assert result.vendor_or_party == "Example Supplies"
    assert result.date == date(2026, 9, 18)
    assert result.amount == Decimal("12.50")


def test_unreadable_contact_does_not_discard_readable_date_and_total():
    lines = (
        OcrLine(text="Invoice", confidence=0.99),
        OcrLine(text="Invoice Date: 2026-09-18", confidence=0.99),
        OcrLine(text="Grand Total: 1380.00", confidence=0.99),
        OcrLine(text="Vendor: Uncertain Name", confidence=0.3),
    )
    result = parse_document(NormalizedOcrResult(
        text="\n".join(line.text for line in lines), confidence=0.3, lines=lines,
    ))
    assert result.date == date(2026, 9, 18)
    assert result.amount == Decimal("1380.00")
    assert result.vendor_or_party is None
    assert result.document_type_detected == "invoice"
    assert result.confidence == "low"


def test_unclear_competing_total_is_not_ignored_in_favor_of_confident_total():
    lines = (
        OcrLine(text="Total: 20.00", confidence=0.99),
        OcrLine(text="Grand Total: 30.00", confidence=0.3),
        OcrLine(text="Vendor: Example Supplies", confidence=0.99),
    )
    result = parse_document(NormalizedOcrResult(
        text="\n".join(line.text for line in lines), confidence=0.99, lines=lines,
    ))
    assert result.amount is None
    assert result.vendor_or_party == "Example Supplies"
    assert result.confidence == "low"


def test_split_label_and_value_require_reliable_confidence_on_both_lines():
    lines = (
        OcrLine(text="Vendor:", confidence=0.99),
        OcrLine(text="Example Supplies", confidence=0.2),
        OcrLine(text="Total:", confidence=0.4),
        OcrLine(text="12.50", confidence=0.99),
        OcrLine(text="Date:", confidence=0.99),
        OcrLine(text="2026-09-18", confidence=0.99),
    )
    result = parse_document(NormalizedOcrResult(text="", confidence=0.99, lines=lines))
    assert result.vendor_or_party is None
    assert result.amount is None
    assert result.date == date(2026, 9, 18)


def test_unknown_line_confidence_caps_complete_extraction_at_medium():
    lines = tuple(OcrLine(text=text, confidence=None) for text in (
        "Invoice", "Date: 2026-09-18", "Vendor: Example Supplies",
        "Total: 12.50", "Document role: purchase",
    ))
    result = parse_document(NormalizedOcrResult(text="", confidence=0.99, lines=lines))
    assert result.amount == Decimal("12.50")
    assert result.vendor_or_party == "Example Supplies"
    assert result.confidence == "medium"


def test_low_confidence_document_title_does_not_override_confident_title():
    lines = (OcrLine(text="Invoice", confidence=0.99), OcrLine(text="Receipt", confidence=0.2))
    result = parse_document(NormalizedOcrResult(text="", confidence=0.99, lines=lines))
    assert result.document_type_detected == "unknown"
