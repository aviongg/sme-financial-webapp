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
            Authentication authentication,
            HttpServletRequest request
    ) {
        boolean isInternalService = isInternalOcrService(authentication, request);

        UploadedDocument doc;
        byte[] bytes;
        if (isInternalService) {
            // Narrowly scoped internal OCR branch (no session or active business context)
            doc = uploadService.getDocument(id);
            bytes = uploadService.getDocumentBytes(id);
        } else {
            // Browser user branch: requires active business + DOCUMENT_READ permission + tenant-scoped lookup
            BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.DOCUMENT_READ);
            doc = uploadService.getDocument(context.businessId(), id);
            bytes = uploadService.getDocumentBytes(context.businessId(), id);
        }

        String contentType = doc.getContentType() != null ? doc.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String filename = doc.getOriginalFilename() != null ? doc.getOriginalFilename() : "document.bin";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(bytes);
    }

    private boolean isInternalOcrService(Authentication authentication, HttpServletRequest request) {
        Authentication auth = authentication;
        if (auth == null) {
            auth = SecurityContextHolder.getContext().getAuthentication();
        }
        if (auth == null && request != null) {
            if (request.getUserPrincipal() instanceof Authentication userAuth) {
                auth = userAuth;
            } else if (request.getSession(false) != null) {
                Object sessionContext = request.getSession(false).getAttribute("SPRING_SECURITY_CONTEXT");
                if (sessionContext instanceof SecurityContext secContext) {
                    auth = secContext.getAuthentication();
                }
            }
            if (auth == null && request.getAttribute("SPRING_SECURITY_CONTEXT") instanceof SecurityContext secContext) {
                auth = secContext.getAuthentication();
            }
        }

        if (auth != null && auth.getAuthorities() != null) {
            for (var authority : auth.getAuthorities()) {
                if ("ROLE_INTERNAL_OCR".equals(authority.getAuthority()) || "INTERNAL_OCR".equals(authority.getAuthority())) {
                    return true;
                }
            }
        }

        return request != null && (request.isUserInRole("INTERNAL_OCR") || request.isUserInRole("ROLE_INTERNAL_OCR"));
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
