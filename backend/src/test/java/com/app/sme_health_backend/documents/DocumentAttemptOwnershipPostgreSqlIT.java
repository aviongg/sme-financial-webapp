package com.app.sme_health_backend.documents;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.ocr.*;
import com.app.sme_health_backend.documents.processing.*;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.documents.service.DocumentUploadService;
import com.app.sme_health_backend.documents.storage.DocumentStorageService;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL transactions and runtime role; only external extraction/storage and dispatch are controlled. */
@SpringBootTest
@Import(DocumentAttemptOwnershipPostgreSqlIT.TimeConfiguration.class)
class DocumentAttemptOwnershipPostgreSqlIT extends DisposablePostgres {
    @Autowired UploadedDocumentRepository documents;
    @Autowired JpaDocumentDraftStore store;
    @Autowired DocumentUploadService uploads;
    @Autowired DocumentStartupRecovery recovery;
    @Autowired DocumentProcessingWorker worker;
    @Autowired ProcessingAttemptPolicy policy;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean DocumentStorageService storage;
    @MockitoBean AsyncDocumentProcessingService dispatch;
    @MockitoBean OcrClient client;
    @MockitoSpyBean SecurityAuditService audit;
    private UUID businessId;
    private static final Instant BASE = Instant.parse("2026-10-01T12:00:00.123456789Z");

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary MutableClock attemptTestClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> value = new AtomicReference<>(BASE);
        void set(Instant instant) { value.set(instant); }
        @Override public Instant instant() { return value.get(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
    }

    @BeforeEach void fixture() {
        clock.set(BASE);
        businessId = UUID.randomUUID();
        jdbc.update("INSERT INTO businesses(id,status) VALUES (?,'ACTIVE')", businessId);
        jdbc.update("INSERT INTO business_profiles(user_id,business_type) VALUES (?,'retail')", businessId);
        when(storage.loadBytes(anyString())).thenReturn(new byte[]{1,2,3});
    }

    @Test void claimsUseDatabasePrecisionAndStartupLeavesFreshAndBoundaryClaimsAlone() {
        UUID fresh = pending();
        DocumentDraftStore.Claim claim = store.claimPending(fresh).orElseThrow();
        LocalDateTime persisted = documents.findById(fresh).orElseThrow().getProcessingStartedAt();
        assertEquals(persisted, claim.processingStartedAt());
        assertEquals(0, persisted.getNano() % 1000, "PostgreSQL stores microseconds");
        assertNotEquals(policy.now(), persisted, "The fixture deliberately supplies nanoseconds needing DB rounding");
        clock.set(BASE.with(java.time.temporal.ChronoField.NANO_OF_SECOND, 123457000));
        UUID boundary = processingAt(policy.staleCutoff());
        UUID stale = processingAt(policy.staleCutoff().minusNanos(1000));
        UUID legacy = processingAt(null);
        recovery.recoverStaleProcessingDocuments();
        assertEquals(DocumentStatus.processing, documents.findById(fresh).orElseThrow().getProcessingStatus());
        assertEquals(DocumentStatus.processing, documents.findById(boundary).orElseThrow().getProcessingStatus());
        assertThrows(IllegalStateException.class, () -> uploads.retryProcessing(businessId, boundary));
        for(UUID id : List.of(stale, legacy)) {
            UploadedDocument row = documents.findById(id).orElseThrow();
            assertEquals(DocumentStatus.failed, row.getProcessingStatus());
            assertEquals("processing_interrupted", row.getFailureReason());
            assertNull(row.getProcessingStartedAt());
        }
    }

    @Test void blockedOldWorkerCannotSaveOrFailAfterRetryAndNewClaimCompletes() throws Exception {
        UUID id = pending();
        CountDownLatch extracting = new CountDownLatch(1), release = new CountDownLatch(1);
        when(client.extract(any())).thenAnswer(call -> {
            extracting.countDown();
            if(!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("Old worker was not released");
            return extraction("old");
        });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<Optional<DocumentStatus>> old = pool.submit(() -> worker.process(id));
        try {
            assertTrue(extracting.await(15, TimeUnit.SECONDS));
            LocalDateTime oldToken = documents.findById(id).orElseThrow().getProcessingStartedAt();
            clock.set(BASE.plusSeconds(360));
            uploads.retryProcessing(businessId, id);
            verify(dispatch).processAfterCommit(id);
            DocumentDraftStore.Claim newer = store.claimPending(id).orElseThrow();
            assertNotEquals(oldToken, newer.processingStartedAt());
            assertFalse(store.failIfProcessing(id, oldToken, OcrClientException.Reason.unavailable));
            assertEquals(0, failureCount(id), "A losing worker must not emit a failure audit");
            release.countDown();
            assertTrue(old.get(15, TimeUnit.SECONDS).isEmpty());
            assertEquals(newer.processingStartedAt(), documents.findById(id).orElseThrow().getProcessingStartedAt());
            assertTrue(store.saveDraftIfProcessing(id, newer.processingStartedAt(), extraction("new"), DocumentStatus.extracted));
            UploadedDocument saved = documents.findById(id).orElseThrow();
            assertEquals(DocumentStatus.extracted, saved.getProcessingStatus());
            assertTrue(saved.getExtractedData().contains("new"));
            assertNull(saved.getProcessingStartedAt());
            assertEquals(0, failureCount(id));
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void obsoleteWorkerFailureCannotFailNewerAttemptAndWinningFailureHasSystemBusinessAudit() {
        UUID id = pending();
        DocumentDraftStore.Claim first = store.claimPending(id).orElseThrow();
        clock.set(BASE.plusSeconds(360));
        uploads.retryProcessing(businessId, id);
        DocumentDraftStore.Claim second = store.claimPending(id).orElseThrow();
        assertFalse(store.failIfProcessing(id, first.processingStartedAt(), OcrClientException.Reason.invalid_response));
        assertEquals(0, failureCount(id));
        assertTrue(store.failIfProcessing(id, second.processingStartedAt(), OcrClientException.Reason.unavailable));
        assertFalse(store.failIfProcessing(id, second.processingStartedAt(), OcrClientException.Reason.unavailable));
        assertEquals(1, failureCount(id));
        Map<String,Object> event = jdbc.queryForMap("SELECT actor_user_id,business_id,is_system,outcome,metadata::text FROM security_audit_events WHERE event_type='OCR_PROCESSING_FAILED' AND target_id=?", id.toString());
        assertNull(event.get("actor_user_id")); assertEquals(businessId,event.get("business_id"));
        assertEquals(true,event.get("is_system")); assertEquals("FAILURE",event.get("outcome"));
        assertTrue(event.get("metadata").toString().contains(second.processingStartedAt().toString()));
        assertNull(documents.findById(id).orElseThrow().getProcessingStartedAt());
    }

    @Test void humanReviewAndConfirmedStatusCannotBeOverwrittenEvenWithTheCorrectOldToken() {
        for (DocumentStatus status : List.of(DocumentStatus.processing, DocumentStatus.needs_review, DocumentStatus.confirmed)) {
            UUID id = pending();
            var claim = store.claimPending(id).orElseThrow();
            jdbc.update("UPDATE uploaded_documents SET processing_status=?, reviewed_data=CAST(? AS jsonb) WHERE id=?",
                    status.name(), "{\"amount\":99}", id);
            assertFalse(store.saveDraftIfProcessing(id,claim.processingStartedAt(),extraction("old"),DocumentStatus.extracted));
            assertFalse(store.failIfProcessing(id,claim.processingStartedAt(),OcrClientException.Reason.unavailable));
            UploadedDocument row=documents.findById(id).orElseThrow();
            assertEquals(status,row.getProcessingStatus()); assertTrue(row.getReviewedData().contains("99"));
            assertNull(row.getExtractedData()); assertEquals(0,failureCount(id));
        }
    }

    @Test void auditFailureRollsBackTheWinningFailureTransition() {
        UUID id=pending(); var claim=store.claimPending(id).orElseThrow();
        doThrow(new IllegalStateException("audit persistence unavailable")).when(audit).logSystemFailure(
                eq(AuditEventType.OCR_PROCESSING_FAILED),eq(businessId),eq("document"),eq(id.toString()),anyString(),anyMap());
        assertThrows(IllegalStateException.class,()->store.failIfProcessing(id,claim.processingStartedAt(),OcrClientException.Reason.unavailable));
        UploadedDocument row=documents.findById(id).orElseThrow();
        assertEquals(DocumentStatus.processing,row.getProcessingStatus());
        assertEquals(claim.processingStartedAt(),row.getProcessingStartedAt());
        assertEquals(0,failureCount(id));
    }

    @Test void unexpectedClientExceptionProducesOnePersistedFailureAndOneSystemEvent() {
        UUID id=pending();
        when(client.extract(any())).thenThrow(new IllegalStateException("unexpected client error"));
        assertEquals(Optional.of(DocumentStatus.failed),worker.process(id));
        assertEquals(DocumentStatus.failed,documents.findById(id).orElseThrow().getProcessingStatus());
        assertEquals(1,failureCount(id));
        assertEquals(businessId,jdbc.queryForObject("SELECT business_id FROM security_audit_events WHERE event_type='OCR_PROCESSING_FAILED' AND target_id=?",UUID.class,id.toString()));
    }

    private UUID pending() {
        UploadedDocument d=new UploadedDocument(); d.setId(UUID.randomUUID()); d.setUserId(businessId);
        d.setFileUrl("/api/documents/"+d.getId()+"/file"); d.setStoragePath("controlled-"+d.getId());
        d.setContentType("image/png"); d.setOriginalFilename("fixture.png"); d.setProcessingStatus(DocumentStatus.pending);
        return documents.saveAndFlush(d).getId();
    }
    private UUID processingAt(LocalDateTime started) {
        UUID id=pending(); jdbc.update("UPDATE uploaded_documents SET processing_status='processing',processing_started_at=? WHERE id=?",started,id); return id;
    }
    private int failureCount(UUID id) { return jdbc.queryForObject("SELECT count(*) FROM security_audit_events WHERE event_type='OCR_PROCESSING_FAILED' AND target_id=?",Integer.class,id.toString()); }
    private OcrExtraction extraction(String party) { return new OcrExtraction(LocalDate.of(2026,10,1),BigDecimal.TEN,party,OcrExtraction.Category.purchase,OcrExtraction.Confidence.high,OcrExtraction.DocumentType.invoice); }
}
