package com.app.sme_health_backend.estimates.controller;

import com.app.sme_health_backend.estimates.model.EstimateStatus;
import com.app.sme_health_backend.estimates.service.ConversionPrecisionException;
import com.app.sme_health_backend.estimates.service.EstimateConversionService;
import com.app.sme_health_backend.estimates.service.EstimateException;
import com.app.sme_health_backend.estimates.service.EstimateService;
import com.app.sme_health_backend.estimates.service.EstimateValidationException;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.projects.service.ProjectException;
import com.app.sme_health_backend.projects.service.ProjectValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * F06 Phase B persisted planning estimates. Reads require FINANCIAL_DATA_READ; every mutation, approval and conversion
 * requires RECORD_CREATE_UPDATE (OWNER, ACCOUNTANT). The Phase A {@code POST /api/estimates/preview} is unchanged in
 * {@link EstimatePreviewController}. Nothing here posts money; conversion creates an F05 DRAFT planning budget only.
 */
@RestController
@RequestMapping("/api/estimates")
public class EstimateController {
    private final BusinessAuthorizationService authorization;
    private final EstimateService service;
    private final EstimateConversionService conversions;

    public EstimateController(BusinessAuthorizationService authorization, EstimateService service, EstimateConversionService conversions) {
        this.authorization = authorization;
        this.service = service;
        this.conversions = conversions;
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<?> list(@RequestParam(value = "status", required = false) String status,
                                  @RequestParam(value = "includeArchived", defaultValue = "false") boolean includeArchived,
                                  HttpServletRequest request) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return ok(service.list(context, parseStatus(status), includeArchived));
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> create(HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) return tooLarge();
        return created(service.create(context, EstimateRequestReader.readCreate(body)));
    }

