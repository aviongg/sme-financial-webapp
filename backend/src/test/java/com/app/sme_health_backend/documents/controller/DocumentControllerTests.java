package com.app.sme_health_backend.documents.controller;

import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.exception.DocumentNotFoundException;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.service.DocumentUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
class DocumentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentUploadService uploadService;

    @MockitoBean
    private com.app.sme_health_backend.documents.service.DocumentConfirmationService confirmationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID docId = UUID.randomUUID();


    @Test
    void uploadSingleDocumentReturns201Created() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[]{1, 2, 3});
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setFileUrl("http://localhost:8080/api/documents/" + docId + "/file");
        doc.setOriginalFilename("receipt.png");
        doc.setContentType("image/png");
        doc.setProcessingStatus(DocumentStatus.pending);

        when(uploadService.uploadSingle(eq(userId), any(), eq("receipt"))).thenReturn(doc);

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("userId", userId.toString())
                        .param("documentTypeHint", "receipt"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(docId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.processingStatus").value("pending"))
                .andExpect(jsonPath("$.originalFilename").value("receipt.png"));
    }

    @Test
    void uploadBulkDocumentsReturns202Accepted() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("files", "doc1.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile file2 = new MockMultipartFile("files", "doc2.jpg", "image/jpeg", new byte[]{2});

        UploadedDocument doc1 = new UploadedDocument();
        doc1.setId(UUID.randomUUID());
        doc1.setUserId(userId);
        doc1.setProcessingStatus(DocumentStatus.pending);

        UploadedDocument doc2 = new UploadedDocument();
        doc2.setId(UUID.randomUUID());
        doc2.setUserId(userId);
        doc2.setProcessingStatus(DocumentStatus.pending);

        when(uploadService.uploadBulk(eq(userId), anyList(), any())).thenReturn(List.of(doc1, doc2));

        mockMvc.perform(multipart("/api/documents/bulk")
                        .file(file1)
                        .file(file2)
                        .param("userId", userId.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.documents.length()").value(2));
    }

    @Test
    void getDocumentReturns200WithDraftDetails() throws Exception {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setProcessingStatus(DocumentStatus.extracted);
        doc.setExtractedData("{\"date\":\"2026-09-10\",\"amount\":1500.00}");

        when(uploadService.getDocument(userId, docId)).thenReturn(doc);

        mockMvc.perform(get("/api/documents/{id}", docId)
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docId.toString()))
                .andExpect(jsonPath("$.processingStatus").value("extracted"))
                .andExpect(jsonPath("$.extractedData").isNotEmpty());
    }

    @Test
    void getDocumentReturns404WhenNotFound() throws Exception {
        when(uploadService.getDocument(userId, docId))
                .thenThrow(new DocumentNotFoundException("Document not found"));

        mockMvc.perform(get("/api/documents/{id}", docId)
                        .param("userId", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void getDocumentFileReturnsBytesWithCorrectContentType() throws Exception {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setContentType("image/png");
        doc.setOriginalFilename("test.png");

        byte[] fakeBytes = new byte[]{1, 2, 3, 4};
        when(uploadService.getDocument(userId, docId)).thenReturn(doc);
        when(uploadService.getDocumentBytes(userId, docId)).thenReturn(fakeBytes);

        mockMvc.perform(get("/api/documents/{id}/file", docId)
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(fakeBytes));
    }

    @Test
    void retryProcessingReturns202Accepted() throws Exception {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setProcessingStatus(DocumentStatus.pending);

        when(uploadService.retryProcessing(userId, docId)).thenReturn(doc);

        mockMvc.perform(post("/api/documents/{id}/retry", docId)
                        .param("userId", userId.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processingStatus").value("pending"));
    }

    @Test
    void deleteDraftReturns204NoContent() throws Exception {
        doNothing().when(uploadService).deleteDraft(userId, docId);

        mockMvc.perform(delete("/api/documents/{id}", docId)
                        .param("userId", userId.toString()))
                .andExpect(status().isNoContent());

        verify(uploadService).deleteDraft(userId, docId);
    }

    @Test
    void updateDraftReturns200WithUpdatedDraft() throws Exception {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setProcessingStatus(DocumentStatus.extracted);
        doc.setExtractedData("{\"date\":\"2026-03-15\",\"amount\":12000.00}");

        when(uploadService.updateDraft(eq(userId), eq(docId), any())).thenReturn(doc);

        mockMvc.perform(patch("/api/documents/{id}", docId)
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":12000.00,\"vendorOrParty\":\"Al-Madina\",\"category\":\"expense\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docId.toString()))
                .andExpect(jsonPath("$.processingStatus").value("extracted"));
    }

    @Test
    void confirmDocumentReturns200WithConfirmedStatus() throws Exception {
        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setProcessingStatus(DocumentStatus.confirmed);
        doc.setLinkedMonth("2026-03");

        when(confirmationService.confirmDocument(eq(userId), eq(docId), any())).thenReturn(doc);

        mockMvc.perform(post("/api/documents/{id}/confirm", docId)
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMonth\":\"2026-03\",\"confirmedAmount\":12000.00,\"targetClassification\":\"operating_expenses\",\"cashFlowImpact\":\"cash_outflow\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docId.toString()))
                .andExpect(jsonPath("$.processingStatus").value("confirmed"))
                .andExpect(jsonPath("$.linkedMonth").value("2026-03"));
    }

    @Test
    void confirmDuplicateDocumentReturns409Conflict() throws Exception {
        when(confirmationService.confirmDocument(eq(userId), eq(docId), any()))
                .thenThrow(new com.app.sme_health_backend.documents.exception.DocumentAlreadyConfirmedException("Already confirmed"));

        mockMvc.perform(post("/api/documents/{id}/confirm", docId)
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMonth\":\"2026-03\",\"confirmedAmount\":12000.00,\"targetClassification\":\"operating_expenses\",\"cashFlowImpact\":\"cash_outflow\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("already_confirmed"));
    }
}
