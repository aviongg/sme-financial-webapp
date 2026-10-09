package com.app.sme_health_backend.documents;

import com.app.sme_health_backend.documents.dto.DocumentDraftCorrectionRequest;
import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.exception.DocumentNotFoundException;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.documents.service.DocumentReviewService;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DocumentProvenancePostgreSqlIT extends DisposablePostgres {
    @Autowired DocumentReviewService reviews;
    @Autowired UploadedDocumentRepository documents;
    @Autowired AppUserRepository users;
    @Autowired BusinessService businesses;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private UUID actor, business, document;
    private static final String ORIGINAL = "{\"date\":\"2026-09-01\",\"amount\":5,\"vendor_or_party\":\"Original party\",\"category\":\"sales\",\"confidence\":\"low\",\"document_type_detected\":\"invoice\"}";
    @BeforeEach void setup() {
        AppUser user = new AppUser(); user.setEmail("provenance-" + UUID.randomUUID() + "@example.test");
        user.setFullName("Provenance fixture"); user.setPasswordHash("unused-test-password-hash"); actor = users.save(user).getId();
        business = businesses.createBusiness(new CreateBusinessRequest("Provenance business", "retail", "en", null, false, null, null, null), actor).businessId();
        UploadedDocument doc = new UploadedDocument(); document = UUID.randomUUID(); doc.setId(document); doc.setUserId(business);
        doc.setFileUrl("/api/documents/" + document + "/file"); doc.setProcessingStatus(DocumentStatus.needs_review); doc.setExtractedData(ORIGINAL);
        documents.save(doc);
    }
    private DocumentDraftCorrectionRequest correction(String amount) {
        return new DocumentDraftCorrectionRequest(LocalDate.of(2026, 9, 2), new BigDecimal(amount), "Reviewed party", "sales", "invoice");
    }
    @AfterEach void cleanup() {
        // Correction snapshots intentionally survive document/identity deletion.
        jdbc.update("DELETE FROM uploaded_documents WHERE user_id = ?", business);
        jdbc.update("DELETE FROM business_memberships WHERE business_id = ?", business);
        jdbc.update("DELETE FROM business_profiles WHERE user_id = ?", business);
        jdbc.update("DELETE FROM businesses WHERE id = ?", business);
        jdbc.update("DELETE FROM app_users WHERE id = ?", actor);
    }
    @Test void realCorrectionsRetainOriginalAndActorWithoutFinancialPostingAndSurviveDraftDeletion() {
        reviews.updateDraft(business, document, actor, correction("25.00"));
        reviews.updateDraft(business, document, actor, correction("35.00"));
        UploadedDocument saved = documents.findById(document).orElseThrow();
        assertEquals(json.readTree(ORIGINAL), json.readTree(saved.getExtractedData()));
        assertEquals("low", json.readTree(saved.getReviewedData()).get("confidence").asText());
        assertEquals("ORIGINAL_OCR", saved.getExtractionProvenance());
        var history = reviews.history(business, document, actor);
        assertEquals(2, history.size()); assertTrue(history.stream().allMatch(h -> h.actorIsCurrentUser()));
        assertEquals(json.readTree(history.get(0).newData()), json.readTree(history.get(1).previousData()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM monthly_records WHERE user_id = ?", Integer.class, business));
        assertThrows(DocumentNotFoundException.class, () -> reviews.history(UUID.randomUUID(), document, actor));
        documents.deleteById(document);
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM document_corrections WHERE document_id = ?", Integer.class, document));
    }
    @Test void runtimeGrantsAndDefenseInDepthTriggersPreventHistoryRewriting() throws Exception {
        reviews.updateDraft(business, document, actor, correction("25.00"));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE document_corrections SET new_data = '{}'::jsonb WHERE document_id = ?", document));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM document_corrections WHERE document_id = ?", document));
        assertThrows(DataAccessException.class, () -> jdbc.execute("TRUNCATE document_corrections"));
        // Even a role with table privileges still reaches the immutable-history trigger.
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE document_corrections SET new_data = '{}'::jsonb WHERE document_id = '" + document + "'"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM document_corrections WHERE document_id = '" + document + "'"));
            assertThrows(SQLException.class, () -> statement.execute("TRUNCATE document_corrections"));
        }
    }
    @Test void databaseRejectsOriginalReplacementProvenanceRelabelAndConfirmedReviewChanges() {
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE uploaded_documents SET extracted_data = '{}'::jsonb WHERE id = ?", document));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE uploaded_documents SET extraction_provenance = 'LEGACY_UNKNOWN' WHERE id = ?", document));
        reviews.updateDraft(business, document, actor, correction("25.00"));
        jdbc.update("UPDATE uploaded_documents SET processing_status = 'confirmed' WHERE id = ?", document);
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE uploaded_documents SET reviewed_data = '{}'::jsonb WHERE id = ?", document));
        assertThrows(IllegalStateException.class, () -> reviews.updateDraft(business, document, actor, correction("35.00")));
    }
}
