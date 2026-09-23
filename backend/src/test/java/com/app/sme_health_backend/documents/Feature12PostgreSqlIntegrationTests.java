package com.app.sme_health_backend.documents;

import com.app.sme_health_backend.documents.dto.DocumentConfirmationRequest;
import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.exception.DocumentAlreadyConfirmedException;
import com.app.sme_health_backend.documents.processing.DocumentStartupRecovery;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.documents.service.DocumentConfirmationService;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.repository.ScoreResultRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class Feature12PostgreSqlIntegrationTests {

    @Autowired
    private UploadedDocumentRepository documentRepository;

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    @Autowired
    private MonthlyRecordRepository monthlyRecordRepository;

    @Autowired
    private ScoreResultRepository scoreResultRepository;

    @Autowired
    private DocumentConfirmationService confirmationService;

    @Autowired
    private DocumentStartupRecovery startupRecovery;

    private UUID testUserId;
    private final List<UUID> createdDocIds = new ArrayList<>();
    private final List<UUID> createdRecordIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(testUserId);
        profile.setBusinessType("retail");
        profile.setLanguagePreference("en");
        profile.setWhatsappOptIn(false);
        profile.setCreatedAt(LocalDateTime.now());
        businessProfileRepository.save(profile);
    }

    @AfterEach
    void tearDown() {
        for (UUID docId : createdDocIds) {
            try {
                documentRepository.deleteById(docId);
            } catch (Exception ignored) {}
        }
        for (UUID recId : createdRecordIds) {
            try {
                monthlyRecordRepository.deleteById(recId);
            } catch (Exception ignored) {}
        }
        try {
            List<ScoreResult> scores = scoreResultRepository.findByUserIdOrderByMonthDesc(testUserId);
            scoreResultRepository.deleteAll(scores);
        } catch (Exception ignored) {}
        try {
            businessProfileRepository.deleteById(testUserId);
        } catch (Exception ignored) {}
    }

    @Test
    void verifyV7MetadataAndJsonbPersistenceSatisfiesV1Constraints() {
        UploadedDocument doc = new UploadedDocument();
        UUID docId = UUID.randomUUID();
        createdDocIds.add(docId);

        doc.setId(docId);
        doc.setUserId(testUserId);
        doc.setFileUrl("http://localhost:8080/api/documents/" + docId + "/file");
        doc.setDocumentTypeHint("receipt");
        doc.setProcessingStatus(DocumentStatus.pending);
        doc.setOriginalFilename("march_receipt.jpg");
        doc.setContentType("image/jpeg");
        doc.setFileSizeBytes(1048576L);
        doc.setStoragePath("/var/storage/documents/" + testUserId + "/" + docId + ".jpg");
        doc.setProcessingStartedAt(LocalDateTime.now());
        doc.setExtractedData("{\"date\":\"2026-03-15\",\"amount\":15000.00,\"vendor_or_party\":\"Supplier Co\",\"category\":\"cogs\",\"confidence\":\"high\",\"document_type_detected\":\"receipt\"}");

        UploadedDocument saved = documentRepository.save(doc);

        Optional<UploadedDocument> retrieved = documentRepository.findById(saved.getId());
        assertTrue(retrieved.isPresent());
        UploadedDocument found = retrieved.get();

        assertNotNull(found.getFileUrl(), "file_url must satisfy NOT NULL constraint");
        assertEquals("march_receipt.jpg", found.getOriginalFilename());
        assertEquals("image/jpeg", found.getContentType());
        assertEquals(1048576L, found.getFileSizeBytes());
        assertEquals("/var/storage/documents/" + testUserId + "/" + docId + ".jpg", found.getStoragePath());
        assertNotNull(found.getProcessingStartedAt());
        assertNotNull(found.getExtractedData());
        assertTrue(found.getExtractedData().contains("Supplier Co"));
        assertEquals(DocumentStatus.pending, found.getProcessingStatus());
    }

    @Test
    @Transactional
    void verifyAtomicStatusTransitionsAgainstPostgreSql() {
        UploadedDocument doc = new UploadedDocument();
        UUID docId = UUID.randomUUID();
        createdDocIds.add(docId);

        doc.setId(docId);
        doc.setUserId(testUserId);
        doc.setFileUrl("http://localhost:8080/api/documents/" + docId + "/file");
        doc.setProcessingStatus(DocumentStatus.pending);
        doc.setDocumentTypeHint("receipt");
        doc.setOriginalFilename("test.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSizeBytes(2048L);
        doc.setStoragePath("/storage/" + docId + ".pdf");
        documentRepository.save(doc);

        // 1. Atomic claim: pending -> processing succeeds exactly once
        int firstClaim = documentRepository.claimStatus(docId, DocumentStatus.pending, DocumentStatus.processing, LocalDateTime.now());
        assertEquals(1, firstClaim, "First claim must transition pending -> processing");

        int secondClaim = documentRepository.claimStatus(docId, DocumentStatus.pending, DocumentStatus.processing, LocalDateTime.now());
        assertEquals(0, secondClaim, "Second claim on already processing document must return 0");

        // 2. Conditional transition: processing -> extracted succeeds
        String json = "{\"amount\":5000}";
        int saveDraft = documentRepository.saveDraftIfStatus(docId, DocumentStatus.processing, DocumentStatus.extracted, json);
        assertEquals(1, saveDraft, "Must transition processing -> extracted");

        // 3. Conditional transition from wrong state fails
        int invalidSave = documentRepository.saveDraftIfStatus(docId, DocumentStatus.processing, DocumentStatus.extracted, json);
        assertEquals(0, invalidSave, "Transition from processing -> extracted must fail when status is already extracted");

        // 4. failIfStatus conditional predicate
        int invalidFail = documentRepository.failIfStatus(docId, DocumentStatus.processing, DocumentStatus.failed, "timeout");
        assertEquals(0, invalidFail, "failIfStatus must return 0 when document is not in processing status");
    }

    @Test
    void verifyStartupRecoveryResetsInterruptedProcessingToFailed() {
        UploadedDocument doc = new UploadedDocument();
        UUID docId = UUID.randomUUID();
        createdDocIds.add(docId);

        doc.setId(docId);
        doc.setUserId(testUserId);
        doc.setFileUrl("http://localhost:8080/api/documents/" + docId + "/file");
        doc.setProcessingStatus(DocumentStatus.processing);
        doc.setProcessingStartedAt(LocalDateTime.now().minusMinutes(10));
        doc.setOriginalFilename("interrupted.png");
        doc.setContentType("image/png");
        doc.setFileSizeBytes(5000L);
        doc.setStoragePath("/storage/interrupted.png");
        documentRepository.save(doc);

        startupRecovery.recoverStaleProcessingDocuments();

        UploadedDocument recovered = documentRepository.findById(docId).orElseThrow();
        assertEquals(DocumentStatus.failed, recovered.getProcessingStatus());
        assertEquals("processing_interrupted", recovered.getFailureReason());
    }

    @Test
    void verifyConfirmationAppliesContributionRescoresAndEnforcesIdempotency() {
        String month = "2026-03";

        // Create base monthly record for scoring
        MonthlyRecord baseRecord = new MonthlyRecord();
        baseRecord.setUserId(testUserId);
        baseRecord.setMonth(month);
        baseRecord.setRevenue(new BigDecimal("100000.00"));
        baseRecord.setOperatingExpenses(new BigDecimal("40000.00"));
        baseRecord.setCogs(new BigDecimal("20000.00"));
        baseRecord.setCashInflow(new BigDecimal("100000.00"));
        baseRecord.setCashOutflow(new BigDecimal("60000.00"));
        baseRecord.setCashBalanceEom(new BigDecimal("40000.00"));
        baseRecord.setFinancingType("none");
        baseRecord.setUpdatedAt(LocalDateTime.now());
        MonthlyRecord savedRecord = monthlyRecordRepository.save(baseRecord);
        createdRecordIds.add(savedRecord.getId());

        // Create extracted document ready for confirmation
        UploadedDocument doc = new UploadedDocument();
        UUID docId = UUID.randomUUID();
        createdDocIds.add(docId);

        doc.setId(docId);
        doc.setUserId(testUserId);
        doc.setFileUrl("http://localhost:8080/api/documents/" + docId + "/file");
        doc.setDocumentTypeHint("invoice");
        doc.setProcessingStatus(DocumentStatus.extracted);
        doc.setOriginalFilename("revenue_invoice.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSizeBytes(12000L);
        doc.setStoragePath("/storage/" + docId + ".pdf");
        doc.setExtractedData("{\"date\":\"2026-03-20\",\"amount\":15000.00,\"vendor_or_party\":\"Client Alpha\",\"category\":\"revenue\",\"confidence\":\"high\",\"document_type_detected\":\"invoice\"}");
        documentRepository.save(doc);

        DocumentConfirmationRequest confirmReq = new DocumentConfirmationRequest(
                month,
                new BigDecimal("15000.00"),
                java.time.LocalDate.of(2026, 3, 20),
                "Client Alpha",
                "revenue",
                "cash_inflow",
                null
        );

        // 1. First confirmation
        UploadedDocument confirmedDoc = confirmationService.confirmDocument(testUserId, docId, confirmReq);
        assertEquals(DocumentStatus.confirmed, confirmedDoc.getProcessingStatus());
        assertEquals(month, confirmedDoc.getLinkedMonth());
        assertNotNull(confirmedDoc.getConfirmedAt());
        assertNotNull(confirmedDoc.getConfirmedData());
        assertTrue(confirmedDoc.getConfirmedData().contains("15000.00"));

        // Verify monthly record updated by contribution
        MonthlyRecord updatedRecord = monthlyRecordRepository.findByUserIdAndMonth(testUserId, month).orElseThrow();
        assertEquals(0, new BigDecimal("115000.00").compareTo(updatedRecord.getRevenue()), "Revenue should increase from 100000 to 115000");
        assertEquals(0, new BigDecimal("115000.00").compareTo(updatedRecord.getCashInflow()), "Cash inflow should increase from 100000 to 115000");

        // Verify affected ScoreResult was recalculated
        Optional<ScoreResult> score = scoreResultRepository.findByUserIdAndMonth(testUserId, month);
        assertTrue(score.isPresent(), "ScoreResult should be recalculated and saved for affected month");

        // 2. Duplicate confirmation attempt must be rejected idempotently
        assertThrows(DocumentAlreadyConfirmedException.class, () ->
                confirmationService.confirmDocument(testUserId, docId, confirmReq)
        );

        // Verify monthly record was NOT double-posted
        MonthlyRecord postCheckRecord = monthlyRecordRepository.findByUserIdAndMonth(testUserId, month).orElseThrow();
        assertEquals(0, new BigDecimal("115000.00").compareTo(postCheckRecord.getRevenue()), "Revenue must NOT double post on duplicate confirm");
        assertEquals(0, new BigDecimal("115000.00").compareTo(postCheckRecord.getCashInflow()), "Cash inflow must NOT double post on duplicate confirm");
    }
}
