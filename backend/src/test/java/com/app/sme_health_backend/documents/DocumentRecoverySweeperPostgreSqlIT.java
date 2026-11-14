package com.app.sme_health_backend.documents;

import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.processing.AsyncDocumentProcessingService;
import com.app.sme_health_backend.documents.processing.DocumentRecoverySweeper;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.processing.ProcessingAttemptPolicy;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression (production-closure audit P2-05): recovery ran only once at startup with a five-minute cutoff, so a
 * document interrupted shortly before a restart stayed "processing", and a "pending" upload whose post-commit
 * dispatch was lost was never processed. The sweeper fails stale attempts and re-dispatches lost pending uploads;
 * fresh rows are left alone.
 */
@SpringBootTest(properties = "app.whatsapp.provider=mock")
class DocumentRecoverySweeperPostgreSqlIT extends DisposablePostgres {

    @Autowired private UploadedDocumentRepository documents;
    @Autowired private BusinessRepository businesses;
    @Autowired private ProcessingAttemptPolicy policy;
    @Autowired private AsyncDocumentProcessingService dispatcher;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    private UUID document(UUID businessId, DocumentStatus status, LocalDateTime uploaded, LocalDateTime started) {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(UUID.randomUUID());
        doc.setUserId(businessId);
        doc.setFileUrl("synthetic-recovery-file");
        doc.setProcessingStatus(status);
        UUID id = documents.saveAndFlush(doc).getId();
        jdbc.update("UPDATE uploaded_documents SET upload_timestamp = ?, processing_started_at = ? WHERE id = ?",
                Timestamp.valueOf(uploaded), started == null ? null : Timestamp.valueOf(started), id);
        return id;
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT processing_status FROM uploaded_documents WHERE id = ?", String.class, id);
    }

    @Test
    void failsStaleAttemptsAndRedispatchesLostPendingUploadsOnly() throws Exception {
        UUID businessId = UUID.randomUUID();
        businesses.saveAndFlush(new Business(businessId));
        jdbc.update("INSERT INTO business_profiles(user_id, business_type) VALUES (?, 'retail')", businessId);

        LocalDateTime now = policy.now();
        UUID staleProcessing = document(businessId, DocumentStatus.processing, now.minusMinutes(20), now.minusMinutes(6));
        UUID freshProcessing = document(businessId, DocumentStatus.processing, now.minusMinutes(20), now.minusMinutes(1));
        UUID lostPending = document(businessId, DocumentStatus.pending, now.minusMinutes(10), null);
        UUID freshPending = document(businessId, DocumentStatus.pending, now.minusSeconds(5), null);

        DocumentRecoverySweeper sweeper = new DocumentRecoverySweeper(documents, policy, dispatcher, transactionManager, 120);
        DocumentRecoverySweeper.SweepResult result = sweeper.sweep();

        assertThat(status(staleProcessing)).isEqualTo("failed");
        assertThat(jdbc.queryForObject("SELECT failure_reason FROM uploaded_documents WHERE id = ?", String.class, staleProcessing))
                .isEqualTo("processing_interrupted");
        assertThat(status(freshProcessing)).isEqualTo("processing");
        assertThat(result.redispatched()).contains(lostPending).doesNotContain(freshPending);

        // The re-dispatched upload is claimed by the worker (no OCR provider in tests, so it ends failed, not pending).
        long deadline = System.currentTimeMillis() + 15_000;
        while ("pending".equals(status(lostPending)) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(status(lostPending)).isNotEqualTo("pending");
        assertThat(status(freshPending)).isEqualTo("pending");
    }
}
