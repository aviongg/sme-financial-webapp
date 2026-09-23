package com.app.sme_health_backend.documents.service;

import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.exception.DocumentNotFoundException;
import com.app.sme_health_backend.documents.exception.DocumentValidationException;
import com.app.sme_health_backend.documents.processing.AsyncDocumentProcessingService;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.documents.storage.DocumentStorageService;
import com.app.sme_health_backend.documents.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentUploadServiceTests {

    @Mock
    private UploadedDocumentRepository repository;

    @Mock
    private DocumentStorageService storageService;

    @Mock
    private AsyncDocumentProcessingService asyncProcessingService;

    private DocumentUploadService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID docId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DocumentUploadService(repository, storageService, asyncProcessingService);
    }

    @Test
    void uploadSingleSuccessfullyStoresFileAndTriggersAsyncProcessing() {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[]{1, 2, 3});
        StoredFile stored = new StoredFile("/path/to/receipt.png", "receipt.png", "image/png", 3L);

        when(storageService.store(userId, file)).thenReturn(stored);
        when(storageService.resolveFileUrl(any())).thenReturn("http://localhost:8080/api/documents/" + docId + "/file");
        when(repository.save(any(UploadedDocument.class))).thenAnswer(invocation -> {
            UploadedDocument doc = invocation.getArgument(0);
            return doc;
        });

        UploadedDocument created = service.uploadSingle(userId, file, "receipt");

        assertNotNull(created);
        assertEquals(userId, created.getUserId());
        assertEquals(DocumentStatus.pending, created.getProcessingStatus());
        assertEquals("receipt", created.getDocumentTypeHint());
        assertEquals("receipt.png", created.getOriginalFilename());
        verify(storageService).store(userId, file);
        verify(repository).save(any(UploadedDocument.class));
        verify(asyncProcessingService).processAfterCommit(any());
    }

    @Test
    void uploadBulkStoresMultipleFilesAndReturnsList() {
        MockMultipartFile file1 = new MockMultipartFile("files", "doc1.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile file2 = new MockMultipartFile("files", "doc2.jpg", "image/jpeg", new byte[]{2});
        StoredFile stored1 = new StoredFile("/path/to/1.jpg", "doc1.jpg", "image/jpeg", 1L);
        StoredFile stored2 = new StoredFile("/path/to/2.jpg", "doc2.jpg", "image/jpeg", 1L);

        when(storageService.store(userId, file1)).thenReturn(stored1);
        when(storageService.store(userId, file2)).thenReturn(stored2);
        when(repository.save(any(UploadedDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        List<UploadedDocument> results = service.uploadBulk(userId, List.of(file1, file2), "invoice");

        assertEquals(2, results.size());
        verify(storageService, times(2)).store(eq(userId), any());
        verify(asyncProcessingService, times(2)).processAfterCommit(any());
    }

    @Test
    void uploadBulkRejectsMoreThanTenFiles() {
        List<org.springframework.web.multipart.MultipartFile> elevenFiles = java.util.Collections.nCopies(
                11, new MockMultipartFile("files", "test.png", "image/png", new byte[]{1})
        );

        DocumentValidationException ex = assertThrows(DocumentValidationException.class,
                () -> service.uploadBulk(userId, elevenFiles, "receipt"));
        assertTrue(ex.getMessage().contains("limit exceeded"));
    }

    @Test
    void getDocumentEnforcesUserOwnership() {
        UUID wrongUser = UUID.randomUUID();
        when(repository.findByIdAndUserId(docId, wrongUser)).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> service.getDocument(wrongUser, docId));
    }

    @Test
    void retryProcessingResetsPendingStatusAndClearsFailureReason() {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setProcessingStatus(DocumentStatus.failed);
        doc.setFailureReason("timeout");

        when(repository.findByIdAndUserId(docId, userId)).thenReturn(Optional.of(doc));
        when(repository.save(doc)).thenReturn(doc);

        UploadedDocument retried = service.retryProcessing(userId, docId);

        assertEquals(DocumentStatus.pending, retried.getProcessingStatus());
        assertNull(retried.getFailureReason());
        verify(asyncProcessingService).processAfterCommit(docId);
    }

    @Test
    void retryProcessingRejectsConfirmedDocument() {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setProcessingStatus(DocumentStatus.confirmed);

        when(repository.findByIdAndUserId(docId, userId)).thenReturn(Optional.of(doc));

        assertThrows(IllegalStateException.class, () -> service.retryProcessing(userId, docId));
    }

    @Test
    void deleteDraftDeletesPhysicalFileAndRepositoryRow() {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setStoragePath("/storage/test.png");
        doc.setProcessingStatus(DocumentStatus.needs_review);

        when(repository.findByIdAndUserId(docId, userId)).thenReturn(Optional.of(doc));

        service.deleteDraft(userId, docId);

        verify(storageService).delete("/storage/test.png");
        verify(repository).delete(doc);
    }

    @Test
    void deleteDraftRefusesDeletionOfConfirmedDocument() {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setProcessingStatus(DocumentStatus.confirmed);

        when(repository.findByIdAndUserId(docId, userId)).thenReturn(Optional.of(doc));

        assertThrows(IllegalStateException.class, () -> service.deleteDraft(userId, docId));
        verify(storageService, never()).delete(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void uploadSingleCompensatesAndDeletesPhysicalFileIfDatabasePersistenceFails() {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[]{1, 2, 3});
        StoredFile stored = new StoredFile("/path/to/receipt.png", "receipt.png", "image/png", 3L);

        when(storageService.store(userId, file)).thenReturn(stored);
        when(storageService.resolveFileUrl(any())).thenReturn("http://localhost:8080/api/documents/" + docId + "/file");
        when(repository.save(any(UploadedDocument.class))).thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> service.uploadSingle(userId, file, "receipt"));

        verify(storageService).delete("/path/to/receipt.png");
        verify(asyncProcessingService, never()).processAfterCommit(any());
    }

    @Test
    void retryProcessingRejectsActiveProcessingDocumentWithinThreshold() {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setProcessingStatus(DocumentStatus.processing);
        doc.setProcessingStartedAt(java.time.LocalDateTime.now().minusMinutes(1)); // 1 min ago (active < 5 min)

        when(repository.findByIdAndUserId(docId, userId)).thenReturn(Optional.of(doc));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.retryProcessing(userId, docId));
        assertTrue(ex.getMessage().contains("currently being processed"));
        verify(repository, never()).save(any());
    }

    @Test
    void retryProcessingAllowsStaleProcessingDocument() {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setProcessingStatus(DocumentStatus.processing);
        doc.setProcessingStartedAt(java.time.LocalDateTime.now().minusMinutes(10)); // 10 min ago (stale > 5 min)

        when(repository.findByIdAndUserId(docId, userId)).thenReturn(Optional.of(doc));
        when(repository.save(any(UploadedDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        UploadedDocument retried = service.retryProcessing(userId, docId);

        assertEquals(DocumentStatus.pending, retried.getProcessingStatus());
        assertNull(retried.getProcessingStartedAt());
        assertNull(retried.getFailureReason());
        verify(repository).save(doc);
        verify(asyncProcessingService).processAfterCommit(docId);
    }
}
