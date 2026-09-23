package com.app.sme_health_backend.documents.processing;

import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.ocr.OcrClientException;
import com.app.sme_health_backend.documents.ocr.OcrExtraction;
import com.app.sme_health_backend.documents.ocr.OcrRequest;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaDocumentDraftStoreTests {

    @Mock
    private UploadedDocumentRepository repository;

    private JpaDocumentDraftStore store;
    private final UUID docId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        store = new JpaDocumentDraftStore(repository);
    }

    @Test
    void claimPendingSuccessfullyClaimsAndReturnsOcrRequest() {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setFileUrl("http://localhost:8080/api/documents/" + docId + "/file");
        doc.setDocumentTypeHint("invoice");
        doc.setProcessingStatus(DocumentStatus.processing);

        when(repository.claimStatus(eq(docId), eq(DocumentStatus.pending), eq(DocumentStatus.processing), any()))
                .thenReturn(1);
        when(repository.findById(docId)).thenReturn(Optional.of(doc));

        Optional<OcrRequest> claimed = store.claimPending(docId);

        assertTrue(claimed.isPresent());
        assertEquals("http://localhost:8080/api/documents/" + docId + "/file", claimed.get().imageUrl());
        assertEquals(OcrExtraction.DocumentType.invoice, claimed.get().documentTypeHint());
        verify(repository).claimStatus(eq(docId), eq(DocumentStatus.pending), eq(DocumentStatus.processing), any());
    }

    @Test
    void claimPendingReturnsEmptyWhenDocumentNotPending() {
        when(repository.claimStatus(eq(docId), eq(DocumentStatus.pending), eq(DocumentStatus.processing), any()))
                .thenReturn(0);

        Optional<OcrRequest> claimed = store.claimPending(docId);

        assertTrue(claimed.isEmpty());
        verify(repository, never()).findById(any());
    }

    @Test
    void saveDraftIfProcessingPersistsExtractedDataAndTransitionsStatus() {
        OcrExtraction extraction = new OcrExtraction(
                LocalDate.of(2026, 9, 15),
                new BigDecimal("4500.00"),
                "National Electric",
                OcrExtraction.Category.expense,
                OcrExtraction.Confidence.high,
                OcrExtraction.DocumentType.receipt
        );

        when(repository.saveDraftIfStatus(eq(docId), eq(DocumentStatus.processing), eq(DocumentStatus.extracted), anyString()))
                .thenReturn(1);

        boolean saved = store.saveDraftIfProcessing(docId, extraction, DocumentStatus.extracted);

        assertTrue(saved);
        verify(repository).saveDraftIfStatus(
                eq(docId),
                eq(DocumentStatus.processing),
                eq(DocumentStatus.extracted),
                argThat(json -> json.contains("National Electric") && json.contains("4500.00"))
        );
    }

    @Test
    void saveDraftIfProcessingRejectsConfirmedTargetStatus() {
        OcrExtraction extraction = new OcrExtraction(
                LocalDate.of(2026, 9, 15),
                new BigDecimal("4500.00"),
                "National Electric",
                OcrExtraction.Category.expense,
                OcrExtraction.Confidence.high,
                OcrExtraction.DocumentType.receipt
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> store.saveDraftIfProcessing(docId, extraction, DocumentStatus.confirmed));
        assertTrue(ex.getMessage().contains("confirmed"));
    }

    @Test
    void failIfProcessingRecordsFailureReason() {
        when(repository.failIfStatus(docId, DocumentStatus.processing, DocumentStatus.failed, "unavailable"))
                .thenReturn(1);

        boolean failed = store.failIfProcessing(docId, OcrClientException.Reason.unavailable);

        assertTrue(failed);
        verify(repository).failIfStatus(docId, DocumentStatus.processing, DocumentStatus.failed, "unavailable");
    }

    @Test
    void failIfProcessingReturnsFalseIfDocumentNoLongerProcessing() {
        when(repository.failIfStatus(docId, DocumentStatus.processing, DocumentStatus.failed, "invalid_response"))
                .thenReturn(0);

        boolean failed = store.failIfProcessing(docId, OcrClientException.Reason.invalid_response);

        assertFalse(failed);
    }
}
