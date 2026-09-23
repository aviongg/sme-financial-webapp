package com.app.sme_health_backend.documents.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AsyncDocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(AsyncDocumentProcessingService.class);

    private final DocumentDraftProcessor processor;

    public AsyncDocumentProcessingService(DocumentDraftProcessor processor) {
        this.processor = Objects.requireNonNull(processor, "processor is required");
    }

    /**
     * Ensures document processing is dispatched strictly AFTER the current database transaction commits.
     * If no transaction is active, dispatches immediately.
     */
    public void processAfterCommit(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId is required");

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.debug("Transaction committed; dispatching OCR processing for document {}", documentId);
                    dispatchAsync(documentId);
                }
            });
        } else {
            log.debug("No active transaction; dispatching OCR processing directly for document {}", documentId);
            dispatchAsync(documentId);
        }
    }

    @Async("documentProcessingExecutor")
    public CompletableFuture<Optional<DocumentStatus>> dispatchAsync(UUID documentId) {
        try {
            log.info("Starting background OCR extraction for document {}", documentId);
            Optional<DocumentStatus> result = processor.process(documentId);
            log.info("Completed background OCR extraction for document {}: result={}", documentId, result.orElse(null));
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Unexpected error during background OCR extraction for document {}: {}", documentId, e.getMessage(), e);
            return CompletableFuture.completedFuture(Optional.of(DocumentStatus.failed));
        }
    }
}
