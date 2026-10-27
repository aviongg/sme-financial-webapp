package com.app.sme_health_backend.commercial;

import com.app.sme_health_backend.commercial.dto.CommercialDocumentDraftRequest;
import com.app.sme_health_backend.commercial.dto.CommercialLineRequest;
import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.model.CommercialDocumentRecord;
import com.app.sme_health_backend.commercial.model.CommercialDocumentStatus;
import com.app.sme_health_backend.commercial.service.CommercialDocumentCalculator;
import com.app.sme_health_backend.commercial.service.CommercialValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommercialDocumentCalculatorTests {
    private final CommercialDocumentCalculator calculator = new CommercialDocumentCalculator();

    private static CommercialLineRequest line(String quantity, String unitPrice) {
        return new CommercialLineRequest("Service work", quantity, "hour", unitPrice);
    }

    private Map<String, String> rejected(List<CommercialLineRequest> lines) {
        return assertThrows(CommercialValidationException.class, () -> calculator.calculateLines(lines)).errors();
    }

    @Test
    void exactQuantityTimesPriceIsAuthoritativeWithTwoDecimalAmounts() {
        var result = calculator.calculateLines(List.of(line("2", "500.00")));
        assertEquals(new BigDecimal("1000.00"), result.lines().getFirst().lineAmount());
        assertEquals("1000.00", CommercialDocumentCalculator.amount(result.subtotal()));
        assertEquals(result.subtotal(), result.total());

        var fractional = calculator.calculateLines(List.of(line("1.5", "333.34"), line("0.25", "100")));
        assertEquals("500.01", CommercialDocumentCalculator.amount(fractional.lines().get(0).lineAmount()));
        assertEquals("25.00", CommercialDocumentCalculator.amount(fractional.lines().get(1).lineAmount()));
        assertEquals("525.01", CommercialDocumentCalculator.amount(fractional.total()));
    }

    @Test
    void nonExactTwoDecimalProductIsRejectedWithoutRounding() {
        assertEquals(Map.of("lines[0].lineAmount", "line_amount_not_exact"), rejected(List.of(line("1.5", "333.33"))));
        // Six-decimal quantities are allowed only when the product is still exact to the paisa.
        assertEquals("1.00", CommercialDocumentCalculator.amount(
                calculator.calculateLines(List.of(line("0.000001", "1000000"))).total()));
        assertEquals(Map.of("lines[1].lineAmount", "line_amount_not_exact"),
                rejected(List.of(line("1", "10"), line("0.333333", "3"))));
    }

    @Test
    void zeroPricedLinesProduceExplicitZeroWhileNoLinesProduceNull() {
        var zero = calculator.calculateLines(List.of(line("3", "0")));
        assertEquals("0.00", CommercialDocumentCalculator.amount(zero.lines().getFirst().lineAmount()));
        assertEquals("0.00", CommercialDocumentCalculator.amount(zero.subtotal()));
        assertEquals("0.00", CommercialDocumentCalculator.amount(zero.total()));

        var empty = calculator.calculateLines(List.of());
        assertTrue(empty.lines().isEmpty());
        assertNull(empty.subtotal());
        assertNull(empty.total());
        assertNull(CommercialDocumentCalculator.amount(empty.total()));
    }

    @Test
    void multipleLineSubtotalIsTheExactSumAndTotalEqualsSubtotal() {
        var result = calculator.calculateLines(List.of(
                line("2", "500.00"), line("3.5", "120.40"), line("1", "0.01"), line("10", "0")));
        assertEquals(List.of("1000.00", "421.40", "0.01", "0.00"),
                result.lines().stream().map(l -> CommercialDocumentCalculator.amount(l.lineAmount())).toList());
        assertEquals("1421.41", CommercialDocumentCalculator.amount(result.subtotal()));
        assertEquals(result.subtotal(), result.total());
        assertEquals(List.of(1, 2, 3, 4), result.lines().stream().map(CommercialDocumentCalculator.CalculatedLine::lineNumber).toList());
    }

    @Test
    void quantityAndUnitPriceFormatsAndBoundsAreStrict() {
        assertEquals("quantity_must_be_positive", rejected(List.of(line("0", "1"))).get("lines[0].quantity"));
        assertEquals("quantity_must_be_positive", rejected(List.of(line("0.000000", "1"))).get("lines[0].quantity"));
        for (String bad : List.of("-1", "1e3", "1,000", " 1", "", ".5", "5.", "0x10", "١")) {
            assertEquals("decimal_invalid", rejected(List.of(line(bad, "1"))).get("lines[0].quantity"), bad);
            assertEquals("decimal_invalid", rejected(List.of(line("1", bad))).get("lines[0].unitPrice"), bad);
        }
        assertEquals("too_many_decimal_places", rejected(List.of(line("1.0000001", "1"))).get("lines[0].quantity"));
        assertEquals("too_many_decimal_places", rejected(List.of(line("1", "1.001"))).get("lines[0].unitPrice"));
        assertEquals("out_of_range", rejected(List.of(line("1000000000", "1"))).get("lines[0].quantity"));
        assertEquals("out_of_range", rejected(List.of(line("1", "1000000000000"))).get("lines[0].unitPrice"));
        // Leading zeros do not count toward integer capacity.
        assertEquals("5.00", CommercialDocumentCalculator.amount(calculator.calculateLines(List.of(line("0001", "005"))).total()));
        assertEquals("required", rejected(List.of(new CommercialLineRequest("x", null, null, "1"))).get("lines[0].quantity"));
        assertEquals("required", rejected(List.of(new CommercialLineRequest("  ", "1", null, "1"))).get("lines[0].description"));
    }

    @Test
    void numericCapacityLimitsForLineAmountsAndTotalsAreEnforced() {
        // Largest allowed inputs produce a product beyond NUMERIC(18, 2).
        assertEquals("line_amount_out_of_range",
                rejected(List.of(line("999999999", "999999999999.99"))).get("lines[0].lineAmount"));
        var maxLine = calculator.calculateLines(List.of(line("10000", "999999999999.99")));
        assertEquals("9999999999999900.00", CommercialDocumentCalculator.amount(maxLine.total()));
        assertEquals("total_out_of_range",
                rejected(List.of(line("10000", "999999999999.99"), line("10000", "999999999999.99"))).get("total"));
        var exactMax = calculator.calculateLines(List.of(line("1", "999999999999.99")));
        assertEquals("999999999999.99", CommercialDocumentCalculator.amount(exactMax.total()));
        List<CommercialLineRequest> hundred = new ArrayList<>(Collections.nCopies(100, line("1", "1")));
        assertEquals("100.00", CommercialDocumentCalculator.amount(calculator.calculateLines(hundred).total()));
        hundred.add(line("1", "1"));
        assertEquals("line_count_invalid", rejected(hundred).get("lines"));
    }

    @Test
    void draftMetadataIsNormalizedAndValidated() {
        var content = calculator.validateDraft(new CommercialDocumentDraftRequest(
                "  Acme Traders  ", "2026-10-28", "2026-11-27", "  Net 30\nThank you  ", List.of(), null));
        assertEquals("Acme Traders", content.counterpartyName());
        assertEquals(LocalDate.parse("2026-10-28"), content.documentDate());
        assertEquals("Net 30\nThank you", content.notes());
        assertNull(content.calculation().total());

        var blank = calculator.validateDraft(new CommercialDocumentDraftRequest("   ", null, null, " ", List.of(), null));
        assertNull(blank.counterpartyName());
        assertNull(blank.notes());

        Map<String, String> due = assertThrows(CommercialValidationException.class, () -> calculator.validateDraft(
                new CommercialDocumentDraftRequest(null, "2026-10-28", "2026-10-27", null, List.of(), null))).errors();
        assertEquals(Map.of("dueDate", "due_date_before_document_date"), due);
        for (String bad : List.of("2026-02-30", "28-10-2026", "2026-1-01", "2026-10-28T00:00")) {
            assertEquals("date_invalid", assertThrows(CommercialValidationException.class, () -> calculator.validateDraft(
                    new CommercialDocumentDraftRequest(null, bad, null, null, List.of(), null))).errors().get("documentDate"));
        }
        assertEquals("text_invalid", assertThrows(CommercialValidationException.class, () -> calculator.validateDraft(
                new CommercialDocumentDraftRequest("Acme\nInjected", null, null, null, List.of(), null))).errors().get("counterpartyName"));
        assertEquals("too_long", assertThrows(CommercialValidationException.class, () -> calculator.validateDraft(
                new CommercialDocumentDraftRequest("x".repeat(161), null, null, null, List.of(), null))).errors().get("counterpartyName"));
        // Urdu names are measured in code points, not bytes.
        assertEquals("احمد ٹریڈرز", calculator.validateDraft(
                new CommercialDocumentDraftRequest("احمد ٹریڈرز", null, null, null, List.of(), null)).counterpartyName());
        assertEquals("required", assertThrows(CommercialValidationException.class,
                () -> calculator.validateVoidReason("  ")).errors().get("reason"));
        assertEquals("too_long", assertThrows(CommercialValidationException.class,
                () -> calculator.validateVoidReason("r".repeat(501))).errors().get("reason"));
    }

    @Test
    void finalizeBlockersDescribeMissingRequirementsOnlyForDrafts() {
        assertEquals(List.of("counterparty_required", "document_date_required", "lines_required"),
                calculator.finalizeBlockers(record(CommercialDocumentStatus.DRAFT, null, null, 0, null)));
        assertEquals(List.of("total_must_be_positive"),
                calculator.finalizeBlockers(record(CommercialDocumentStatus.DRAFT, "Acme", LocalDate.now(), 1, BigDecimal.ZERO.setScale(2))));
        assertEquals(List.of(),
                calculator.finalizeBlockers(record(CommercialDocumentStatus.DRAFT, "Acme", LocalDate.now(), 1, new BigDecimal("0.01"))));
        assertEquals(List.of(), calculator.finalizeBlockers(record(CommercialDocumentStatus.FINALIZED, null, null, 0, null)));
    }

    private static CommercialDocumentRecord record(
            CommercialDocumentStatus status, String counterparty, LocalDate date, int lines, BigDecimal total) {
        return new CommercialDocumentRecord(UUID.randomUUID(), UUID.randomUUID(), CommercialDocumentKind.INVOICE, status,
                null, counterparty, date, null, null, total, total, "PKR", "NONE", "f04-commercial-v1", 0,
                UUID.randomUUID(), UUID.randomUUID(), null, null, null, null, null, null, null, lines, List.of());
    }
}
