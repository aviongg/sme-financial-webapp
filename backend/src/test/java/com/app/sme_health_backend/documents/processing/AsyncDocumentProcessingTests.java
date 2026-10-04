package com.app.sme_health_backend.documents.processing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncDocumentProcessingTests {

    @Mock
    private DocumentDraftProcessor processor;

    private AsyncDocumentProcessingService service;
    private final UUID documentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AsyncDocumentProcessingService(new DocumentProcessingWorker(processor, null), Runnable::run);
    }

    @Test
    void dispatchAsyncInvokesProcessorAndReturnsStatus() throws Exception {
        when(processor.process(documentId)).thenReturn(Optional.of(DocumentStatus.extracted));

        CompletableFuture<Optional<DocumentStatus>> future = service.dispatchAsync(documentId);
        Optional<DocumentStatus> result = future.get();

        assertTrue(result.isPresent());
        assertEquals(DocumentStatus.extracted, result.get());
        verify(processor).process(documentId);
    }

    @Test
    void dispatchAsyncHandlesExceptionGracefully() throws Exception {
        when(processor.process(documentId)).thenThrow(new RuntimeException("Crash during extraction"));

        CompletableFuture<Optional<DocumentStatus>> future = service.dispatchAsync(documentId);
        Optional<DocumentStatus> result = future.get();

        assertTrue(result.isPresent());
        assertEquals(DocumentStatus.failed, result.get());
    }

    @Test
    void processAfterCommitWithoutActiveTransactionDispatchesDirectly() {
        when(processor.process(documentId)).thenReturn(Optional.of(DocumentStatus.needs_review));

        service.processAfterCommit(documentId);

        verify(processor).process(documentId);
    }

    @Test
    void committedCallbackSubmitsToExecutorInsteadOfInvokingProcessorInline() {
        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        service = new AsyncDocumentProcessingService(new DocumentProcessingWorker(processor, null), queued::add);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.processAfterCommit(documentId);
            assertTrue(queued.isEmpty());
            verifyNoInteractions(processor);
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
            assertEquals(1, queued.size());
            verifyNoInteractions(processor);
            when(processor.process(documentId)).thenReturn(Optional.of(DocumentStatus.needs_review));
            queued.get(0).run();
            verify(processor).process(documentId);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void rolledBackTransactionNeverSubmitsProcessing() {
        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        service = new AsyncDocumentProcessingService(new DocumentProcessingWorker(processor, null), queued::add);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.processAfterCommit(documentId);
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().get(0)
                    .afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
            assertTrue(queued.isEmpty()); verifyNoInteractions(processor);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
