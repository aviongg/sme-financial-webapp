package com.app.sme_health_backend.estimates.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.estimates.dto.EstimateConversionRequest;
import com.app.sme_health_backend.estimates.dto.EstimateConversionResponse;
import com.app.sme_health_backend.estimates.dto.EstimateConversionResultResponse;
import com.app.sme_health_backend.estimates.model.EstimateConversionRecord;
import com.app.sme_health_backend.estimates.model.EstimateRecord;
import com.app.sme_health_backend.estimates.model.EstimateRevisionRecord;
import com.app.sme_health_backend.estimates.model.EstimateRevisionStatus;
import com.app.sme_health_backend.estimates.repository.EstimateRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.projects.dto.ProjectBudgetLineRequest;
import com.app.sme_health_backend.projects.dto.ProjectBudgetVersionResponse;
import com.app.sme_health_backend.projects.model.ProjectBudgetStatus;
import com.app.sme_health_backend.projects.model.ProjectRecord;
import com.app.sme_health_backend.projects.repository.ProjectBudgetRepository;
import com.app.sme_health_backend.projects.repository.ProjectRepository;
import com.app.sme_health_backend.projects.service.ProjectBudgetCalculator;
import com.app.sme_health_backend.projects.service.ProjectPlanningService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Converts one APPROVED estimate revision into a DRAFT planning budget on an existing F05 project.
 * F05's {@link ProjectPlanningService} creates the budget under its own locks and invariants; this service adds only
 * the exact-fit gate and immutable lineage. Nothing is posted and the F05 budget stays PLANNED_ONLY.
 * Lock order is always estimate row, then estimate revisions, then (inside F05) project row, then project budgets.
 */
@Service
public class EstimateConversionService {
    public static final String CONTRACT_VERSION = "f06-f05-exact-v1";

    private final EstimateRepository repository;
    private final EstimateService estimates;
    private final ProjectRepository projects;
    private final ProjectBudgetRepository budgets;
    private final ProjectPlanningService planning;
    private final SecurityAuditService audit;

    public EstimateConversionService(EstimateRepository repository, EstimateService estimates, ProjectRepository projects,
                                     ProjectBudgetRepository budgets, ProjectPlanningService planning, SecurityAuditService audit) {
        this.repository = repository;
        this.estimates = estimates;
        this.projects = projects;
        this.budgets = budgets;
        this.planning = planning;
        this.audit = audit;
    }

    public record Outcome(boolean created, EstimateConversionResultResponse body) { }

    @Transactional
    public Outcome convert(BusinessAccessContext context, UUID estimateId, UUID revisionId, EstimateConversionRequest request) {
        if (request == null) throw new EstimateValidationException("request", "required");
        if (request.targetProjectId() == null) throw new EstimateValidationException("targetProjectId", "required");
        if (request.expectedProjectVersion() == null || request.expectedProjectVersion() < 0)
            throw new EstimateValidationException("expectedProjectVersion", "version_invalid");
        UUID businessId = context.businessId();

        // 1-4. Source estimate and revision in the authenticated business; only APPROVED converts.
        EstimateRecord estimate = estimates.find(context, estimateId);
        EstimateRevisionRecord source = repository.findRevision(businessId, estimateId, revisionId)
                .orElseThrow(EstimateService::revisionNotFound);
        // A replay is answered from lineage first, so the draft it created is not reported as a conflict.
        var existing = repository.findConversionByRevision(businessId, revisionId);
        if (existing.isPresent()) return replay(context, existing.get(), request.targetProjectId());
        requireConvertible(estimate, source);

        // 5-7. Target F05 project in the same business, open to planning, without an open DRAFT budget.
        ProjectRecord project = projects.findById(businessId, request.targetProjectId())
                .orElseThrow(() -> new EstimateException("project_not_found", 404, "Project not found."));
        if (!project.status().allowsPlanningMutations()) {
            throw new EstimateException("invalid_project_state", 409,
                    "Completed, cancelled, or archived projects cannot receive a planning budget.");
        }
        if (budgets.findAllByProjectOrderByRevisionDesc(businessId, project.id()).stream()
                .anyMatch(budget -> budget.status() == ProjectBudgetStatus.DRAFT)) {
            // Lineage commits with the draft it created, so a concurrent conversion of this same revision is visible now.
            existing = repository.findConversionByRevision(businessId, revisionId);
            if (existing.isPresent()) return replay(context, existing.get(), request.targetProjectId());
            throw new EstimateException("draft_budget_already_exists", 409,
                    "The project already has an open draft budget. Approve or discard it before converting an estimate.");
        }

        // 8. Exact F05 representability; the estimate itself stays valid when this fails.
        ProjectConversionValidator.Conversion conversion = ProjectConversionValidator.convert(source);

        // 10-11. Lock estimate then its revisions, and revalidate everything that could have changed.
        EstimateRecord locked = repository.lockEstimate(businessId, estimateId).orElseThrow(EstimateService::estimateNotFound);
        EstimateRevisionRecord current = repository.lockRevisions(businessId, estimateId).stream()
                .filter(revision -> revision.id().equals(revisionId)).findFirst().orElseThrow(EstimateService::revisionNotFound);
        // 9. One successful conversion per revision, checked again under the estimate lock.
        existing = repository.findConversionByRevision(businessId, revisionId);
        if (existing.isPresent()) return replay(context, existing.get(), request.targetProjectId());
        requireConvertible(locked, current);

        // 12. F05 creates the DRAFT budget with its own locks, version, lifecycle and one-draft checks.
        String sourceRef = "F06/" + locked.code() + "/R" + current.revision() + "/" + current.id();
        ProjectBudgetVersionResponse budget = planning.createDraftBudgetFromEstimate(context, project.id(),
                conversion.request(request.expectedProjectVersion()), sourceRef);
        requireExactCopy(current, conversion, budget);

        // 13. Immutable lineage; uniqueness constraints back the application checks.
        EstimateConversionRecord lineage = new EstimateConversionRecord(UUID.randomUUID(), businessId, estimateId, revisionId,
                project.id(), budget.id(), CONTRACT_VERSION, PricingCalculator.CALCULATION_VERSION,
                ProjectBudgetCalculator.CALCULATION_VERSION, context.userId(), OffsetDateTime.now());
        try {
            repository.insertConversion(lineage);
        } catch (DataIntegrityViolationException ex) {
            throw alreadyConverted();
        }
        // Respond with the stored row so a later replay returns the identical lineage (database timestamp precision).
        lineage = repository.findConversionByRevision(businessId, revisionId).orElseThrow();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("estimateId", estimateId.toString());
        details.put("revision", current.revision());
        details.put("projectId", project.id().toString());
        details.put("projectBudgetVersionId", budget.id().toString());
        details.put("plannedRevenue", budget.plannedRevenue());
        details.put("plannedCost", budget.plannedCost());
        details.put("conversionContractVersion", CONTRACT_VERSION);
        details.put("planningOnly", true);
        audit.logSuccess(AuditEventType.ESTIMATE_REVISION_CONVERTED, context.userId(), null, businessId, "estimate_version",
                revisionId.toString(), details);
        // 14.
        return new Outcome(true, result(false, lineage, budget));
    }

