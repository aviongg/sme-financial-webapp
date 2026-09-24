package com.app.sme_health_backend.documents.processing;

import com.app.sme_health_backend.documents.ocr.OcrClient;
import com.app.sme_health_backend.documents.ocr.OcrClientException;
import com.app.sme_health_backend.documents.ocr.OcrExtraction;
import com.app.sme_health_backend.documents.ocr.OcrRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentDraftProcessorTests {
    @Mock OcrClient client;
    @Mock DocumentDraftStore store;
    private DocumentDraftProcessor processor;
    private final UUID documentId = UUID.randomUUID();
    private final OcrRequest request = new OcrRequest(new byte[]{1, 2, 3}, "image/png", OcrExtraction.DocumentType.invoice);

    @BeforeEach
    void setUp() {
        processor = new DocumentDraftProcessor(client, store);
    }

    @Test
    void keepsRecognizedValuesAndMissingContactInAnUnapprovedDraft() {
        OcrExtraction partial = new OcrExtraction(LocalDate.of(2026, 9, 1), new BigDecimal("1250.50"), null,
                OcrExtraction.Category.unknown, OcrExtraction.Confidence.medium, OcrExtraction.DocumentType.invoice);
        when(store.claimPending(documentId)).thenReturn(Optional.of(request));
        when(client.extract(request)).thenReturn(partial);
        when(store.saveDraftIfProcessing(documentId, partial, DocumentStatus.needs_review)).thenReturn(true);

        assertEquals(Optional.of(DocumentStatus.needs_review), processor.process(documentId));
        verify(store).saveDraftIfProcessing(documentId, partial, DocumentStatus.needs_review);
        assertNull(partial.vendorOrParty());
        assertEquals(new BigDecimal("1250.50"), partial.amount());
        verify(store, never()).saveDraftIfProcessing(any(), any(), eq(DocumentStatus.confirmed));
    }

    @Test
    void completeHighConfidenceResultStillRequiresExplicitHumanApproval() {
        OcrExtraction complete = complete();
        when(store.claimPending(documentId)).thenReturn(Optional.of(request));
        when(client.extract(request)).thenReturn(complete);
        when(store.saveDraftIfProcessing(documentId, complete, DocumentStatus.extracted)).thenReturn(true);

        assertEquals(Optional.of(DocumentStatus.extracted), processor.process(documentId));
        verify(store, never()).saveDraftIfProcessing(any(), any(), eq(DocumentStatus.confirmed));
    }

    @Test
    void allBlankResultIsReviewableRatherThanFabricatedOrMarkedFailed() {
        OcrExtraction blank = new OcrExtraction(null, null, null, OcrExtraction.Category.unknown,
                OcrExtraction.Confidence.low, OcrExtraction.DocumentType.unknown);
        when(store.claimPending(documentId)).thenReturn(Optional.of(request));
        when(client.extract(request)).thenReturn(blank);
        when(store.saveDraftIfProcessing(documentId, blank, DocumentStatus.needs_review)).thenReturn(true);

        assertEquals(Optional.of(DocumentStatus.needs_review), processor.process(documentId));
        verify(store, never()).failIfProcessing(any(), any());
    }

    @Test
    void terminalFailureMarksFailedWithoutSavingFabricatedFieldsOrRetrying() {
        when(store.claimPending(documentId)).thenReturn(Optional.of(request));
        when(client.extract(request)).thenThrow(new OcrClientException(OcrClientException.Reason.unavailable));
        when(store.failIfProcessing(documentId, OcrClientException.Reason.unavailable)).thenReturn(true);

        assertEquals(Optional.of(DocumentStatus.failed), processor.process(documentId));
        verify(client, times(1)).extract(request);
        verify(store, never()).saveDraftIfProcessing(any(), any(), any());
    }

    @Test
    void malformedUpstreamResultFailsSafely() {
        when(store.claimPending(documentId)).thenReturn(Optional.of(request));
        when(client.extract(request)).thenThrow(new OcrClientException(OcrClientException.Reason.invalid_response));
        when(store.failIfProcessing(documentId, OcrClientException.Reason.invalid_response)).thenReturn(true);
        assertEquals(Optional.of(DocumentStatus.failed), processor.process(documentId));
    }

    @Test
    void onlyAnAtomicPendingClaimMayTriggerExtraction() {
        when(store.claimPending(documentId)).thenReturn(Optional.empty());
        assertEquals(Optional.empty(), processor.process(documentId));
        verifyNoInteractions(client);
        verify(store, never()).saveDraftIfProcessing(any(), any(), any());
        verify(store, never()).failIfProcessing(any(), any());
    }

    @Test
    void preservesHumanChangesWhenDocumentLeavesProcessingBeforeResponse() {
        when(store.claimPending(documentId)).thenReturn(Optional.of(request));
        when(client.extract(request)).thenReturn(complete());
        when(store.saveDraftIfProcessing(eq(documentId), any(), eq(DocumentStatus.extracted))).thenReturn(false);
        assertEquals(Optional.empty(), processor.process(documentId));
        verify(store, never()).failIfProcessing(any(), any());
    }

    @Test
    void preservesAllSixExistingStatusesWithoutAddingADraftStatus() {
        assertEquals(Arrays.asList("pending", "processing", "extracted", "needs_review", "confirmed", "failed"),
                Arrays.stream(DocumentStatus.values()).map(Enum::name).toList());
    }

    private OcrExtraction complete() {
        return new OcrExtraction(LocalDate.of(2026, 9, 1), BigDecimal.TEN, "Example Supplier",
                OcrExtraction.Category.purchase, OcrExtraction.Confidence.high, OcrExtraction.DocumentType.invoice);
    }
}
