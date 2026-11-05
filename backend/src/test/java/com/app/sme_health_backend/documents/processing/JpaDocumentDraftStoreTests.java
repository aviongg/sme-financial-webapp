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

    @Mock
    private com.app.sme_health_backend.documents.storage.DocumentStorageService storageService;

    @Mock private com.app.sme_health_backend.audit.service.SecurityAuditService audit;
    private final UUID businessId = UUID.randomUUID();
    private final java.time.LocalDateTime attempt = java.time.LocalDateTime.of(2026, 10, 1, 12, 0, 0, 123456000);
    private JpaDocumentDraftStore store;
    private final UUID docId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        store = new JpaDocumentDraftStore(repository, storageService, audit,
                new ProcessingAttemptPolicy(java.time.Clock.systemUTC()));
    }

    @Test
    void claimPendingSuccessfullyClaimsAndReturnsOcrRequest() {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(businessId);
        doc.setProcessingStartedAt(attempt);
        doc.setStoragePath("user/2026-09/test.pdf");
        doc.setOriginalFilename("test.pdf");
        doc.setContentType("application/pdf");
        doc.setDocumentTypeHint("invoice");
        doc.setProcessingStatus(DocumentStatus.processing);

        when(repository.claimStatus(eq(docId), eq(DocumentStatus.pending), eq(DocumentStatus.processing), any()))
                .thenReturn(1);
        when(repository.findById(docId)).thenReturn(Optional.of(doc));
        when(storageService.loadBytes("user/2026-09/test.pdf")).thenReturn(new byte[]{1, 2, 3});

        Optional<DocumentDraftStore.Claim> claimed = store.claimPending(docId);

        assertTrue(claimed.isPresent());
        assertEquals(attempt, claimed.get().processingStartedAt(), "Return the persisted token, not a new Java timestamp");
        assertArrayEquals(new byte[]{1, 2, 3}, claimed.get().request().fileBytes());
        assertEquals("test.pdf", claimed.get().request().filename());
        assertEquals("application/pdf", claimed.get().request().contentType());
        assertEquals(OcrExtraction.DocumentType.invoice, claimed.get().request().documentTypeHint());
        verify(repository).claimStatus(eq(docId), eq(DocumentStatus.pending), eq(DocumentStatus.processing), any());
    }

    @Test
    void claimPendingFailsCleanlyWhenStorageFileNotFound() {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(businessId);
        doc.setProcessingStartedAt(attempt);
        doc.setStoragePath("missing.pdf");
        doc.setContentType("application/pdf");
        doc.setDocumentTypeHint("invoice");

        when(repository.claimStatus(eq(docId), eq(DocumentStatus.pending), eq(DocumentStatus.processing), any()))
                .thenReturn(1);
        when(repository.findById(docId)).thenReturn(Optional.of(doc));
        when(storageService.loadBytes("missing.pdf")).thenThrow(new RuntimeException("File not found"));

        Optional<DocumentDraftStore.Claim> claimed = store.claimPending(docId);

        assertTrue(claimed.isEmpty());
        verify(repository).failIfStatus(eq(docId), eq(DocumentStatus.processing), eq(DocumentStatus.failed), eq("file_storage_error"), eq(attempt));
    }

    @Test
    void claimPendingReturnsEmptyWhenDocumentNotPending() {
        when(repository.claimStatus(eq(docId), eq(DocumentStatus.pending), eq(DocumentStatus.processing), any()))
                .thenReturn(0);

        Optional<DocumentDraftStore.Claim> claimed = store.claimPending(docId);

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

        when(repository.saveDraftIfStatus(eq(docId), eq(DocumentStatus.processing), eq(DocumentStatus.extracted), anyString(), eq(attempt)))
                .thenReturn(1);

        boolean saved = store.saveDraftIfProcessing(docId, attempt, extraction, DocumentStatus.extracted);

        assertTrue(saved);
        verify(repository).saveDraftIfStatus(
                eq(docId),
                eq(DocumentStatus.processing),
                eq(DocumentStatus.extracted),
                argThat(json -> json.contains("National Electric") && json.contains("4500.00")),
                eq(attempt)
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
                () -> store.saveDraftIfProcessing(docId, attempt, extraction, DocumentStatus.confirmed));
        assertTrue(ex.getMessage().contains("confirmed"));
    }

    @Test
    void failIfProcessingRecordsFailureReason() {
        when(repository.failIfStatus(docId, DocumentStatus.processing, DocumentStatus.failed, "unavailable", attempt))
                .thenReturn(1);

        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId); doc.setUserId(businessId);
        when(repository.findById(docId)).thenReturn(Optional.of(doc));
        boolean failed = store.failIfProcessing(docId, attempt, OcrClientException.Reason.unavailable);

        assertTrue(failed);
        verify(audit).logSystemFailure(eq(com.app.sme_health_backend.audit.model.AuditEventType.OCR_PROCESSING_FAILED),
                eq(businessId), eq("document"), eq(docId.toString()), eq("unavailable"), anyMap());
        verify(repository).failIfStatus(docId, DocumentStatus.processing, DocumentStatus.failed, "unavailable", attempt);
    }

    @Test
    void failIfProcessingReturnsFalseIfDocumentNoLongerProcessing() {
        when(repository.failIfStatus(docId, DocumentStatus.processing, DocumentStatus.failed, "invalid_response", attempt))
                .thenReturn(0);

        boolean failed = store.failIfProcessing(docId, attempt, OcrClientException.Reason.invalid_response);

        assertFalse(failed);
        verifyNoInteractions(audit);
    }
}
