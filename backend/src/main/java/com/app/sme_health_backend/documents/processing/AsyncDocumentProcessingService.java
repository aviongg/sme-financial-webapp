package com.app.sme_health_backend.documents.processing;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class AsyncDocumentProcessingService {
    private final DocumentProcessingWorker worker;
    private final Executor executor;

    public AsyncDocumentProcessingService(DocumentProcessingWorker worker,
            @Qualifier("documentProcessingExecutor") Executor executor) {
        this.worker = Objects.requireNonNull(worker);
        this.executor = Objects.requireNonNull(executor);
    }

    /** Dispatch only after commit; rollback never enqueues an OCR operation. */
    public void processAfterCommit(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId is required");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { dispatchAsync(documentId); }
            });
        } else dispatchAsync(documentId);
    }

    public CompletableFuture<Optional<DocumentStatus>> dispatchAsync(UUID documentId) {
        // Explicit executor submission avoids self-invocation bypassing an @Async proxy.
        return CompletableFuture.supplyAsync(() -> worker.process(documentId), executor);
    }
}
