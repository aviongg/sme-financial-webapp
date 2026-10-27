package com.app.sme_health_backend.commercial.controller;

import com.app.sme_health_backend.commercial.dto.CommercialDocumentListResponse;
import com.app.sme_health_backend.commercial.dto.CommercialDocumentResponse;
import com.app.sme_health_backend.commercial.dto.CommercialPreviewResponse;
import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.model.CommercialDocumentStatus;
import com.app.sme_health_backend.commercial.service.CommercialDocumentService;
import com.app.sme_health_backend.commercial.service.CommercialException;
import com.app.sme_health_backend.commercial.service.CommercialValidationException;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Shared F04 Phase A endpoints. {@link InvoiceController} and {@link BillController} bind the kind to the path,
 * so an invoice ID is never visible through the bill API (and vice versa) and kind is never client-controlled.
 * Reads and previews require FINANCIAL_DATA_READ; every mutation requires RECORD_CREATE_UPDATE.
 */
public abstract class CommercialDocumentController {
    private final BusinessAuthorizationService authorization;
    private final CommercialDocumentService service;
    private final CommercialDocumentKind kind;

    protected CommercialDocumentController(
            BusinessAuthorizationService authorization,
            CommercialDocumentService service,
            CommercialDocumentKind kind
    ) {
        this.authorization = authorization;
        this.service = service;
        this.kind = kind;
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<CommercialDocumentListResponse> list(
            @RequestParam(value = "status", required = false) String status,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return ok(service.list(context, kind, parseStatus(status)));
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<CommercialDocumentResponse> get(@PathVariable("id") UUID id, HttpServletRequest request) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return ok(service.get(context, kind, id));
    }

    @PostMapping(value = "/preview", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> preview(HttpServletRequest request) throws IOException {
        authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        CommercialPreviewResponse preview = service.preview(kind, CommercialDocumentRequestReader.readPreview(body));
        return ok(preview);
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> create(HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        CommercialDocumentResponse created = service.createDraft(context, kind, CommercialDocumentRequestReader.readCreate(body));
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(created);
    }

    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> update(@PathVariable("id") UUID id, HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        return ok(service.updateDraft(context, kind, id, CommercialDocumentRequestReader.readUpdate(body)));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") UUID id,
            @RequestParam(value = "expectedVersion", required = false) Long expectedVersion,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        service.deleteDraft(context, kind, id, expectedVersion);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @PostMapping(value = "/{id}/finalize", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> finalizeDocument(@PathVariable("id") UUID id, HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        return ok(service.finalizeDraft(context, kind, id, CommercialDocumentRequestReader.readFinalize(body)));
    }

    @PostMapping(value = "/{id}/void", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> voidDocument(@PathVariable("id") UUID id, HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        return ok(service.voidFinalized(context, kind, id, CommercialDocumentRequestReader.readVoid(body)));
    }

    @ExceptionHandler(CommercialValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(CommercialValidationException ex) {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body(Map.of(
                "status", 400,
                "error", "commercial_document_invalid",
                "message", ex.getMessage(),
                "errors", ex.errors()
        ));
    }

    @ExceptionHandler(CommercialException.class)
    public ResponseEntity<Map<String, Object>> handleDomain(CommercialException ex) {
        return ResponseEntity.status(ex.status()).header("Cache-Control", "no-store").body(Map.of(
                "status", ex.status(),
                "error", ex.code(),
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName() != null ? ex.getName() : "parameter";
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body(Map.of(
                "status", 400,
                "error", "commercial_document_invalid",
                "message", "Invalid request parameter.",
                "errors", Map.of(name, "invalid")
        ));
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
    }

    private static CommercialDocumentStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CommercialDocumentStatus.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new CommercialValidationException("status", "enum_invalid");
        }
    }

    private static byte[] readBoundedBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > CommercialDocumentRequestReader.MAX_REQUEST_BYTES) {
            return null;
        }
        byte[] body = request.getInputStream().readNBytes(CommercialDocumentRequestReader.MAX_REQUEST_BYTES + 1);
        return body.length > CommercialDocumentRequestReader.MAX_REQUEST_BYTES ? null : body;
    }

    private static ResponseEntity<Map<String, Object>> tooLarge() {
        return ResponseEntity.status(413).header("Cache-Control", "no-store").body(Map.of(
                "status", 413,
                "error", "commercial_request_too_large",
                "message", "The commercial document request is too large."
        ));
    }
}
