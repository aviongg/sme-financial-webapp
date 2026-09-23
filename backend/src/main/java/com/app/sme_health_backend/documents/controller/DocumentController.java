package com.app.sme_health_backend.documents.controller;

import com.app.sme_health_backend.documents.dto.BulkUploadResponse;
import com.app.sme_health_backend.documents.dto.DocumentMapper;
import com.app.sme_health_backend.documents.dto.DocumentResponse;
import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.exception.DocumentAlreadyConfirmedException;
import com.app.sme_health_backend.documents.exception.DocumentNotFoundException;
import com.app.sme_health_backend.documents.exception.DocumentValidationException;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.service.DocumentUploadService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentUploadService uploadService;

    public DocumentController(DocumentUploadService uploadService) {
        this.uploadService = Objects.requireNonNull(uploadService, "uploadService is required");
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") UUID userId,
            @RequestParam(value = "documentTypeHint", required = false) String documentTypeHint
    ) {
        UploadedDocument doc = uploadService.uploadSingle(userId, file, documentTypeHint);
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentMapper.toResponse(doc));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BulkUploadResponse> uploadBulk(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("userId") UUID userId,
            @RequestParam(value = "documentTypeHint", required = false) String documentTypeHint
    ) {
        List<UploadedDocument> docs = uploadService.uploadBulk(userId, files, documentTypeHint);
        List<DocumentResponse> responses = docs.stream().map(DocumentMapper::toResponse).toList();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new BulkUploadResponse(responses.size(), responses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable("id") UUID id,
            @RequestParam("userId") UUID userId
    ) {
        UploadedDocument doc = uploadService.getDocument(userId, id);
        return ResponseEntity.ok(DocumentMapper.toResponse(doc));
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> listDocuments(
            @RequestParam("userId") UUID userId,
            @RequestParam(value = "status", required = false) DocumentStatus status,
            @RequestParam(value = "month", required = false) String month
    ) {
        List<UploadedDocument> docs = uploadService.listDocuments(userId, status, month);
        return ResponseEntity.ok(docs.stream().map(DocumentMapper::toResponse).toList());
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> getDocumentFile(
            @PathVariable("id") UUID id,
            @RequestParam("userId") UUID userId
    ) {
        UploadedDocument doc = uploadService.getDocument(userId, id);
        byte[] bytes = uploadService.getDocumentBytes(userId, id);

        String contentType = doc.getContentType() != null ? doc.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String filename = doc.getOriginalFilename() != null ? doc.getOriginalFilename() : "document.bin";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(bytes);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<DocumentResponse> retryProcessing(
            @PathVariable("id") UUID id,
            @RequestParam("userId") UUID userId
    ) {
        UploadedDocument doc = uploadService.retryProcessing(userId, id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentMapper.toResponse(doc));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable("id") UUID id,
            @RequestParam("userId") UUID userId
    ) {
        uploadService.deleteDraft(userId, id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(DocumentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "not_found", "message", ex.getMessage()));
    }

    @ExceptionHandler(DocumentValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(DocumentValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "invalid_request", "message", ex.getMessage()));
    }

    @ExceptionHandler(DocumentAlreadyConfirmedException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyConfirmed(DocumentAlreadyConfirmedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "already_confirmed", "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "illegal_state", "message", ex.getMessage()));
    }
}
