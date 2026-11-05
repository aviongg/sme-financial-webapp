package com.app.sme_health_backend.samples.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.samples.dto.SamplePlanVersionResponse;
import com.app.sme_health_backend.samples.dto.SampleListResponse;
import com.app.sme_health_backend.samples.dto.SamplePlanningSummaryResponse;
import com.app.sme_health_backend.samples.dto.SampleResponse;
import com.app.sme_health_backend.samples.model.SampleKind;
import com.app.sme_health_backend.samples.model.SampleStatus;
import com.app.sme_health_backend.samples.service.SampleException;
import com.app.sme_health_backend.samples.service.SamplePlanningService;
import com.app.sme_health_backend.samples.service.SampleValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/samples")
public class SampleController {
    private final BusinessAuthorizationService authorization;
    private final SamplePlanningService service;

    public SampleController(BusinessAuthorizationService authorization, SamplePlanningService service) {
        this.authorization = authorization;
        this.service = service;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createActivity(HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        SampleResponse created = service.createActivity(context, SampleRequestReader.readCreate(body));
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(created);
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<SampleListResponse> listActivities(
            @RequestParam(value = "status", required = false) String statusParam,
            @RequestParam(value = "kind", required = false) String kindParam,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "includeArchived", defaultValue = "false") boolean includeArchived,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        SampleStatus status = parseEnumParam(SampleStatus.class, statusParam, "status");
        SampleKind kind = parseEnumParam(SampleKind.class, kindParam, "kind");
        SampleListResponse response = service.listActivities(context, status, kind, query, includeArchived);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(response);
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<SampleResponse> getActivity(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        SampleResponse response = service.getActivity(context, id);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(response);
    }

    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> updateActivity(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        SampleResponse updated = service.updateActivity(context, id, SampleRequestReader.readUpdate(body));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(updated);
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deleteDraftActivity(
            @PathVariable("id") UUID id,
            @RequestParam(value = "expectedVersion", required = false) Long expectedVersion,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        service.deleteDraftActivity(context, id, expectedVersion);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @PostMapping(value = "/{id}/transition", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> transitionActivity(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        SampleResponse updated = service.transitionActivity(context, id, SampleRequestReader.readTransition(body));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(updated);
    }

    @PostMapping(value = "/{id}/plans", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createDraftPlan(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        SamplePlanVersionResponse created = service.createDraftPlan(context, id, SampleRequestReader.readPlanCreate(body));
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(created);
    }

    @PutMapping(value = "/{id}/plans/{planId}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> updateDraftPlan(
            @PathVariable("id") UUID id,
            @PathVariable("planId") UUID planId,
            HttpServletRequest request
    ) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        SamplePlanVersionResponse updated = service.updateDraftPlan(
                context,
                id,
                planId,
                SampleRequestReader.readPlanUpdate(body)
        );
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(updated);
    }

    @DeleteMapping(value = "/{id}/plans/{planId}")
    public ResponseEntity<Void> deleteDraftPlan(
            @PathVariable("id") UUID id,
            @PathVariable("planId") UUID planId,
            @RequestParam(value = "expectedActivityVersion", required = false) Long expectedActivityVersion,
            @RequestParam(value = "expectedPlanVersion", required = false) Long expectedPlanVersion,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        service.deleteDraftPlan(context, id, planId, expectedActivityVersion, expectedPlanVersion);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @PostMapping(value = "/{id}/plans/{planId}/approve", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> approvePlan(
            @PathVariable("id") UUID id,
            @PathVariable("planId") UUID planId,
            HttpServletRequest request
    ) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        SamplePlanVersionResponse approved = service.approvePlan(
                context,
                id,
                planId,
                SampleRequestReader.readPlanApproval(body)
        );
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(approved);
    }

    @GetMapping(value = "/{id}/plans", produces = "application/json")
    public ResponseEntity<List<SamplePlanVersionResponse>> listPlans(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        List<SamplePlanVersionResponse> plans = service.listPlans(context, id);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(plans);
    }

    @GetMapping(value = "/{id}/plans/{planId}", produces = "application/json")
    public ResponseEntity<SamplePlanVersionResponse> getPlan(@PathVariable("id") UUID id,
            @PathVariable("planId") UUID planId, HttpServletRequest request) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.getPlan(context, id, planId));
    }

    @GetMapping(value = "/{id}/planning-summary", produces = "application/json")
    public ResponseEntity<SamplePlanningSummaryResponse> getPlanningSummary(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        SamplePlanningSummaryResponse summary = service.getPlanningSummary(context, id);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(summary);
    }

    @ExceptionHandler(SampleValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(SampleValidationException ex) {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body(Map.of(
                "status", 400,
                "error", "activity_invalid",
                "message", ex.getMessage(),
                "errors", ex.errors()
        ));
    }

    @ExceptionHandler(SampleException.class)
    public ResponseEntity<Map<String, Object>> handleDomainException(SampleException ex) {
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
                "error", "activity_invalid",
                "message", "Invalid request parameter.",
                "errors", Map.of(name, "invalid")
        ));
    }

    private static byte[] readBoundedBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > SampleRequestReader.MAX_REQUEST_BYTES) {
            return null;
        }
        byte[] body = request.getInputStream().readNBytes(SampleRequestReader.MAX_REQUEST_BYTES + 1);
        if (body.length > SampleRequestReader.MAX_REQUEST_BYTES) {
            return null;
        }
        return body;
    }

    private static ResponseEntity<Map<String, Object>> tooLarge() {
        return ResponseEntity.status(413).header("Cache-Control", "no-store").body(Map.of(
                "status", 413,
                "error", "activity_request_too_large",
                "message", "The activity planning request is too large."
        ));
    }

    private static <E extends Enum<E>> E parseEnumParam(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException ex) {
            throw new SampleValidationException(field, "enum_invalid");
        }
    }
}