    private static void requireConvertible(EstimateRecord estimate, EstimateRevisionRecord revision) {
        if (!estimate.status().allowsChanges()) {
            throw new EstimateException("estimate_archived", 409, "Archived estimates are read-only and cannot be converted.");
        }
        if (revision.status() != EstimateRevisionStatus.APPROVED) {
            throw new EstimateException("revision_not_approved", 409, "Only the APPROVED estimate revision can be converted.");
        }
    }

    /** Same target: the original result, never a second budget. A different target conflicts. */
    private Outcome replay(BusinessAccessContext context, EstimateConversionRecord existing, UUID targetProjectId) {
        if (!existing.projectId().equals(targetProjectId)) throw alreadyConverted();
        ProjectBudgetVersionResponse budget = budgets.findAllByProjectOrderByRevisionDesc(context.businessId(), existing.projectId())
                .stream().filter(record -> record.id().equals(existing.projectBudgetVersionId())).findFirst()
                .map(ProjectBudgetVersionResponse::fromRecord).orElse(null);
        return new Outcome(false, result(true, existing, budget));
    }

    /** Post-condition inside the transaction: F05 must hold the identical numbers; any difference rolls back. */
    private static void requireExactCopy(EstimateRevisionRecord source, ProjectConversionValidator.Conversion conversion,
                                         ProjectBudgetVersionResponse budget) {
        boolean exact = budget.status() == ProjectBudgetStatus.DRAFT && budget.lines().size() == source.lines().size()
                && new BigDecimal(budget.plannedRevenue()).compareTo(source.plannedBatchPrice()) == 0
                && new BigDecimal(budget.plannedCost()).compareTo(source.totalPlannedCost()) == 0;
        for (int index = 0; exact && index < source.lines().size(); index++) {
            var line = source.lines().get(index);
            ProjectBudgetLineRequest sent = conversion.lines().get(index);
            var stored = budget.lines().get(index);
            exact = stored.category() == sent.category() && stored.category().name().equals(line.costGroup().name())
                    && new BigDecimal(stored.quantity()).compareTo(line.quantity()) == 0
                    && new BigDecimal(stored.unitCost()).compareTo(line.unitCost()) == 0
                    && new BigDecimal(stored.plannedLineCost()).compareTo(line.plannedCost()) == 0;
        }
        if (!exact) throw new IllegalStateException("F05 budget does not exactly reproduce the approved estimate revision");
    }

    private static EstimateConversionResultResponse result(boolean replayed, EstimateConversionRecord lineage,
                                                           ProjectBudgetVersionResponse budget) {
        return new EstimateConversionResultResponse(replayed, EstimateConversionResponse.from(lineage), budget, "NONE", "PLANNED_ONLY");
    }

    private static EstimateException alreadyConverted() {
        return new EstimateException("revision_already_converted", 409,
                "This approved revision was already converted into a project planning budget.");
    }
}
