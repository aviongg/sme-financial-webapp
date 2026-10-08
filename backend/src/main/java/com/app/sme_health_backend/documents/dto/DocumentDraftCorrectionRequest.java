package com.app.sme_health_backend.documents.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** PATCH distinguishes an omitted value (preserve it) from explicit null (clear it). */
public final class DocumentDraftCorrectionRequest {
    private LocalDate date;
    private BigDecimal amount;
    private String vendorOrParty;
    private String category;
    private String documentType;
    private final Set<String> supplied = new HashSet<>();

    @JsonCreator
    public DocumentDraftCorrectionRequest() {}

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public DocumentDraftCorrectionRequest(LocalDate date, BigDecimal amount, String vendorOrParty, String category, String documentType) {
        setDate(date); setAmount(amount); setVendorOrParty(vendorOrParty); setCategory(category); setDocumentType(documentType);
    }

    @JsonSetter("date") public void setDate(LocalDate date) { this.date = date; supplied.add("date"); }
    @JsonSetter("amount") public void setAmount(BigDecimal amount) { this.amount = amount; supplied.add("amount"); }
    @JsonSetter("vendorOrParty") public void setVendorOrParty(String vendorOrParty) { this.vendorOrParty = vendorOrParty; supplied.add("vendorOrParty"); }
    @JsonSetter("category") public void setCategory(String category) { this.category = category; supplied.add("category"); }
    @JsonSetter("documentType") public void setDocumentType(String documentType) { this.documentType = documentType; supplied.add("documentType"); }
    public LocalDate date() { return date; }
    public BigDecimal amount() { return amount; }
    public String vendorOrParty() { return vendorOrParty; }
    public String category() { return category; }
    public String documentType() { return documentType; }
    public boolean supplies(String field) { return supplied.contains(field); }
}
