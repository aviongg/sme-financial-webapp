package com.app.sme_health_backend.commercial.service;

import com.app.sme_health_backend.commercial.dto.CommercialDocumentDraftRequest;
import com.app.sme_health_backend.commercial.dto.CommercialLineRequest;
import com.app.sme_health_backend.commercial.model.CommercialDocumentRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single authoritative F04 Phase A commercial-line arithmetic.
 * lineAmount = quantity x unitPrice exactly; subtotal = sum(lineAmount); total = subtotal (no tax/discount).
 * No rounding policy exists: a product that is not exactly representable with 2 decimals is rejected.
 */
@Component
public class CommercialDocumentCalculator {
    public static final String BASE_CURRENCY = "PKR";
    public static final String CALCULATION_VERSION = "f04-commercial-v1";
    public static final String FINANCIAL_EFFECT = "NONE";
    public static final String SETTLEMENT_AVAILABILITY = "NOT_AVAILABLE_PHASE_A";
    public static final int MAX_LINES = 100;
    public static final int MAX_COUNTERPARTY_LENGTH = 160;
    public static final int MAX_NOTES_LENGTH = 1000;
    public static final int MAX_DESCRIPTION_LENGTH = 200;
    public static final int MAX_UNIT_LENGTH = 40;
    public static final int MAX_VOID_REASON_LENGTH = 500;
    /** NUMERIC(18, 2) capacity for both line amounts and document totals. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");

    private static final Pattern DECIMAL = Pattern.compile("([0-9]+)(?:\\.([0-9]+))?");
    private static final Pattern ISO_DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final int QUANTITY_INTEGER_DIGITS = 9;
    private static final int QUANTITY_FRACTION_DIGITS = 6;
    private static final int PRICE_INTEGER_DIGITS = 12;
    private static final int PRICE_FRACTION_DIGITS = 2;

    public record CalculatedLine(
            int lineNumber,
            String description,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal lineAmount
    ) { }

    /** subtotal/total are null when there are no lines; an explicit zero-priced line yields 0.00. */
    public record CalculatedLines(List<CalculatedLine> lines, BigDecimal subtotal, BigDecimal total) { }

    public record DraftContent(
            String counterpartyName,
            LocalDate documentDate,
            LocalDate dueDate,
            String notes,
            CalculatedLines calculation
    ) { }

    public CalculatedLines calculateLines(List<CommercialLineRequest> lines) {
        if (lines == null) {
            throw new CommercialValidationException("lines", "required");
        }
        if (lines.size() > MAX_LINES) {
            throw new CommercialValidationException("lines", "line_count_invalid");
        }
        List<CalculatedLine> calculated = new ArrayList<>(lines.size());
        BigDecimal subtotal = null;
        for (int index = 0; index < lines.size(); index++) {
            String path = "lines[" + index + "]";
            CommercialLineRequest line = lines.get(index);
            if (line == null) {
                throw new CommercialValidationException(path, "object_required");
            }
            String description = requiredText(line.description(), path + ".description", MAX_DESCRIPTION_LENGTH);
            String unit = optionalText(line.unit(), path + ".unit", MAX_UNIT_LENGTH, false);
            BigDecimal quantity = parseDecimal(line.quantity(), path + ".quantity",
                    QUANTITY_INTEGER_DIGITS, QUANTITY_FRACTION_DIGITS);
            if (quantity.signum() <= 0) {
                throw new CommercialValidationException(path + ".quantity", "quantity_must_be_positive");
            }
            BigDecimal unitPrice = parseDecimal(line.unitPrice(), path + ".unitPrice",
                    PRICE_INTEGER_DIGITS, PRICE_FRACTION_DIGITS).setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal lineAmount = exactAmount(quantity.multiply(unitPrice), path + ".lineAmount");
            calculated.add(new CalculatedLine(index + 1, description, quantity, unit, unitPrice, lineAmount));
            subtotal = subtotal == null ? lineAmount : subtotal.add(lineAmount);
        }
        if (subtotal != null && subtotal.compareTo(MAX_AMOUNT) > 0) {
            throw new CommercialValidationException("total", "total_out_of_range");
        }
        return new CalculatedLines(List.copyOf(calculated), subtotal, subtotal);
    }

