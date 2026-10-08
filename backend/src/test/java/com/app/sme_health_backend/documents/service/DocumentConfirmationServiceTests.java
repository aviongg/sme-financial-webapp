package com.app.sme_health_backend.documents.service;

import com.app.sme_health_backend.documents.dto.DocumentConfirmationRequest;
import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.exception.DocumentAlreadyConfirmedException;
import com.app.sme_health_backend.documents.exception.DocumentNotFoundException;
import com.app.sme_health_backend.documents.exception.DocumentValidationException;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentConfirmationServiceTests {

    @Mock
    private UploadedDocumentRepository repository;

    @Mock
    private MonthlyRecordService monthlyRecordService;

    private DocumentConfirmationService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID docId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DocumentConfirmationService(repository, monthlyRecordService);
    }

    @Test
    void successfullyConfirmsExtractedDocumentAndUpdatesMonthlyRecord() {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setProcessingStatus(DocumentStatus.extracted);

        when(repository.findByIdAndUserIdForUpdate(docId, userId)).thenReturn(Optional.of(doc));
        when(repository.save(any(UploadedDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentConfirmationRequest request = new DocumentConfirmationRequest(
                "2026-03",
                new BigDecimal("12500.00"),
                LocalDate.of(2026, 3, 10),
                "Lahore Wholesale",
                "cogs",
                "cash_outflow",
                null
        );

        UploadedDocument confirmed = service.confirmDocument(userId, docId, request);

        assertEquals(DocumentStatus.confirmed, confirmed.getProcessingStatus());
        assertEquals("2026-03", confirmed.getLinkedMonth());
        assertNotNull(confirmed.getConfirmedAt());
        assertNotNull(confirmed.getConfirmedData());
        assertTrue(confirmed.getConfirmedData().contains("Lahore Wholesale"));
        assertTrue(confirmed.getConfirmedData().contains("12500.00"));

        verify(monthlyRecordService).applyDocumentContribution(
                userId,
                "2026-03",
                "cogs",
                "cash_outflow",
                new BigDecimal("12500.00"),
                null
        );
        verify(repository).save(doc);
    }

    @Test
    void successfullyConfirmsNeedsReviewDocument() {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setProcessingStatus(DocumentStatus.needs_review);

        when(repository.findByIdAndUserIdForUpdate(docId, userId)).thenReturn(Optional.of(doc));
        when(repository.save(any(UploadedDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentConfirmationRequest request = new DocumentConfirmationRequest(
                "2026-03",
                new BigDecimal("2500.00"),
                null,
                null,
                "operating_expenses",
                "cash_outflow",
                null
        );

        UploadedDocument confirmed = service.confirmDocument(userId, docId, request);

        assertEquals(DocumentStatus.confirmed, confirmed.getProcessingStatus());
        verify(monthlyRecordService).applyDocumentContribution(
                eq(userId), eq("2026-03"), eq("operating_expenses"), eq("cash_outflow"), eq(new BigDecimal("2500.00")), any()
        );
    }

    @Test
    void duplicateConfirmationThrowsDocumentAlreadyConfirmedException() {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setProcessingStatus(DocumentStatus.confirmed);

        when(repository.findByIdAndUserIdForUpdate(docId, userId)).thenReturn(Optional.of(doc));

        DocumentConfirmationRequest request = new DocumentConfirmationRequest(
                "2026-03", new BigDecimal("100.00"), null, null, "revenue", "cash_inflow", null
        );

        assertThrows(DocumentAlreadyConfirmedException.class, () ->
                service.confirmDocument(userId, docId, request)
        );

        verify(monthlyRecordService, never()).applyDocumentContribution(any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmationRejectsPendingOrProcessingDocuments() {
        UploadedDocument pendingDoc = new UploadedDocument();
        pendingDoc.setId(docId);
        pendingDoc.setUserId(userId);
        pendingDoc.setProcessingStatus(DocumentStatus.pending);

        when(repository.findByIdAndUserIdForUpdate(docId, userId)).thenReturn(Optional.of(pendingDoc));

        DocumentConfirmationRequest request = new DocumentConfirmationRequest(
                "2026-03", new BigDecimal("100.00"), null, null, "revenue", "cash_inflow", null
        );

        assertThrows(IllegalStateException.class, () ->
                service.confirmDocument(userId, docId, request)
        );
    }

    @Test
    void enforcesUserOwnership() {
        UUID wrongUser = UUID.randomUUID();
        when(repository.findByIdAndUserIdForUpdate(docId, wrongUser)).thenReturn(Optional.empty());

        DocumentConfirmationRequest request = new DocumentConfirmationRequest(
                "2026-03", new BigDecimal("100.00"), null, null, "revenue", "cash_inflow", null
        );

        assertThrows(DocumentNotFoundException.class, () ->
                service.confirmDocument(wrongUser, docId, request)
        );
    }

    @Test
    void rejectsInvalidClassificationOrCashFlowImpact() {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setProcessingStatus(DocumentStatus.extracted);

        when(repository.findByIdAndUserIdForUpdate(docId, userId)).thenReturn(Optional.of(doc));

        DocumentConfirmationRequest badClass = new DocumentConfirmationRequest(
                "2026-03", new BigDecimal("100.00"), null, null, "invalid_class", "none", null
        );
        assertThrows(DocumentValidationException.class, () -> service.confirmDocument(userId, docId, badClass));

        DocumentConfirmationRequest badFlow = new DocumentConfirmationRequest(
                "2026-03", new BigDecimal("100.00"), null, null, "revenue", "invalid_flow", null
        );
        assertThrows(DocumentValidationException.class, () -> service.confirmDocument(userId, docId, badFlow));
    }
}
