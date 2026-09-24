package com.app.sme_health_backend.documents.controller;

import com.app.sme_health_backend.documents.dto.BulkUploadResponse;
import com.app.sme_health_backend.documents.dto.DocumentConfirmationRequest;
import com.app.sme_health_backend.documents.dto.DocumentDraftCorrectionRequest;
import com.app.sme_health_backend.documents.dto.DocumentMapper;
import com.app.sme_health_backend.documents.dto.DocumentQueryRequest;
import com.app.sme_health_backend.documents.dto.DocumentResponse;
import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.exception.DocumentAlreadyConfirmedException;
import com.app.sme_health_backend.documents.exception.DocumentNotFoundException;
import com.app.sme_health_backend.documents.exception.DocumentValidationException;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.service.DocumentConfirmationService;
import com.app.sme_health_backend.documents.service.DocumentUploadService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentUploadService uploadService;
    private final DocumentConfirmationService confirmationService;
    private final BusinessAuthorizationService authService;

    public DocumentController(
            DocumentUploadService uploadService,
            DocumentConfirmationService confirmationService,
            BusinessAuthorizationService authService
    ) {
        this.uploadService = Objects.requireNonNull(uploadService, "uploadService is required");
        this.confirmationService = Objects.requireNonNull(confirmationService, "confirmationService is required");
        this.authService = Objects.requireNonNull(authService, "authService is required");
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentTypeHint", required = false) String documentTypeHint,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.DOCUMENT_UPLOAD);

        UploadedDocument doc = uploadService.uploadSingle(context.businessId(), file, documentTypeHint);
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentMapper.toResponse(doc));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BulkUploadResponse> uploadBulk(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "documentTypeHint", required = false) String documentTypeHint,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.DOCUMENT_UPLOAD);

        List<UploadedDocument> docs = uploadService.uploadBulk(context.businessId(), files, documentTypeHint);
        List<DocumentResponse> responses = docs.stream().map(DocumentMapper::toResponse).toList();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new BulkUploadResponse(responses.size(), responses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.DOCUMENT_READ);

        UploadedDocument doc = uploadService.getDocument(context.businessId(), id);
        return ResponseEntity.ok(DocumentMapper.toResponse(doc));
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> listDocuments(HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.DOCUMENT_READ);

        List<UploadedDocument> docs = uploadService.listDocuments(context.businessId(), null, null);
        return ResponseEntity.ok(docs.stream().map(DocumentMapper::toResponse).toList());
    }

    @PostMapping("/query")
    public ResponseEntity<List<DocumentResponse>> queryDocuments(
            @RequestBody(required = false) DocumentQueryRequest queryRequest,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.DOCUMENT_READ);

        DocumentStatus status = queryRequest != null ? queryRequest.status() : null;
        String month = queryRequest != null ? queryRequest.month() : null;

        List<UploadedDocument> docs = uploadService.listDocuments(context.businessId(), status, month);
        return ResponseEntity.ok(docs.stream().map(DocumentMapper::toResponse).toList());
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> getDocumentFile(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.DOCUMENT_READ);
        UploadedDocument doc = uploadService.getDocument(context.businessId(), id);
        byte[] bytes = uploadService.getDocumentBytes(context.businessId(), id);

        String contentType = doc.getContentType() != null ? doc.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String filename = sanitizeForContentDisposition(doc.getOriginalFilename());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(bytes);
    }

    private String sanitizeForContentDisposition(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document.bin";
        }
        String clean = Paths.get(filename).getFileName().toString();
        clean = clean.replaceAll("[\\r\\n\\\"\\\\;\\u0000]", "_").replaceAll("[^a-zA-Z0-9._-]", "_");
        return clean.length() > 200 ? clean.substring(0, 200) : clean;
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<DocumentResponse> retryProcessing(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.DOCUMENT_EDIT);

        UploadedDocument doc = uploadService.retryProcessing(context.businessId(), id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentMapper.toResponse(doc));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.DOCUMENT_DELETE);

        uploadService.deleteDraft(context.businessId(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<DocumentResponse> updateDraft(
            @PathVariable("id") UUID id,
            @RequestBody DocumentDraftCorrectionRequest correctionRequest,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.DOCUMENT_EDIT);

        UploadedDocument doc = uploadService.updateDraft(context.businessId(), id, correctionRequest);
        return ResponseEntity.ok(DocumentMapper.toResponse(doc));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<DocumentResponse> confirmDocument(
            @PathVariable("id") UUID id,
            @Valid @RequestBody DocumentConfirmationRequest confirmationRequest,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.DOCUMENT_CONFIRM);

        UploadedDocument doc = confirmationService.confirmDocument(context.businessId(), id, confirmationRequest);
        return ResponseEntity.ok(DocumentMapper.toResponse(doc));
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
