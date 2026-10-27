package com.app.sme_health_backend.projects.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.projects.dto.ProjectBudgetVersionResponse;
import com.app.sme_health_backend.projects.dto.ProjectListResponse;
import com.app.sme_health_backend.projects.dto.ProjectPlanningSummaryResponse;
import com.app.sme_health_backend.projects.dto.ProjectResponse;
import com.app.sme_health_backend.projects.model.ProjectKind;
import com.app.sme_health_backend.projects.model.ProjectStatus;
import com.app.sme_health_backend.projects.service.ProjectException;
import com.app.sme_health_backend.projects.service.ProjectPlanningService;
import com.app.sme_health_backend.projects.service.ProjectValidationException;
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
@RequestMapping("/api/projects")
public class ProjectController {
    private final BusinessAuthorizationService authorization;
    private final ProjectPlanningService service;

    public ProjectController(BusinessAuthorizationService authorization, ProjectPlanningService service) {
        this.authorization = authorization;
        this.service = service;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createProject(HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        ProjectResponse created = service.createProject(context, ProjectRequestReader.readCreate(body));
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(created);
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<ProjectListResponse> listProjects(
            @RequestParam(value = "status", required = false) String statusParam,
            @RequestParam(value = "kind", required = false) String kindParam,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "includeArchived", defaultValue = "false") boolean includeArchived,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        ProjectStatus status = parseEnumParam(ProjectStatus.class, statusParam, "status");
        ProjectKind kind = parseEnumParam(ProjectKind.class, kindParam, "kind");
        ProjectListResponse response = service.listProjects(context, status, kind, query, includeArchived);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(response);
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<ProjectResponse> getProject(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        ProjectResponse response = service.getProject(context, id);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(response);
    }

    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> updateProject(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        ProjectResponse updated = service.updateProject(context, id, ProjectRequestReader.readUpdate(body));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(updated);
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deleteDraftProject(
            @PathVariable("id") UUID id,
            @RequestParam(value = "expectedVersion", required = false) Long expectedVersion,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        service.deleteDraftProject(context, id, expectedVersion);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @PostMapping(value = "/{id}/transition", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> transitionProject(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        ProjectResponse updated = service.transitionProject(context, id, ProjectRequestReader.readTransition(body));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(updated);
    }

    @PostMapping(value = "/{id}/budgets", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createDraftBudget(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        ProjectBudgetVersionResponse created = service.createDraftBudget(context, id, ProjectRequestReader.readBudgetCreate(body));
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(created);
    }

    @PutMapping(value = "/{id}/budgets/{budgetId}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> updateDraftBudget(
            @PathVariable("id") UUID id,
            @PathVariable("budgetId") UUID budgetId,
            HttpServletRequest request
    ) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        ProjectBudgetVersionResponse updated = service.updateDraftBudget(
                context,
                id,
                budgetId,
                ProjectRequestReader.readBudgetUpdate(body)
        );
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(updated);
    }

    @DeleteMapping(value = "/{id}/budgets/{budgetId}")
    public ResponseEntity<Void> deleteDraftBudget(
            @PathVariable("id") UUID id,
            @PathVariable("budgetId") UUID budgetId,
            @RequestParam(value = "expectedProjectVersion", required = false) Long expectedProjectVersion,
            @RequestParam(value = "expectedBudgetVersion", required = false) Long expectedBudgetVersion,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        service.deleteDraftBudget(context, id, budgetId, expectedProjectVersion, expectedBudgetVersion);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @PostMapping(value = "/{id}/budgets/{budgetId}/approve", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> approveBudget(
            @PathVariable("id") UUID id,
            @PathVariable("budgetId") UUID budgetId,
            HttpServletRequest request
    ) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        ProjectBudgetVersionResponse approved = service.approveBudget(
                context,
                id,
                budgetId,
                ProjectRequestReader.readBudgetApproval(body)
        );
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(approved);
    }

    @GetMapping(value = "/{id}/budgets", produces = "application/json")
    public ResponseEntity<List<ProjectBudgetVersionResponse>> listBudgets(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        List<ProjectBudgetVersionResponse> budgets = service.listBudgets(context, id);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(budgets);
    }

    @GetMapping(value = "/{id}/planning-summary", produces = "application/json")
    public ResponseEntity<ProjectPlanningSummaryResponse> getPlanningSummary(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        ProjectPlanningSummaryResponse summary = service.getPlanningSummary(context, id);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(summary);
    }

    @ExceptionHandler(ProjectValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ProjectValidationException ex) {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body(Map.of(
                "status", 400,
                "error", "project_invalid",
                "message", ex.getMessage(),
                "errors", ex.errors()
        ));
    }

    @ExceptionHandler(ProjectException.class)
    public ResponseEntity<Map<String, Object>> handleDomainException(ProjectException ex) {
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
                "error", "project_invalid",
                "message", "Invalid request parameter.",
                "errors", Map.of(name, "invalid")
        ));
    }

    private static byte[] readBoundedBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > ProjectRequestReader.MAX_REQUEST_BYTES) {
            return null;
        }
        byte[] body = request.getInputStream().readNBytes(ProjectRequestReader.MAX_REQUEST_BYTES + 1);
        if (body.length > ProjectRequestReader.MAX_REQUEST_BYTES) {
            return null;
        }
        return body;
    }

    private static ResponseEntity<Map<String, Object>> tooLarge() {
        return ResponseEntity.status(413).header("Cache-Control", "no-store").body(Map.of(
                "status", 413,
                "error", "project_request_too_large",
                "message", "The project planning request is too large."
        ));
    }

    private static <E extends Enum<E>> E parseEnumParam(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException ex) {
            throw new ProjectValidationException(field, "enum_invalid");
        }
    }
}
