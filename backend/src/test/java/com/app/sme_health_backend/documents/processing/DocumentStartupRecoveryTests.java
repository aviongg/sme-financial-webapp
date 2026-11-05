package com.app.sme_health_backend.documents.processing;

import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentStartupRecoveryTests {

    @Mock
    private UploadedDocumentRepository repository;

    private final ProcessingAttemptPolicy policy = new ProcessingAttemptPolicy(java.time.Clock.fixed(
            java.time.Instant.parse("2026-10-01T12:00:00Z"), java.time.ZoneOffset.UTC));

    @Test
    void recoverStaleProcessingDocumentsResetsInterruptedDocumentsToFailed() {
        when(repository.recoverStaleProcessing(DocumentStatus.processing, DocumentStatus.failed, "processing_interrupted", policy.staleCutoff()))
                .thenReturn(3);

        new DocumentStartupRecovery(repository, policy).recoverStaleProcessingDocuments();

        verify(repository).recoverStaleProcessing(
                DocumentStatus.processing,
                DocumentStatus.failed,
                "processing_interrupted",
                policy.staleCutoff()
        );
    }
}
