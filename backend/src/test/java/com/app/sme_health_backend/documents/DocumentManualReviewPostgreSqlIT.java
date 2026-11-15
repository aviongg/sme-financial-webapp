package com.app.sme_health_backend.documents;

import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.documents.storage.DocumentStorageService;
import com.app.sme_health_backend.documents.storage.StoredFile;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.testsupport.ProductApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/**
 * DEF-01: deliberate manual-review fallback for uploads whose OCR failed because the provider was unavailable.
 * failed(unavailable) -> manual review (needs_review) -> correction -> confirmation -> commercial evidence link,
 * with the OCR failure reason, NULL extraction and audit provenance preserved. Non-eligible failures stay blocked.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class DocumentManualReviewPostgreSqlIT extends ProductApiTestBase {
    @Autowired UploadedDocumentRepository documents;
    @Autowired DocumentStorageService storage;

    /** A real stored upload (passed the upload validator and storage) whose OCR attempt failed with {@code reason}. */
    private UUID failedUpload(Actor a, String reason) {
        byte[] pdf = ("%PDF-1.4\n% synthetic manual-review fixture " + UUID.randomUUID() + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
        StoredFile stored = storage.store(a.businessId(), new MockMultipartFile("file", "receipt.pdf", "application/pdf", pdf));
        UploadedDocument doc = new UploadedDocument();
        UUID id = UUID.randomUUID();
        doc.setId(id); doc.setUserId(a.businessId()); doc.setFileUrl(storage.resolveFileUrl(id));
        doc.setStoragePath(stored.storagePath()); doc.setOriginalFilename(stored.originalFilename());
        doc.setContentType(stored.contentType()); doc.setFileSizeBytes(stored.sizeBytes());
        doc.setUploadTimestamp(LocalDateTime.now()); doc.setDocumentTypeHint("receipt");
        doc.setProcessingStatus(DocumentStatus.failed); doc.setFailureReason(reason);
        documents.saveAndFlush(doc);
        return id;
    }

    private Map<String, Object> confirmation() {
        return map("targetMonth", TODAY.toString().substring(0, 7), "confirmedAmount", 1250.50, "confirmedDate", TODAY.toString(),
                "confirmedParty", "Synthetic Supplier", "targetClassification", "operating_expenses", "cashFlowImpact", "cash_outflow",
                "initialCashBalanceEom", null);
    }

    @Test
    void providerUnavailableFailureCanBeManuallyReviewedCorrectedConfirmedAndLinkedAsEvidence() throws Exception {
        Actor owner = owner();
        UUID doc = failedUpload(owner, "unavailable");
        String provenanceBefore = jdbc.queryForObject("SELECT extraction_provenance FROM uploaded_documents WHERE id = ?", String.class, doc);

        JsonNode failed = getJson(owner, "/api/documents/" + doc);
        assertTrue(failed.get("manualReviewEligible").asBoolean());
        // No direct confirmation bypass: a failed document still cannot be confirmed.
        postJson(owner, "/api/documents/" + doc + "/confirm", confirmation(), null, 400);

        JsonNode started = postJson(owner, "/api/documents/" + doc + "/manual-review", null, null, 200);
        assertEquals("needs_review", started.get("processingStatus").asText());
        assertTrue(started.get("extractedData").isNull(), "No OCR values may be fabricated");
        assertEquals("unavailable", started.get("failureReason").asText(), "OCR failure reason is preserved");
        assertFalse(started.get("manualReviewEligible").asBoolean());
        JsonNode draft = json.readTree(started.get("reviewedData").asText());
        assertEquals("manual", draft.get("entry_mode").asText());
        assertEquals("unavailable", draft.get("ocr_failure_reason").asText());
        assertTrue(draft.get("amount").isNull()); assertTrue(draft.get("confidence").isNull());
        var audit = jdbc.queryForMap("SELECT actor_user_id, metadata::text AS metadata FROM security_audit_events "
                + "WHERE business_id = ? AND event_type = 'DOCUMENT_MANUAL_REVIEW_STARTED' AND target_id = ?", owner.businessId(), doc.toString());
        assertEquals(owner.fixture().user().getId(), audit.get("actor_user_id"));
        assertTrue(audit.get("metadata").toString().contains("unavailable"), audit.toString());
        // Starting twice, or re-running OCR over a manual draft, is refused.
        assertEquals("manual_review_not_eligible",
                postJson(owner, "/api/documents/" + doc + "/manual-review", null, null, 409).get("error").asText());
        postJson(owner, "/api/documents/" + doc + "/retry", null, null, 400);

        // Manual entry through the existing correction contract (append-only history).
        call(owner, patch("/api/documents/" + doc).with(csrf(owner.session())).contentType("application/json")
                .content(json.writeValueAsString(map("date", TODAY.toString(), "amount", 1250.50, "vendorOrParty", "Synthetic Supplier",
                        "category", "expense", "documentType", "receipt"))), 200);
        JsonNode corrected = getJson(owner, "/api/documents/" + doc);
        JsonNode reviewed = json.readTree(corrected.get("reviewedData").asText());
        assertEquals("manual", reviewed.get("entry_mode").asText(), "Manual-entry provenance survives corrections");
        assertEquals(0, reviewed.get("amount").decimalValue().compareTo(new java.math.BigDecimal("1250.50")));
        assertTrue(corrected.get("extractedData").isNull());
        assertEquals(1, getJson(owner, "/api/documents/" + doc + "/corrections").size());

        // Existing confirmation contract, then commercial evidence linking.
        JsonNode confirmed = postJson(owner, "/api/documents/" + doc + "/confirm", confirmation(), null, 200);
        assertEquals("confirmed", confirmed.get("processingStatus").asText());
        assertEquals("unavailable", confirmed.get("failureReason").asText());
        UUID bill = finalizedDocument(owner, "bills", TODAY, List.<Object[]>of(new Object[] {"Supplies", "1", "1250.50", null}));
        JsonNode linked = postJson(owner, "/api/commercial/documents/" + bill + "/evidence", map("uploadedDocumentId", doc), null, 200);
        assertEquals(1, linked.get("evidence").size());
        assertEquals("confirmed", linked.get("evidence").get(0).get("reviewStatus").asText());

        assertEquals(provenanceBefore, jdbc.queryForObject("SELECT extraction_provenance FROM uploaded_documents WHERE id = ?", String.class, doc));
        assertNull(jdbc.queryForObject("SELECT extracted_data::text FROM uploaded_documents WHERE id = ?", String.class, doc));
    }

    @Test
    void otherTransientProviderFailuresAreEligible() throws Exception {
        Actor owner = owner();
        for (String reason : List.of("invalid_response", "processing_interrupted")) {
            UUID doc = failedUpload(owner, reason);
            assertEquals("needs_review", postJson(owner, "/api/documents/" + doc + "/manual-review", null, null, 200)
                    .get("processingStatus").asText(), reason);
        }
    }

    @Test
    void invalidStorageOrNonEligibleFailuresGainNoManualConfirmBypass() throws Exception {
        Actor owner = owner();
        // Unsupported/invalid input rejected by OCR, storage failures and unknown reasons are not eligible.
        for (String reason : new String[] {"invalid_request", "file_storage_error", "unknown_failure", null}) {
            UUID doc = failedUpload(owner, reason);
            assertFalse(getJson(owner, "/api/documents/" + doc).get("manualReviewEligible").asBoolean(), String.valueOf(reason));
            assertEquals("manual_review_not_eligible",
                    postJson(owner, "/api/documents/" + doc + "/manual-review", null, null, 409).get("error").asText());
            postJson(owner, "/api/documents/" + doc + "/confirm", confirmation(), null, 400);
            assertEquals("failed", jdbc.queryForObject("SELECT processing_status FROM uploaded_documents WHERE id = ?", String.class, doc));
            assertNull(jdbc.queryForObject("SELECT reviewed_data::text FROM uploaded_documents WHERE id = ?", String.class, doc));
        }
        // An eligible reason whose file is no longer in storage is refused.
        UUID missing = failedUpload(owner, "unavailable");
        storage.delete(documents.findById(missing).orElseThrow().getStoragePath());
        assertEquals("manual_review_not_eligible",
                postJson(owner, "/api/documents/" + missing + "/manual-review", null, null, 409).get("error").asText());
        assertEquals("failed", jdbc.queryForObject("SELECT processing_status FROM uploaded_documents WHERE id = ?", String.class, missing));
        // Only failed documents qualify (never a pending/processing/extracted bypass).
        UUID pending = failedUpload(owner, "unavailable");
        jdbc.update("UPDATE uploaded_documents SET processing_status = 'pending', failure_reason = NULL WHERE id = ?", pending);
        postJson(owner, "/api/documents/" + pending + "/manual-review", null, null, 409);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM security_audit_events WHERE business_id = ? "
                + "AND event_type = 'DOCUMENT_MANUAL_REVIEW_STARTED'", Integer.class, owner.businessId()));
    }

    @Test
    void manualReviewRespectsPermissionsTenantAndModule() throws Exception {
        Actor owner = owner();
        UUID doc = failedUpload(owner, "unavailable");
        Actor viewer = actor(owner.businessId(), MembershipRole.VIEWER);
        postJson(viewer, "/api/documents/" + doc + "/manual-review", null, null, 403);
        Actor other = owner();
        postJson(other, "/api/documents/" + doc + "/manual-review", null, null, 404);
        jdbc.update("INSERT INTO business_configuration (business_id, operating_profile, operating_answers, requested_modules, version, configured, updated_at) "
                + "VALUES (?, 'SERVICES', '{}'::jsonb, '[\"core_finance\"]'::jsonb, 1, true, now())", owner.businessId());
        assertEquals("module_disabled", postJson(owner, "/api/documents/" + doc + "/manual-review", null, null, 403).get("error").asText());
        assertEquals("failed", jdbc.queryForObject("SELECT processing_status FROM uploaded_documents WHERE id = ?", String.class, doc));
    }
}