    @GetMapping(value = "/{estimateId}", produces = "application/json")
    public ResponseEntity<?> get(@PathVariable("estimateId") UUID estimateId, HttpServletRequest request) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return ok(service.get(context, estimateId));
    }

    @PutMapping(value = "/{estimateId}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> update(@PathVariable("estimateId") UUID estimateId, HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) return tooLarge();
        return ok(service.update(context, estimateId, EstimateRequestReader.readUpdate(body)));
    }

    @DeleteMapping("/{estimateId}")
    public ResponseEntity<Void> delete(@PathVariable("estimateId") UUID estimateId,
                                       @RequestParam(value = "expectedVersion", required = false) Long expectedVersion,
                                       HttpServletRequest request) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        service.delete(context, estimateId, expectedVersion);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @PostMapping(value = "/{estimateId}/archive", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> archive(@PathVariable("estimateId") UUID estimateId, HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) return tooLarge();
        return ok(service.archive(context, estimateId, EstimateRequestReader.readTransition(body)));
    }

    @GetMapping(value = "/{estimateId}/versions", produces = "application/json")
    public ResponseEntity<?> listVersions(@PathVariable("estimateId") UUID estimateId, HttpServletRequest request) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return ok(service.listRevisions(context, estimateId));
    }

    @GetMapping(value = "/{estimateId}/versions/{versionId}", produces = "application/json")
    public ResponseEntity<?> getVersion(@PathVariable("estimateId") UUID estimateId, @PathVariable("versionId") UUID versionId,
                                        HttpServletRequest request) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return ok(service.getRevision(context, estimateId, versionId));
    }

    @PostMapping(value = "/{estimateId}/versions", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createVersion(@PathVariable("estimateId") UUID estimateId, HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) return tooLarge();
        return created(service.createRevision(context, estimateId, EstimateRequestReader.readRevision(body)));
    }

    @PutMapping(value = "/{estimateId}/versions/{versionId}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> updateVersion(@PathVariable("estimateId") UUID estimateId, @PathVariable("versionId") UUID versionId,
                                           HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) return tooLarge();
        return ok(service.updateRevision(context, estimateId, versionId, EstimateRequestReader.readRevision(body)));
    }

    @DeleteMapping("/{estimateId}/versions/{versionId}")
    public ResponseEntity<Void> deleteVersion(@PathVariable("estimateId") UUID estimateId, @PathVariable("versionId") UUID versionId,
                                              @RequestParam(value = "expectedVersion", required = false) Long expectedVersion,
                                              HttpServletRequest request) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        service.deleteRevision(context, estimateId, versionId, expectedVersion);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @PostMapping(value = "/{estimateId}/versions/{versionId}/approve", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> approve(@PathVariable("estimateId") UUID estimateId, @PathVariable("versionId") UUID versionId,
                                     HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) return tooLarge();
        return ok(service.approve(context, estimateId, versionId, EstimateRequestReader.readTransition(body)));
    }

    @PostMapping(value = "/{estimateId}/versions/{versionId}/convert-to-project", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> convert(@PathVariable("estimateId") UUID estimateId, @PathVariable("versionId") UUID versionId,
                                     HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) return tooLarge();
        var outcome = conversions.convert(context, estimateId, versionId, EstimateRequestReader.readConversion(body));
        return outcome.created() ? created(outcome.body()) : ok(outcome.body());
    }

    @ExceptionHandler(EstimateValidationException.class)
    public ResponseEntity<Map<String, Object>> invalid(EstimateValidationException exception) {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body(Map.of(
                "status", 400, "error", "estimate_invalid", "message", exception.getMessage(), "errors", exception.errors()));
    }

    @ExceptionHandler(ConversionPrecisionException.class)
    public ResponseEntity<Map<String, Object>> unrepresentable(ConversionPrecisionException exception) {
        return ResponseEntity.status(exception.status()).header("Cache-Control", "no-store").body(Map.of(
                "status", exception.status(), "error", exception.code(), "message", exception.getMessage(),
                "estimateValid", true, "details", exception.issues()));
    }

    @ExceptionHandler(EstimateException.class)
    public ResponseEntity<Map<String, Object>> domain(EstimateException exception) {
        return ResponseEntity.status(exception.status()).header("Cache-Control", "no-store").body(Map.of(
                "status", exception.status(), "error", exception.code(), "message", exception.getMessage()));
    }

    /** F05 keeps its own authority during conversion; its stable codes pass through unchanged. */
    @ExceptionHandler(ProjectException.class)
    public ResponseEntity<Map<String, Object>> project(ProjectException exception) {
        return ResponseEntity.status(exception.status()).header("Cache-Control", "no-store").body(Map.of(
                "status", exception.status(), "error", exception.code(), "message", exception.getMessage()));
    }

    @ExceptionHandler(ProjectValidationException.class)
    public ResponseEntity<Map<String, Object>> projectInvalid(ProjectValidationException exception) {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body(Map.of(
                "status", 400, "error", "project_invalid", "message", exception.getMessage(), "errors", exception.errors()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> typeMismatch(MethodArgumentTypeMismatchException exception) {
        String name = exception.getName() != null ? exception.getName() : "parameter";
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body(Map.of(
                "status", 400, "error", "estimate_invalid", "message", "Invalid request parameter.", "errors", Map.of(name, "invalid")));
    }

    private static <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().header("Cache-Control", "no-store").body(body); }
    private static <T> ResponseEntity<T> created(T body) {
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(body);
    }

    private static EstimateStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try { return EstimateStatus.valueOf(value.trim()); }
        catch (IllegalArgumentException exception) { throw new EstimateValidationException("status", "enum_invalid"); }
    }

    private static byte[] readBoundedBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > EstimatePreviewController.MAX_REQUEST_BYTES) return null;
        // Read at most limit + 1 even when Content-Length is missing or dishonest.
        byte[] body = request.getInputStream().readNBytes(EstimatePreviewController.MAX_REQUEST_BYTES + 1);
        return body.length > EstimatePreviewController.MAX_REQUEST_BYTES ? null : body;
    }

    private static ResponseEntity<Map<String, Object>> tooLarge() {
        return ResponseEntity.status(413).header("Cache-Control", "no-store").body(Map.of(
                "status", 413, "error", "estimate_request_too_large", "message", "The planning estimate request is too large."));
    }
}
