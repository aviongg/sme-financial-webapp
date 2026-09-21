package com.app.sme_health_backend.documents.ocr;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** The shared six-field extraction contract. Null fields are intentionally blank draft values. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OcrExtraction(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate date,
        BigDecimal amount,
        @JsonProperty("vendor_or_party") String vendorOrParty,
        Category category,
        Confidence confidence,
        @JsonProperty("document_type_detected") DocumentType documentTypeDetected
) {
    public OcrExtraction {
        if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
            throw new IllegalArgumentException("Date must have a year from 0001 to 9999");
        }
        Objects.requireNonNull(category, "category is required");
        Objects.requireNonNull(confidence, "confidence is required");
        Objects.requireNonNull(documentTypeDetected, "document type is required");
        vendorOrParty = vendorOrParty == null || vendorOrParty.isBlank() ? null : vendorOrParty.strip();
    }

    @JsonIgnore
    public boolean isCompleteAndHighConfidence() {
        return date != null && amount != null && vendorOrParty != null
                && category != Category.unknown && documentTypeDetected != DocumentType.unknown
                && confidence == Confidence.high;
    }

    public enum Category { sales, expense, purchase, unknown }
    public enum Confidence { high, medium, low }
    public enum DocumentType { receipt, invoice, bank_statement, unknown }
}
