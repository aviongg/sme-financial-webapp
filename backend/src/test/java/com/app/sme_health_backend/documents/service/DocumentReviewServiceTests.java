package com.app.sme_health_backend.documents.service;

import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.documents.dto.DocumentDraftCorrectionRequest;
import com.app.sme_health_backend.documents.entity.*;
import com.app.sme_health_backend.documents.exception.*;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentReviewServiceTests {
    private final UploadedDocumentRepository documents = mock(UploadedDocumentRepository.class);
    private final DocumentCorrectionRepository corrections = mock(DocumentCorrectionRepository.class);
    private final ObjectMapper json = new ObjectMapper();
    private final DocumentReviewService service = new DocumentReviewService(documents, corrections, mock(SecurityAuditService.class), json);
    private final UUID business = UUID.randomUUID(), docId = UUID.randomUUID(), actor = UUID.randomUUID();
    private UploadedDocument document;
    private static final String ORIGINAL = "{\"date\":\"2026-09-01\",\"amount\":5,\"vendor_or_party\":\"OCR Party\",\"category\":\"sales\",\"confidence\":\"low\",\"document_type_detected\":\"invoice\"}";

    @BeforeEach void setup() {
        document = new UploadedDocument(); document.setId(docId); document.setUserId(business);
        document.setProcessingStatus(DocumentStatus.needs_review); document.setExtractedData(ORIGINAL);
        when(documents.findByIdAndUserIdForUpdate(docId, business)).thenReturn(Optional.of(document));
        when(documents.save(document)).thenReturn(document);
    }
    private DocumentDraftCorrectionRequest request(String amount) {
        return new DocumentDraftCorrectionRequest(LocalDate.of(2026, 9, 1), new BigDecimal(amount), "Reviewed Party", "sales", "invoice");
    }
    @Test void preservesOriginalConfidenceAndRecordsActorWithPreviousAndNewStates() {
        service.updateDraft(business, docId, actor, request("15.00"));
        assertEquals(ORIGINAL, document.getExtractedData());
        assertEquals("low", json.readTree(document.getReviewedData()).get("confidence").asText());
        assertEquals(DocumentStatus.extracted, document.getProcessingStatus());
        ArgumentCaptor<DocumentCorrection> capture = ArgumentCaptor.forClass(DocumentCorrection.class);
        verify(corrections).save(capture.capture());
        assertEquals(actor, capture.getValue().getActorUserId());
        assertEquals(business, capture.getValue().getBusinessId());
        assertEquals(ORIGINAL, capture.getValue().getPreviousData());
        assertEquals(document.getReviewedData(), capture.getValue().getNewData());
        assertEquals(List.of("amount", "vendor_or_party"), capture.getValue().getChangedFields());
        assertNull(document.getConfirmedData());
    }
    @Test void repeatedCorrectionsUsePreviousReviewedSnapshotAndNoOpDoesNotAppend() {
        service.updateDraft(business, docId, actor, request("15.00"));
        String previous = document.getReviewedData();
        service.updateDraft(business, docId, actor, request("20.00"));
        ArgumentCaptor<DocumentCorrection> capture = ArgumentCaptor.forClass(DocumentCorrection.class);
        verify(corrections, times(2)).save(capture.capture());
        assertEquals(previous, capture.getAllValues().get(1).getPreviousData());
        service.updateDraft(business, docId, actor, request("20.00"));
        verify(corrections, times(2)).save(any());
    }
    @Test void confirmedAndProcessingDraftsCannotBeEdited() {
        for (DocumentStatus status : List.of(DocumentStatus.confirmed, DocumentStatus.processing, DocumentStatus.pending, DocumentStatus.failed)) {
            document.setProcessingStatus(status);
            assertThrows(IllegalStateException.class, () -> service.updateDraft(business, docId, actor, request("10.00")));
        }
        verifyNoInteractions(corrections);
    }
    @Test void rejectsCrossTenantAndMissingActor() {
        assertThrows(DocumentNotFoundException.class, () -> service.updateDraft(UUID.randomUUID(), docId, actor, request("10.00")));
        assertThrows(NullPointerException.class, () -> service.updateDraft(business, docId, null, request("10.00")));
        verifyNoInteractions(corrections);
    }
    @Test void rejectsUnsupportedValuesWithoutMutatingOriginal() {
        assertThrows(DocumentValidationException.class, () -> service.updateDraft(business, docId, actor, request("-1")));
        assertThrows(DocumentValidationException.class, () -> service.updateDraft(business, docId, actor,
                new DocumentDraftCorrectionRequest(null, null, null, "malformed\"category", "invoice")));
        assertEquals(ORIGINAL, document.getExtractedData());
        verifyNoInteractions(corrections);
    }
    @Test void historyRequiresDocumentInCurrentTenant() {
        assertThrows(DocumentNotFoundException.class, () -> service.history(business, docId, actor));
        verifyNoInteractions(corrections);
    }

    @Test void amountOnlyJsonPatchPreservesOtherOriginalValuesAndOnlyRecordsAmountChange() {
        DocumentDraftCorrectionRequest partial = json.readValue("{\"amount\":20.00}", DocumentDraftCorrectionRequest.class);
        service.updateDraft(business, docId, actor, partial);
        var reviewed = json.readTree(document.getReviewedData());
        var original = json.readTree(ORIGINAL);
        for (String field : List.of("date", "vendor_or_party", "category", "document_type_detected", "confidence")) {
            assertEquals(original.get(field), reviewed.get(field), field + " must survive an omitted PATCH field");
        }
        ArgumentCaptor<DocumentCorrection> capture = ArgumentCaptor.forClass(DocumentCorrection.class);
        verify(corrections).save(capture.capture());
        assertEquals(List.of("amount"), capture.getValue().getChangedFields());
        // Serialization scale changes must not append a second correction.
        service.updateDraft(business, docId, actor, json.readValue("{\"amount\":20}", DocumentDraftCorrectionRequest.class));
        verify(corrections, times(1)).save(any());
    }

    @Test void explicitNullClearsOnlyRequestedFieldAndRequiresReview() {
        service.updateDraft(business, docId, actor, json.readValue("{\"amount\":null}", DocumentDraftCorrectionRequest.class));
        var reviewed = json.readTree(document.getReviewedData());
        assertTrue(reviewed.get("amount").isNull());
        assertEquals("2026-09-01", reviewed.get("date").asText());
        assertEquals("OCR Party", reviewed.get("vendor_or_party").asText());
        assertEquals(DocumentStatus.needs_review, document.getProcessingStatus());
    }

    @Test void emptyPatchDoesNotWriteCorrectionOrCreateReviewedData() {
        service.updateDraft(business, docId, actor, json.readValue("{}", DocumentDraftCorrectionRequest.class));
        assertNull(document.getReviewedData());
        verifyNoInteractions(corrections);
        verify(documents, never()).save(any());
    }
}
