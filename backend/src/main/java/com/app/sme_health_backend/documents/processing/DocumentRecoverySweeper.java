package com.app.sme_health_backend.documents.processing;

import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Periodic OCR recovery while the application runs (startup recovery alone ran once with a five-minute cutoff).
 *
 * <ul>
 *   <li>{@code processing} rows whose attempt is older than {@link ProcessingAttemptPolicy#STALE_TIMEOUT} are failed
 *       with {@code processing_interrupted} (the same transition as {@link DocumentStartupRecovery}), so a document
 *       interrupted shortly before a restart no longer stays "processing" until a user notices.</li>
 *   <li>{@code pending} rows older than the grace period are re-dispatched: their post-commit dispatch was lost (crash
 *       between commit and submission, or in-memory queue lost on shutdown). Re-dispatch is idempotent: the worker
 *       claims with an atomic pending→processing compare-and-set and writes results conditionally on that claim.</li>
 * </ul>
 * Enabled in the production profile ({@code app.documents.recovery.enabled}); off by default so tests and local runs
 * keep deterministic document states.
 */
@Component
@ConditionalOnProperty(name = "app.documents.recovery.enabled", havingValue = "true")
public class DocumentRecoverySweeper {

    private static final Logger log = LoggerFactory.getLogger(DocumentRecoverySweeper.class);
    static final int BATCH = 20;

    private final UploadedDocumentRepository repository;
    private final ProcessingAttemptPolicy policy;
    private final AsyncDocumentProcessingService dispatcher;
    private final TransactionTemplate tx;
    private final Duration pendingGrace;

    public DocumentRecoverySweeper(UploadedDocumentRepository repository, ProcessingAttemptPolicy policy,
                                   AsyncDocumentProcessingService dispatcher,
                                   org.springframework.transaction.PlatformTransactionManager transactionManager,
                                   @Value("${app.documents.recovery.pending-grace-seconds:120}") long pendingGraceSeconds) {
        this.repository = repository;
        this.policy = policy;
        this.dispatcher = dispatcher;
        this.tx = new TransactionTemplate(transactionManager);
        this.pendingGrace = Duration.ofSeconds(Math.max(30, pendingGraceSeconds));
    }

    public record SweepResult(int failedStale, List<UUID> redispatched) { }

    @Scheduled(fixedDelayString = "${app.documents.recovery.interval-ms:120000}",
            initialDelayString = "${app.documents.recovery.interval-ms:120000}")
    public void scheduledSweep() {
        try {
            sweep();
        } catch (RuntimeException e) {
            log.error("Document recovery sweep failed; type={}", e.getClass().getSimpleName());
        }
    }

    public SweepResult sweep() {
        Integer failed = tx.execute(status -> repository.recoverStaleProcessing(
                DocumentStatus.processing, DocumentStatus.failed, "processing_interrupted", policy.staleCutoff()));
        List<UUID> pending = repository.findIdsByStatusUploadedBefore(DocumentStatus.pending,
                policy.now().minus(pendingGrace), PageRequest.of(0, BATCH));
        for (UUID id : pending) {
            dispatcher.dispatchAsync(id);
        }
        int failedCount = failed == null ? 0 : failed;
        if (failedCount > 0 || !pending.isEmpty()) {
            log.warn("Document recovery: failed {} interrupted attempt(s), re-dispatched {} pending document(s)",
                    failedCount, pending.size());
        }
        return new SweepResult(failedCount, List.copyOf(pending));
    }
}