    public DraftContent validateDraft(CommercialDocumentDraftRequest request) {
        if (request == null) {
            throw new CommercialValidationException("request", "required");
        }
        String counterparty = optionalText(request.counterpartyName(), "counterpartyName", MAX_COUNTERPARTY_LENGTH, false);
        LocalDate documentDate = parseDate(request.documentDate(), "documentDate");
        LocalDate dueDate = parseDate(request.dueDate(), "dueDate");
        if (documentDate != null && dueDate != null && dueDate.isBefore(documentDate)) {
            throw new CommercialValidationException("dueDate", "due_date_before_document_date");
        }
        String notes = optionalText(request.notes(), "notes", MAX_NOTES_LENGTH, true);
        return new DraftContent(counterparty, documentDate, dueDate, notes, calculateLines(request.lines()));
    }

    public String validateVoidReason(String reason) {
        return requiredText(reason, "reason", MAX_VOID_REASON_LENGTH);
    }

    /** Machine codes describing what still prevents finalization; empty when a draft is ready. */
    public List<String> finalizeBlockers(CommercialDocumentRecord document) {
        if (!document.status().canFinalize()) {
            return List.of();
        }
        List<String> blockers = new ArrayList<>();
        if (document.counterpartyName() == null) {
            blockers.add("counterparty_required");
        }
        if (document.documentDate() == null) {
            blockers.add("document_date_required");
        }
        if (document.lineCount() == 0) {
            blockers.add("lines_required");
        } else if (document.total() == null || document.total().signum() <= 0) {
            blockers.add("total_must_be_positive");
        }
        return List.copyOf(blockers);
    }

    public static String amount(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    public static String quantity(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    private static BigDecimal exactAmount(BigDecimal product, String field) {
        if (product.stripTrailingZeros().scale() > 2) {
            throw new CommercialValidationException(field, "line_amount_not_exact");
        }
        BigDecimal amount = product.setScale(2, RoundingMode.UNNECESSARY);
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new CommercialValidationException(field, "line_amount_out_of_range");
        }
        return amount;
    }

    private static BigDecimal parseDecimal(String value, String field, int integerDigits, int fractionDigits) {
        if (value == null) {
            throw new CommercialValidationException(field, "required");
        }
        Matcher matcher = DECIMAL.matcher(value);
        if (value.length() > 40 || !matcher.matches()) {
            throw new CommercialValidationException(field, "decimal_invalid");
        }
        String integer = matcher.group(1).replaceFirst("^0+(?=[0-9])", "");
        String fraction = matcher.group(2);
        if (fraction != null && fraction.length() > fractionDigits) {
            throw new CommercialValidationException(field, "too_many_decimal_places");
        }
        if (integer.length() > integerDigits) {
            throw new CommercialValidationException(field, "out_of_range");
        }
        return new BigDecimal(value);
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null) {
            return null;
        }
        if (!ISO_DATE.matcher(value).matches()) {
            throw new CommercialValidationException(field, "date_invalid");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new CommercialValidationException(field, "date_invalid");
        }
    }

    private static String requiredText(String value, String field, int maxLength) {
        String text = optionalText(value, field, maxLength, false);
        if (text == null) {
            throw new CommercialValidationException(field, "required");
        }
        return text;
    }

    /** Trims; blank becomes null. Control characters (newlines only when multiline) and overlong values are rejected, never truncated. */
    private static String optionalText(String value, String field, int maxLength, boolean multiline) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.chars().anyMatch(ch -> Character.isISOControl(ch) && !(multiline && ch == '\n'))) {
            throw new CommercialValidationException(field, "text_invalid");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > maxLength) {
            throw new CommercialValidationException(field, "too_long");
        }
        return trimmed;
    }
}
