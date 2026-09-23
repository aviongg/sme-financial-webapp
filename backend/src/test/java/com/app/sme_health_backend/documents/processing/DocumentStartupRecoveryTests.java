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

    @InjectMocks
    private DocumentStartupRecovery recovery;

    @Test
    void recoverStaleProcessingDocumentsResetsInterruptedDocumentsToFailed() {
        when(repository.resetAllByStatus(DocumentStatus.processing, DocumentStatus.failed, "processing_interrupted"))
                .thenReturn(3);

        recovery.recoverStaleProcessingDocuments();

        verify(repository).resetAllByStatus(
                DocumentStatus.processing,
                DocumentStatus.failed,
                "processing_interrupted"
        );
    }
}
