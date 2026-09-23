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
        service = new AsyncDocumentProcessingService(processor);
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
}
