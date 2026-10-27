package com.app.sme_health_backend.projects.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.projects.dto.ProjectBudgetApprovalRequest;
import com.app.sme_health_backend.projects.dto.ProjectBudgetCalculationResponse;
import com.app.sme_health_backend.projects.dto.ProjectBudgetRequest;
import com.app.sme_health_backend.projects.dto.ProjectBudgetVersionResponse;
import com.app.sme_health_backend.projects.dto.ProjectCreateRequest;
import com.app.sme_health_backend.projects.dto.ProjectListResponse;
import com.app.sme_health_backend.projects.dto.ProjectPlanningSummaryResponse;
import com.app.sme_health_backend.projects.dto.ProjectResponse;
import com.app.sme_health_backend.projects.dto.ProjectTransitionRequest;
import com.app.sme_health_backend.projects.dto.ProjectUpdateRequest;
import com.app.sme_health_backend.projects.model.ProjectBudgetStatus;
import com.app.sme_health_backend.projects.model.ProjectBudgetVersionRecord;
import com.app.sme_health_backend.projects.model.ProjectBudgetVersionRecord.LineRecord;
import com.app.sme_health_backend.projects.model.ProjectKind;
import com.app.sme_health_backend.projects.model.ProjectRecord;
import com.app.sme_health_backend.projects.model.ProjectStatus;
import com.app.sme_health_backend.projects.repository.ProjectBudgetRepository;
import com.app.sme_health_backend.projects.repository.ProjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectPlanningService {
    public static final String ACTUAL_FINANCIALS_STATUS = "NOT_IMPLEMENTED_PHASE_A";

    private final ProjectRepository projectRepository;
    private final ProjectBudgetRepository budgetRepository;
    private final ProjectBudgetCalculator calculator;
    private final SecurityAuditService audit;

    public ProjectPlanningService(
            ProjectRepository projectRepository,
            ProjectBudgetRepository budgetRepository,
            ProjectBudgetCalculator calculator,
            SecurityAuditService audit
    ) {
        this.projectRepository = projectRepository;
        this.budgetRepository = budgetRepository;
        this.calculator = calculator;
        this.audit = audit;
    }

    @Transactional
    public ProjectResponse createProject(BusinessAccessContext context, ProjectCreateRequest request) {
        var metadata = calculator.validateCreateMetadata(request);
        projectRepository.lockActiveBusiness(context.businessId());
        if (projectRepository.existsByCode(context.businessId(), metadata.code(), null)) {
            throw new ProjectException("duplicate_project_code", 409, "A project, job, or order with this code already exists.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ProjectRecord record = new ProjectRecord(
                UUID.randomUUID(),
                context.businessId(),
                metadata.code(),
                metadata.name(),
                metadata.kind(),
                metadata.description(),
                ProjectStatus.DRAFT,
                metadata.startDate(),
                metadata.targetEndDate(),
                ProjectBudgetCalculator.BASE_CURRENCY,
                0L,
                context.userId(),
                context.userId(),
                now,
                now
        );
        try {
            projectRepository.insert(record);
        } catch (DataIntegrityViolationException ex) {
            throw new ProjectException("duplicate_project_code", 409, "A project, job, or order with this code already exists.");
        }

        audit.logSuccess(
                AuditEventType.PROJECT_CREATED,
                context.userId(),
                null,
                context.businessId(),
                "project",
                record.id().toString(),
                Map.of(
                        "code", record.code(),
                        "kind", record.kind().name(),
                        "status", record.status().name(),
                        "version", record.version()
                )
        );
        return ProjectResponse.fromRecord(record, null, null, 0, false);
    }

    @Transactional(readOnly = true)
    public ProjectListResponse listProjects(
            BusinessAccessContext context,
            ProjectStatus status,
            ProjectKind kind,
            String query,
            boolean includeArchived
    ) {
        List<ProjectRecord> projects = projectRepository.findAllByBusiness(
                context.businessId(),
                status,
                kind,
                query,
                includeArchived
        );
        List<UUID> projectIds = projects.stream().map(ProjectRecord::id).toList();
        Map<UUID, List<ProjectBudgetVersionRecord>> budgetsByProject =
                budgetRepository.findAllByProjectsOrderByRevisionDesc(context.businessId(), projectIds);

        List<ProjectResponse> items = projects.stream()
                .map(project -> assembleProjectResponse(
                        project,
                        budgetsByProject.getOrDefault(project.id(), List.of())
                ))
                .toList();

        return new ProjectListResponse(
                context.businessId(),
                ProjectBudgetCalculator.BASE_CURRENCY,
                ProjectBudgetCalculator.CURRENCY_BASIS,
                true,
                items
        );
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(BusinessAccessContext context, UUID projectId) {
        ProjectRecord project = projectRepository.findById(context.businessId(), projectId)
                .orElseThrow(() -> new ProjectException("project_not_found", 404, "Project not found."));
        List<ProjectBudgetVersionRecord> budgets =
                budgetRepository.findAllByProjectOrderByRevisionDesc(context.businessId(), projectId);
        return assembleProjectResponse(project, budgets);
    }

    @Transactional
    public ProjectResponse updateProject(BusinessAccessContext context, UUID projectId, ProjectUpdateRequest request) {
        var metadata = calculator.validateUpdateMetadata(request);
        // Lock Level 1 when code is checked across business, then Lock Level 2 on project:
        projectRepository.lockActiveBusiness(context.businessId());
        ProjectRecord current = projectRepository.findForUpdate(context.businessId(), projectId)
                .orElseThrow(() -> new ProjectException("project_not_found", 404, "Project not found."));

        if (!current.status().allowsPlanningMutations()) {
            throw new ProjectException(
                    "invalid_project_state",
                    409,
                    "Completed, cancelled, or archived projects cannot be edited."
            );
        }
        if (current.version() != request.expectedVersion()) {
            throw new ProjectException(
                    "stale_project_version",
                    409,
                    "This project changed since you loaded it. Reload before saving."
            );
        }
        if (projectRepository.existsByCode(context.businessId(), metadata.code(), projectId)) {
            throw new ProjectException("duplicate_project_code", 409, "A project, job, or order with this code already exists.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        boolean updated = projectRepository.updateMetadata(
                context.businessId(),
                projectId,
                request.expectedVersion(),
                metadata.code(),
                metadata.name(),
                metadata.kind(),
                metadata.description(),
                metadata.startDate(),
                metadata.targetEndDate(),
                context.userId(),
                now
        );
        if (!updated) {
            throw new ProjectException("stale_project_version", 409, "This project changed since you loaded it. Reload before saving.");
        }

        ProjectRecord reloaded = projectRepository.findById(context.businessId(), projectId).orElseThrow();
        List<ProjectBudgetVersionRecord> budgets =
                budgetRepository.findAllByProjectOrderByRevisionDesc(context.businessId(), projectId);

        audit.logSuccess(
                AuditEventType.PROJECT_UPDATED,
                context.userId(),
                null,
                context.businessId(),
                "project",
                projectId.toString(),
                Map.of(
                        "code", reloaded.code(),
                        "kind", reloaded.kind().name(),
                        "version", reloaded.version()
                )
        );
        return assembleProjectResponse(reloaded, budgets);
    }

    @Transactional
    public void deleteDraftProject(BusinessAccessContext context, UUID projectId, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new ProjectValidationException("expectedVersion", "version_invalid");
        }
        // Lock Level 2 (project) -> Lock Level 3 (budgets)
        ProjectRecord current = projectRepository.findForUpdate(context.businessId(), projectId)
                .orElseThrow(() -> new ProjectException("project_not_found", 404, "Project not found."));
        List<ProjectBudgetVersionRecord> budgets =
                budgetRepository.lockAllByProjectOrderByRevisionAsc(context.businessId(), projectId);

        boolean hasImmutableHistory = budgets.stream().anyMatch(b -> b.status().isImmutable());
        if (!current.status().canDeleteProject(hasImmutableHistory)) {
            throw new ProjectException(
                    "project_delete_forbidden",
                    409,
                    "Only DRAFT projects without approved budget history can be deleted; use CANCELLED or ARCHIVED instead."
            );
        }
        if (current.version() != expectedVersion) {
            throw new ProjectException(
                    "stale_project_version",
                    409,
                    "This project changed since you loaded it. Reload before deleting."
            );
        }

        budgetRepository.deleteAllDraftsForProject(context.businessId(), projectId);
        boolean deleted = projectRepository.deleteDraft(context.businessId(), projectId, expectedVersion);
        if (!deleted) {
            throw new ProjectException("stale_project_version", 409, "This project changed since you loaded it. Reload before deleting.");
        }

        audit.logSuccess(
                AuditEventType.PROJECT_DELETED,
                context.userId(),
                null,
                context.businessId(),
                "project",
                projectId.toString(),
                Map.of("code", current.code(), "deletedStatus", current.status().name())
        );
    }

    @Transactional
    public ProjectResponse transitionProject(
            BusinessAccessContext context,
            UUID projectId,
            ProjectTransitionRequest request
    ) {
        var transition = calculator.validateTransition(request);
        // Lock Level 2 (project) -> Lock Level 3 (budgets)
        ProjectRecord current = projectRepository.findForUpdate(context.businessId(), projectId)
                .orElseThrow(() -> new ProjectException("project_not_found", 404, "Project not found."));
        List<ProjectBudgetVersionRecord> budgets =
                budgetRepository.lockAllByProjectOrderByRevisionAsc(context.businessId(), projectId);

        if (current.version() != transition.expectedVersion()) {
            throw new ProjectException(
                    "stale_project_version",
                    409,
                    "This project changed since you loaded it. Reload before changing status."
            );
        }
        if (!current.status().canTransitionTo(transition.targetStatus())) {
            throw new ProjectException(
                    "invalid_project_transition",
                    409,
                    "Cannot transition project from " + current.status() + " to " + transition.targetStatus() + "."
            );
        }

        boolean hasOpenDraft = budgets.stream().anyMatch(b -> b.status() == ProjectBudgetStatus.DRAFT);
        if ((transition.targetStatus() == ProjectStatus.ARCHIVED || transition.targetStatus() == ProjectStatus.COMPLETED)
                && hasOpenDraft) {
            throw new ProjectException(
                    "open_draft_budget_exists",
                    409,
                    "Approve or discard the open draft budget revision before marking this project completed or archived."
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        boolean updated = projectRepository.updateStatus(
                context.businessId(),
                projectId,
                transition.expectedVersion(),
                transition.targetStatus(),
                context.userId(),
                now
        );
        if (!updated) {
            throw new ProjectException("stale_project_version", 409, "This project changed since you loaded it. Reload before changing status.");
        }

        ProjectRecord reloaded = projectRepository.findById(context.businessId(), projectId).orElseThrow();
        List<ProjectBudgetVersionRecord> orderedDesc =
                budgetRepository.findAllByProjectOrderByRevisionDesc(context.businessId(), projectId);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("fromStatus", current.status().name());
        auditDetails.put("toStatus", reloaded.status().name());
        auditDetails.put("version", reloaded.version());
        if (transition.reason() != null) {
            auditDetails.put("reason", transition.reason());
        }
        audit.logSuccess(
                AuditEventType.PROJECT_STATUS_TRANSITIONED,
                context.userId(),
                null,
                context.businessId(),
                "project",
                projectId.toString(),
                auditDetails
        );
        return assembleProjectResponse(reloaded, orderedDesc);
    }

    @Transactional
    public ProjectBudgetVersionResponse createDraftBudget(
            BusinessAccessContext context,
            UUID projectId,
            ProjectBudgetRequest request
    ) {
        if (request == null) {
            throw new ProjectValidationException("request", "required");
        }
        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new ProjectValidationException("expectedProjectVersion", "version_invalid");
        }
        ProjectBudgetCalculationResponse calculated =
                calculator.calculate(context.businessId(), request.plannedRevenue(), request.lines());

        // Lock Level 2 (project) -> Lock Level 3 (budgets)
        ProjectRecord project = projectRepository.findForUpdate(context.businessId(), projectId)
                .orElseThrow(() -> new ProjectException("project_not_found", 404, "Project not found."));
        if (!project.status().allowsPlanningMutations()) {
            throw new ProjectException(
                    "invalid_project_state",
                    409,
                    "Budgets cannot be created on completed, cancelled, or archived projects."
            );
        }
        if (project.version() != request.expectedVersion()) {
            throw new ProjectException(
                    "stale_project_version",
                    409,
                    "This project changed since you loaded it. Reload before creating a budget revision."
            );
        }

        List<ProjectBudgetVersionRecord> existingBudgets =
                budgetRepository.lockAllByProjectOrderByRevisionAsc(context.businessId(), projectId);
        if (existingBudgets.stream().anyMatch(b -> b.status() == ProjectBudgetStatus.DRAFT)) {
            throw new ProjectException(
                    "draft_budget_already_exists",
                    409,
                    "An open draft budget revision already exists for this project. Edit, approve, or discard that draft first."
            );
        }

        int nextRevision = existingBudgets.stream()
                .mapToInt(ProjectBudgetVersionRecord::revision)
                .max()
                .orElse(0) + 1;

        UUID budgetVersionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        List<LineRecord> lineRecords = calculated.lines().stream().map(line -> new LineRecord(
                UUID.randomUUID(),
                context.businessId(),
                projectId,
                budgetVersionId,
                line.lineNumber(),
                line.category(),
                line.description(),
                line.rawQuantity(),
                line.unit(),
                line.rawUnitCost(),
                line.rawPlannedLineCost()
        )).toList();

        ProjectBudgetVersionRecord draftRecord = new ProjectBudgetVersionRecord(
                budgetVersionId,
                context.businessId(),
                projectId,
                nextRevision,
                ProjectBudgetStatus.DRAFT,
                ProjectBudgetCalculator.BASE_CURRENCY,
                calculated.rawTotals().plannedRevenue(),
                calculated.rawTotals().plannedCost(),
                calculated.rawTotals().plannedMargin(),
                calculated.rawTotals().plannedMarginPct(),
                calculated.unavailableReasons().plannedMarginPct(),
                ProjectBudgetCalculator.CALCULATION_VERSION,
                null,
                0L,
                context.userId(),
                context.userId(),
                now,
                now,
                null,
                null,
                null,
                null,
                lineRecords
        );

        try {
            budgetRepository.insertDraft(draftRecord);
        } catch (DataIntegrityViolationException ex) {
            throw new ProjectException(
                    "draft_budget_already_exists",
                    409,
                    "An open draft budget revision already exists for this project."
            );
        }
        projectRepository.incrementVersion(context.businessId(), projectId, context.userId(), now);

        audit.logSuccess(
                AuditEventType.PROJECT_BUDGET_CREATED,
                context.userId(),
                null,
                context.businessId(),
                "project_budget_version",
                budgetVersionId.toString(),
                Map.of(
                        "projectId", projectId.toString(),
                        "revision", nextRevision,
                        "status", ProjectBudgetStatus.DRAFT.name(),
                        "plannedRevenue", calculated.plannedRevenue(),
                        "plannedCost", calculated.plannedCost()
                )
        );
        return ProjectBudgetVersionResponse.fromRecord(draftRecord);
    }

    @Transactional
    public ProjectBudgetVersionResponse updateDraftBudget(
            BusinessAccessContext context,
            UUID projectId,
            UUID budgetId,
            ProjectBudgetRequest request
    ) {
        if (request == null) {
            throw new ProjectValidationException("request", "required");
        }
        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new ProjectValidationException("expectedBudgetVersion", "version_invalid");
        }
        ProjectBudgetCalculationResponse calculated =
                calculator.calculate(context.businessId(), request.plannedRevenue(), request.lines());

        // Lock Level 2 (project) -> Lock Level 3 (budgets)
        ProjectRecord project = projectRepository.findForUpdate(context.businessId(), projectId)
                .orElseThrow(() -> new ProjectException("project_not_found", 404, "Project not found."));
        if (!project.status().allowsPlanningMutations()) {
            throw new ProjectException(
                    "invalid_project_state",
                    409,
                    "Budgets cannot be edited on completed, cancelled, or archived projects."
            );
        }

        List<ProjectBudgetVersionRecord> budgets =
                budgetRepository.lockAllByProjectOrderByRevisionAsc(context.businessId(), projectId);
        ProjectBudgetVersionRecord target = budgets.stream()
                .filter(b -> b.id().equals(budgetId))
                .findFirst()
                .orElseThrow(() -> new ProjectException("budget_not_found", 404, "Budget version not found."));

        if (target.status().isImmutable()) {
            throw new ProjectException(
                    "budget_immutable",
                    409,
                    "Approved and superseded budget versions are immutable. Create a new budget revision instead."
            );
        }
        if (target.version() != request.expectedVersion()) {
            throw new ProjectException(
                    "stale_budget_version",
                    409,
                    "This draft budget changed since you loaded it. Reload before saving."
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<LineRecord> lineRecords = calculated.lines().stream().map(line -> new LineRecord(
                UUID.randomUUID(),
                context.businessId(),
                projectId,
                budgetId,
                line.lineNumber(),
                line.category(),
                line.description(),
                line.rawQuantity(),
                line.unit(),
                line.rawUnitCost(),
                line.rawPlannedLineCost()
        )).toList();

        boolean replaced = budgetRepository.replaceDraft(
                context.businessId(),
                projectId,
                budgetId,
                request.expectedVersion(),
                calculated.rawTotals().plannedRevenue(),
                calculated.rawTotals().plannedCost(),
                calculated.rawTotals().plannedMargin(),
                calculated.rawTotals().plannedMarginPct(),
                calculated.unavailableReasons().plannedMarginPct(),
                lineRecords,
                context.userId(),
                now
        );
        if (!replaced) {
            throw new ProjectException(
                    "stale_budget_version",
                    409,
                    "This draft budget changed since you loaded it. Reload before saving."
            );
        }
        projectRepository.incrementVersion(context.businessId(), projectId, context.userId(), now);

        ProjectBudgetVersionRecord updatedRecord = budgetRepository
                .findAllByProjectOrderByRevisionDesc(context.businessId(), projectId)
                .stream()
                .filter(b -> b.id().equals(budgetId))
                .findFirst()
                .orElseThrow();

        audit.logSuccess(
                AuditEventType.PROJECT_BUDGET_UPDATED,
                context.userId(),
                null,
                context.businessId(),
                "project_budget_version",
                budgetId.toString(),
                Map.of(
                        "projectId", projectId.toString(),
                        "revision", updatedRecord.revision(),
                        "version", updatedRecord.version(),
                        "plannedRevenue", calculated.plannedRevenue(),
                        "plannedCost", calculated.plannedCost()
                )
        );
        return ProjectBudgetVersionResponse.fromRecord(updatedRecord);
    }

    @Transactional
    public void deleteDraftBudget(
            BusinessAccessContext context,
            UUID projectId,
            UUID budgetId,
            Long expectedProjectVersion,
            Long expectedBudgetVersion
    ) {
        if (expectedProjectVersion == null || expectedProjectVersion < 0) {
            throw new ProjectValidationException("expectedProjectVersion", "version_invalid");
        }
        if (expectedBudgetVersion == null || expectedBudgetVersion < 0) {
            throw new ProjectValidationException("expectedBudgetVersion", "version_invalid");
        }
        // Lock Level 2 (project) -> Lock Level 3 (budgets)
        ProjectRecord project = projectRepository.findForUpdate(context.businessId(), projectId)
                .orElseThrow(() -> new ProjectException("project_not_found", 404, "Project not found."));
        if (!project.status().allowsPlanningMutations()) {
            throw new ProjectException(
                    "invalid_project_state",
                    409,
                    "Budgets cannot be modified on completed, cancelled, or archived projects."
            );
        }
        if (project.version() != expectedProjectVersion) {
            throw new ProjectException(
                    "stale_project_version",
                    409,
                    "This project changed since you loaded it. Reload before discarding the draft budget."
            );
        }

        List<ProjectBudgetVersionRecord> budgets =
                budgetRepository.lockAllByProjectOrderByRevisionAsc(context.businessId(), projectId);
        ProjectBudgetVersionRecord target = budgets.stream()
                .filter(b -> b.id().equals(budgetId))
                .findFirst()
                .orElseThrow(() -> new ProjectException("budget_not_found", 404, "Budget version not found."));

        if (!target.status().canDeleteDraft()) {
            throw new ProjectException(
                    "budget_immutable",
                    409,
                    "Approved and superseded budget versions are permanent history and cannot be deleted."
            );
        }
        if (target.version() != expectedBudgetVersion) {
            throw new ProjectException(
                    "stale_budget_version",
                    409,
                    "This draft budget changed since you loaded it. Reload before discarding."
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        boolean deleted = budgetRepository.deleteDraft(context.businessId(), projectId, budgetId, expectedBudgetVersion);
        if (!deleted) {
            throw new ProjectException("stale_budget_version", 409, "This draft budget changed since you loaded it. Reload before discarding.");
        }
        projectRepository.incrementVersion(context.businessId(), projectId, context.userId(), now);

        audit.logSuccess(
                AuditEventType.PROJECT_BUDGET_DELETED,
                context.userId(),
                null,
                context.businessId(),
                "project_budget_version",
                budgetId.toString(),
                Map.of(
                        "projectId", projectId.toString(),
                        "revision", target.revision(),
                        "deletedStatus", target.status().name()
                )
        );
    }

    @Transactional
    public ProjectBudgetVersionResponse approveBudget(
            BusinessAccessContext context,
            UUID projectId,
            UUID budgetId,
            ProjectBudgetApprovalRequest request
    ) {
        if (request == null) {
            throw new ProjectValidationException("request", "required");
        }
        if (request.expectedProjectVersion() == null || request.expectedProjectVersion() < 0) {
            throw new ProjectValidationException("expectedProjectVersion", "version_invalid");
        }
        if (request.expectedBudgetVersion() == null || request.expectedBudgetVersion() < 0) {
            throw new ProjectValidationException("expectedBudgetVersion", "version_invalid");
        }

        // Lock Level 2 (project) -> Lock Level 3 (budgets ordered by revision ASC, id ASC)
        ProjectRecord project = projectRepository.findForUpdate(context.businessId(), projectId)
                .orElseThrow(() -> new ProjectException("project_not_found", 404, "Project not found."));
        if (!project.status().allowsPlanningMutations()) {
            throw new ProjectException(
                    "invalid_project_state",
                    409,
                    "Budgets cannot be approved on completed, cancelled, or archived projects."
            );
        }
        if (project.version() != request.expectedProjectVersion()) {
            throw new ProjectException(
                    "stale_project_version",
                    409,
                    "This project changed since you loaded it. Reload before approving the budget."
            );
        }

        List<ProjectBudgetVersionRecord> budgets =
                budgetRepository.lockAllByProjectOrderByRevisionAsc(context.businessId(), projectId);
        ProjectBudgetVersionRecord target = budgets.stream()
                .filter(b -> b.id().equals(budgetId))
                .findFirst()
                .orElseThrow(() -> new ProjectException("budget_not_found", 404, "Budget version not found."));

        if (target.status() != ProjectBudgetStatus.DRAFT) {
            throw new ProjectException(
                    "invalid_budget_transition",
                    409,
                    "Only a DRAFT budget version can be approved."
            );
        }
        if (target.version() != request.expectedBudgetVersion()) {
            throw new ProjectException(
                    "stale_budget_version",
                    409,
                    "This draft budget changed since you loaded it. Reload before approving."
            );
        }

        ProjectBudgetVersionRecord currentApproved = budgets.stream()
                .filter(b -> b.status() == ProjectBudgetStatus.APPROVED)
                .findFirst()
                .orElse(null);

        OffsetDateTime now = OffsetDateTime.now();
        // Step 1: Supersede prior APPROVED row first so uq_project_budget_versions_one_approved is never violated.
        if (currentApproved != null) {
            boolean superseded = budgetRepository.supersedeApproved(
                    context.businessId(),
                    projectId,
                    currentApproved.id(),
                    target.id(),
                    context.userId(),
                    now
            );
            if (!superseded) {
                throw new ProjectException("stale_budget_version", 409, "Budget state changed concurrently. Reload before approving.");
            }
        }

        // Step 2: Approve target DRAFT row.
        boolean approved = budgetRepository.approveDraft(
                context.businessId(),
                projectId,
                target.id(),
                request.expectedBudgetVersion(),
                context.userId(),
                now
        );
        if (!approved) {
            throw new ProjectException("stale_budget_version", 409, "This draft budget changed since you loaded it. Reload before approving.");
        }

        // Step 3: Increment parent project version.
        projectRepository.incrementVersion(context.businessId(), projectId, context.userId(), now);

        ProjectBudgetVersionRecord approvedRecord = budgetRepository
                .findAllByProjectOrderByRevisionDesc(context.businessId(), projectId)
                .stream()
                .filter(b -> b.id().equals(target.id()))
                .findFirst()
                .orElseThrow();
        ProjectBudgetVersionResponse response = ProjectBudgetVersionResponse.fromRecord(approvedRecord);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("projectId", projectId.toString());
        auditDetails.put("revision", approvedRecord.revision());
        auditDetails.put("version", approvedRecord.version());
        auditDetails.put("plannedRevenue", response.plannedRevenue());
        auditDetails.put("plannedCost", response.plannedCost());
        auditDetails.put("plannedMargin", response.plannedMargin());
        if (currentApproved != null) {
            auditDetails.put("supersededBudgetId", currentApproved.id().toString());
            auditDetails.put("supersededRevision", currentApproved.revision());
        }
        audit.logSuccess(
                AuditEventType.PROJECT_BUDGET_APPROVED,
                context.userId(),
                null,
                context.businessId(),
                "project_budget_version",
                target.id().toString(),
                auditDetails
        );
        return response;
    }

    @Transactional(readOnly = true)
    public List<ProjectBudgetVersionResponse> listBudgets(BusinessAccessContext context, UUID projectId) {
        projectRepository.findById(context.businessId(), projectId)
                .orElseThrow(() -> new ProjectException("project_not_found", 404, "Project not found."));
        return budgetRepository.findAllByProjectOrderByRevisionDesc(context.businessId(), projectId)
                .stream()
                .map(ProjectBudgetVersionResponse::fromRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectPlanningSummaryResponse getPlanningSummary(BusinessAccessContext context, UUID projectId) {
        ProjectRecord project = projectRepository.findById(context.businessId(), projectId)
                .orElseThrow(() -> new ProjectException("project_not_found", 404, "Project not found."));
        List<ProjectBudgetVersionRecord> budgets =
                budgetRepository.findAllByProjectOrderByRevisionDesc(context.businessId(), projectId);

        ProjectResponse projectResponse = assembleProjectResponse(project, budgets);
        int supersededCount = (int) budgets.stream()
                .filter(b -> b.status() == ProjectBudgetStatus.SUPERSEDED)
                .count();
        String budgetStatusReason = projectResponse.activeApprovedBudget() != null
                ? null
                : (projectResponse.latestDraftBudget() != null ? "DRAFT_NOT_YET_APPROVED" : "NO_BUDGET_DEFINED");

        return new ProjectPlanningSummaryResponse(
                context.businessId(),
                true,
                ProjectBudgetCalculator.BASE_CURRENCY,
                ProjectBudgetCalculator.CURRENCY_BASIS,
                ACTUAL_FINANCIALS_STATUS,
                projectResponse,
                projectResponse.activeApprovedBudget(),
                projectResponse.latestDraftBudget(),
                budgetStatusReason,
                budgets.size(),
                supersededCount,
                new ProjectBudgetCalculationResponse.Calculation(
                        ProjectBudgetCalculator.CALCULATION_VERSION,
                        ProjectDecimals.DIVISION_SCALE,
                        ProjectDecimals.DIVISION_ROUNDING.name(),
                        null
                )
        );
    }

    private ProjectResponse assembleProjectResponse(
            ProjectRecord project,
            List<ProjectBudgetVersionRecord> budgets
    ) {
        ProjectBudgetVersionResponse activeApproved = budgets.stream()
                .filter(b -> b.status() == ProjectBudgetStatus.APPROVED)
                .findFirst()
                .map(ProjectBudgetVersionResponse::fromRecord)
                .orElse(null);
        ProjectBudgetVersionResponse latestDraft = budgets.stream()
                .filter(b -> b.status() == ProjectBudgetStatus.DRAFT)
                .findFirst()
                .map(ProjectBudgetVersionResponse::fromRecord)
                .orElse(null);
        boolean hasImmutableHistory = budgets.stream().anyMatch(b -> b.status().isImmutable());
        return ProjectResponse.fromRecord(project, activeApproved, latestDraft, budgets.size(), hasImmutableHistory);
    }
}
