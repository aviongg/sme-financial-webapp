package com.app.sme_health_backend.samples.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.samples.dto.SamplePlanApprovalRequest;
import com.app.sme_health_backend.samples.dto.SamplePlanCalculationResponse;
import com.app.sme_health_backend.samples.dto.SamplePlanRequest;
import com.app.sme_health_backend.samples.dto.SamplePlanVersionResponse;
import com.app.sme_health_backend.samples.dto.SampleCreateRequest;
import com.app.sme_health_backend.samples.dto.SampleListResponse;
import com.app.sme_health_backend.samples.dto.SamplePlanningSummaryResponse;
import com.app.sme_health_backend.samples.dto.SampleResponse;
import com.app.sme_health_backend.samples.dto.SampleTransitionRequest;
import com.app.sme_health_backend.samples.dto.SampleUpdateRequest;
import com.app.sme_health_backend.samples.model.SamplePlanStatus;
import com.app.sme_health_backend.samples.model.SamplePlanVersionRecord;
import com.app.sme_health_backend.samples.model.SamplePlanVersionRecord.LineRecord;
import com.app.sme_health_backend.samples.model.SampleKind;
import com.app.sme_health_backend.samples.model.SampleRecord;
import com.app.sme_health_backend.samples.model.SampleStatus;
import com.app.sme_health_backend.samples.repository.SamplePlanRepository;
import com.app.sme_health_backend.samples.repository.SampleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SamplePlanningService {
    public static final String ACTUAL_FINANCIALS_STATUS = "NOT_IMPLEMENTED_PHASE_A";

    private final SampleRepository activityRepository;
    private final SamplePlanRepository planRepository;
    private final SamplePlanCalculator calculator;
    private final SecurityAuditService audit;

    public SamplePlanningService(
            SampleRepository activityRepository,
            SamplePlanRepository planRepository,
            SamplePlanCalculator calculator,
            SecurityAuditService audit
    ) {
        this.activityRepository = activityRepository;
        this.planRepository = planRepository;
        this.calculator = calculator;
        this.audit = audit;
    }

    @Transactional
    public SampleResponse createActivity(BusinessAccessContext context, SampleCreateRequest request) {
        var metadata = calculator.validateCreateMetadata(request);
        activityRepository.lockActiveBusiness(context.businessId());
        if (activityRepository.existsByCode(context.businessId(), metadata.code(), null)) {
            throw new SampleException("duplicate_activity_code", 409, "An activity with this code already exists.");
        }

        activityRepository.validateProjectLink(context.businessId(), metadata.projectId(), null);
        OffsetDateTime now = OffsetDateTime.now();
        SampleRecord record = new SampleRecord(
                UUID.randomUUID(),
                context.businessId(),
                metadata.code(),
                metadata.name(),
                metadata.kind(),
                metadata.description(),
                metadata.recoveryIntent(),
                metadata.projectId(),
                SampleStatus.DRAFT,
                metadata.startDate(),
                metadata.targetEndDate(),
                SamplePlanCalculator.BASE_CURRENCY,
                0L,
                context.userId(),
                context.userId(),
                now,
                now
        );
        try {
            activityRepository.insert(record);
        } catch (DataIntegrityViolationException ex) {
            throw new SampleException("duplicate_activity_code", 409, "An activity with this code already exists.");
        }

        audit.logSuccess(
                AuditEventType.SAMPLE_CREATED,
                context.userId(),
                null,
                context.businessId(),
                "activity",
                record.id().toString(),
                Map.of(
                        "code", record.code(),
                        "kind", record.kind().name(),
                        "status", record.status().name(),
                        "version", record.version()
                )
        );
        return SampleResponse.fromRecord(record, null, null, 0, false);
    }

    @Transactional(readOnly = true)
    public SampleListResponse listActivities(
            BusinessAccessContext context,
            SampleStatus status,
            SampleKind kind,
            String query,
            boolean includeArchived
    ) {
        List<SampleRecord> activities = activityRepository.findAllByBusiness(
                context.businessId(),
                status,
                kind,
                query,
                includeArchived
        );
        List<UUID> activityIds = activities.stream().map(SampleRecord::id).toList();
        Map<UUID, List<SamplePlanVersionRecord>> plansByActivity =
                planRepository.findAllByActivitiesOrderByRevisionDesc(context.businessId(), activityIds);

        List<SampleResponse> items = activities.stream()
                .map(activity -> assembleSampleResponse(
                        activity,
                        plansByActivity.getOrDefault(activity.id(), List.of())
                ))
                .toList();

        return new SampleListResponse(
                context.businessId(),
                SamplePlanCalculator.BASE_CURRENCY,
                SamplePlanCalculator.CURRENCY_BASIS,
                true,
                "PLANNED_ONLY",
                items
        );
    }

    @Transactional(readOnly = true)
    public SampleResponse getActivity(BusinessAccessContext context, UUID activityId) {
        SampleRecord activity = activityRepository.findById(context.businessId(), activityId)
                .orElseThrow(() -> new SampleException("activity_not_found", 404, "Sample not found."));
        List<SamplePlanVersionRecord> plans =
                planRepository.findAllByActivityOrderByRevisionDesc(context.businessId(), activityId);
        return assembleSampleResponse(activity, plans);
    }

    @Transactional
    public SampleResponse updateActivity(BusinessAccessContext context, UUID activityId, SampleUpdateRequest request) {
        var metadata = calculator.validateUpdateMetadata(request);
        // Match project deletion's parent-before-child lock order. The pre-read is
        // used only to distinguish a retained archived link; the locked row/version
        // below must still match the caller before any mutation can occur.
        activityRepository.lockActiveBusiness(context.businessId());
        SampleRecord observed = activityRepository.findById(context.businessId(), activityId)
                .orElseThrow(() -> new SampleException("activity_not_found", 404, "Sample not found."));
        activityRepository.validateProjectLink(context.businessId(), metadata.projectId(), observed.projectId());
        SampleRecord current = activityRepository.findForUpdate(context.businessId(), activityId)
                .orElseThrow(() -> new SampleException("activity_not_found", 404, "Sample not found."));

        if (!current.status().allowsPlanningMutations()) {
            throw new SampleException(
                    "invalid_activity_state",
                    409,
                    "Completed, cancelled, or archived activities cannot be edited."
            );
        }
        if (current.version() != request.expectedVersion()) {
            throw new SampleException(
                    "stale_activity_version",
                    409,
                    "This activity changed since you loaded it. Reload before saving."
            );
        }
        if (activityRepository.existsByCode(context.businessId(), metadata.code(), activityId)) {
            throw new SampleException("duplicate_activity_code", 409, "An activity with this code already exists.");
        }

        if (current.recoveryIntent() != metadata.recoveryIntent()
                && !planRepository.lockAllByActivityOrderByRevisionAsc(context.businessId(), activityId).isEmpty()) {
            throw new SampleException("recovery_intent_locked", 409,
                    "Recovery intent cannot change while plan revisions exist. Discard an unapproved draft first or use a new activity.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        boolean updated = activityRepository.updateMetadata(
                context.businessId(),
                activityId,
                request.expectedVersion(),
                metadata.code(),
                metadata.name(),
                metadata.kind(),
                metadata.description(),
                metadata.recoveryIntent(),
                metadata.projectId(),
                metadata.startDate(),
                metadata.targetEndDate(),
                context.userId(),
                now
        );
        if (!updated) {
            throw new SampleException("stale_activity_version", 409, "This activity changed since you loaded it. Reload before saving.");
        }

        SampleRecord reloaded = activityRepository.findById(context.businessId(), activityId).orElseThrow();
        List<SamplePlanVersionRecord> plans =
                planRepository.findAllByActivityOrderByRevisionDesc(context.businessId(), activityId);

        audit.logSuccess(
                AuditEventType.SAMPLE_UPDATED,
                context.userId(),
                null,
                context.businessId(),
                "activity",
                activityId.toString(),
                Map.of(
                        "code", reloaded.code(),
                        "kind", reloaded.kind().name(),
                        "version", reloaded.version()
                )
        );
        return assembleSampleResponse(reloaded, plans);
    }

    @Transactional
    public void deleteDraftActivity(BusinessAccessContext context, UUID activityId, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new SampleValidationException("expectedVersion", "version_invalid");
        }
        // Lock Level 2 (activity) -> Lock Level 3 (plans)
        SampleRecord current = activityRepository.findForUpdate(context.businessId(), activityId)
                .orElseThrow(() -> new SampleException("activity_not_found", 404, "Sample not found."));
        List<SamplePlanVersionRecord> plans =
                planRepository.lockAllByActivityOrderByRevisionAsc(context.businessId(), activityId);

        boolean hasImmutableHistory = plans.stream().anyMatch(b -> b.status().isImmutable());
        if (!current.status().canDeleteActivity(hasImmutableHistory)) {
            throw new SampleException(
                    "activity_delete_forbidden",
                    409,
                    "Only DRAFT activities without approved plan history can be deleted; use CANCELLED or ARCHIVED instead."
            );
        }
        if (current.version() != expectedVersion) {
            throw new SampleException(
                    "stale_activity_version",
                    409,
                    "This activity changed since you loaded it. Reload before deleting."
            );
        }

        planRepository.deleteAllDraftsForActivity(context.businessId(), activityId);
        boolean deleted = activityRepository.deleteDraft(context.businessId(), activityId, expectedVersion);
        if (!deleted) {
            throw new SampleException("stale_activity_version", 409, "This activity changed since you loaded it. Reload before deleting.");
        }

        audit.logSuccess(
                AuditEventType.SAMPLE_DELETED,
                context.userId(),
                null,
                context.businessId(),
                "activity",
                activityId.toString(),
                Map.of("code", current.code(), "deletedStatus", current.status().name())
        );
    }

    @Transactional
    public SampleResponse transitionActivity(
            BusinessAccessContext context,
            UUID activityId,
            SampleTransitionRequest request
    ) {
        var transition = calculator.validateTransition(request);
        // Lock Level 2 (activity) -> Lock Level 3 (plans)
        SampleRecord current = activityRepository.findForUpdate(context.businessId(), activityId)
                .orElseThrow(() -> new SampleException("activity_not_found", 404, "Sample not found."));
        List<SamplePlanVersionRecord> plans =
                planRepository.lockAllByActivityOrderByRevisionAsc(context.businessId(), activityId);

        if (current.version() != transition.expectedVersion()) {
            throw new SampleException(
                    "stale_activity_version",
                    409,
                    "This activity changed since you loaded it. Reload before changing status."
            );
        }
        if (!current.status().canTransitionTo(transition.targetStatus())) {
            throw new SampleException(
                    "invalid_activity_transition",
                    409,
                    "Cannot transition activity from " + current.status() + " to " + transition.targetStatus() + "."
            );
        }

        boolean hasOpenDraft = plans.stream().anyMatch(b -> b.status() == SamplePlanStatus.DRAFT);
        if (!transition.targetStatus().allowsPlanningMutations()
                && hasOpenDraft) {
            throw new SampleException(
                    "open_draft_plan_exists",
                    409,
                    "Approve or discard the open draft plan revision before closing this activity."
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        boolean updated = activityRepository.updateStatus(
                context.businessId(),
                activityId,
                transition.expectedVersion(),
                transition.targetStatus(),
                context.userId(),
                now
        );
        if (!updated) {
            throw new SampleException("stale_activity_version", 409, "This activity changed since you loaded it. Reload before changing status.");
        }

        SampleRecord reloaded = activityRepository.findById(context.businessId(), activityId).orElseThrow();
        List<SamplePlanVersionRecord> orderedDesc =
                planRepository.findAllByActivityOrderByRevisionDesc(context.businessId(), activityId);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("fromStatus", current.status().name());
        auditDetails.put("toStatus", reloaded.status().name());
        auditDetails.put("version", reloaded.version());
        if (transition.reason() != null) {
            auditDetails.put("reason", transition.reason());
        }
        audit.logSuccess(
                AuditEventType.SAMPLE_STATUS_TRANSITIONED,
                context.userId(),
                null,
                context.businessId(),
                "activity",
                activityId.toString(),
                auditDetails
        );
        return assembleSampleResponse(reloaded, orderedDesc);
    }

    @Transactional
    public SamplePlanVersionResponse createDraftPlan(
            BusinessAccessContext context,
            UUID activityId,
            SamplePlanRequest request
    ) {
        if (request == null) {
            throw new SampleValidationException("request", "required");
        }
        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new SampleValidationException("expectedActivityVersion", "version_invalid");
        }
        // Lock Level 2 (activity) -> Lock Level 3 (plans)
        SampleRecord activity = activityRepository.findForUpdate(context.businessId(), activityId)
                .orElseThrow(() -> new SampleException("activity_not_found", 404, "Sample not found."));
        if (!activity.status().allowsPlanningMutations()) {
            throw new SampleException(
                    "invalid_activity_state",
                    409,
                    "Plans cannot be created on completed, cancelled, or archived activities."
            );
        }
        if (activity.version() != request.expectedVersion()) {
            throw new SampleException(
                    "stale_activity_version",
                    409,
                    "This activity changed since you loaded it. Reload before creating a plan revision."
            );
        }

        List<SamplePlanVersionRecord> existingPlans =
                planRepository.lockAllByActivityOrderByRevisionAsc(context.businessId(), activityId);
        if (existingPlans.stream().anyMatch(b -> b.status() == SamplePlanStatus.DRAFT)) {
            throw new SampleException(
                    "draft_plan_already_exists",
                    409,
                    "An open draft plan revision already exists for this activity. Edit, approve, or discard that draft first."
            );
        }

        int nextRevision = existingPlans.stream()
                .mapToInt(SamplePlanVersionRecord::revision)
                .max()
                .orElse(0) + 1;

        UUID planVersionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        SamplePlanCalculationResponse calculated = calculator.calculate(context.businessId(), activity.recoveryIntent(), request.plannedRecovery(), request.lines());
        List<LineRecord> lineRecords = calculated.lines().stream().map(line -> new LineRecord(
                UUID.randomUUID(),
                context.businessId(),
                activityId,
                planVersionId,
                line.lineNumber(),
                line.category(),
                line.description(),
                line.rawQuantity(),
                line.unit(),
                line.rawUnitCost(),
                line.rawPlannedLineCost()
        )).toList();

        SamplePlanVersionRecord draftRecord = new SamplePlanVersionRecord(
                planVersionId,
                context.businessId(),
                activityId,
                nextRevision,
                SamplePlanStatus.DRAFT,
                SamplePlanCalculator.BASE_CURRENCY,
                activity.recoveryIntent(),
                calculated.rawTotals().plannedRecovery(),
                calculated.rawTotals().plannedCost(),
                calculated.rawTotals().plannedContribution(),
                calculated.unavailableReasons().plannedContribution(),
                SamplePlanCalculator.CALCULATION_VERSION,
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
            planRepository.insertDraft(draftRecord);
        } catch (DataIntegrityViolationException ex) {
            throw new SampleException(
                    "draft_plan_already_exists",
                    409,
                    "An open draft plan revision already exists for this activity."
            );
        }
        activityRepository.incrementVersion(context.businessId(), activityId, context.userId(), now);

        audit.logSuccess(
                AuditEventType.SAMPLE_PLAN_CREATED,
                context.userId(),
                null,
                context.businessId(),
                "activity_plan_version",
                planVersionId.toString(),
                Map.of(
                        "activityId", activityId.toString(),
                        "revision", nextRevision,
                        "status", SamplePlanStatus.DRAFT.name(),
                        "plannedRecoveryKnown", calculated.plannedRecovery() != null,
                        "plannedCost", calculated.plannedCost()
                )
        );
        return SamplePlanVersionResponse.fromRecord(draftRecord);
    }

    @Transactional
    public SamplePlanVersionResponse updateDraftPlan(
            BusinessAccessContext context,
            UUID activityId,
            UUID planId,
            SamplePlanRequest request
    ) {
        if (request == null) {
            throw new SampleValidationException("request", "required");
        }
        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new SampleValidationException("expectedPlanVersion", "version_invalid");
        }
        // Lock Level 2 (activity) -> Lock Level 3 (plans)
        SampleRecord activity = activityRepository.findForUpdate(context.businessId(), activityId)
                .orElseThrow(() -> new SampleException("activity_not_found", 404, "Sample not found."));
        if (!activity.status().allowsPlanningMutations()) {
            throw new SampleException(
                    "invalid_activity_state",
                    409,
                    "Plans cannot be edited on completed, cancelled, or archived activities."
            );
        }

        List<SamplePlanVersionRecord> plans =
                planRepository.lockAllByActivityOrderByRevisionAsc(context.businessId(), activityId);
        SamplePlanVersionRecord target = plans.stream()
                .filter(b -> b.id().equals(planId))
                .findFirst()
                .orElseThrow(() -> new SampleException("plan_not_found", 404, "Plan version not found."));

        if (target.status().isImmutable()) {
            throw new SampleException(
                    "plan_immutable",
                    409,
                    "Approved and superseded plan versions are immutable. Create a new plan revision instead."
            );
        }
        if (target.version() != request.expectedVersion()) {
            throw new SampleException(
                    "stale_plan_version",
                    409,
                    "This draft plan changed since you loaded it. Reload before saving."
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        SamplePlanCalculationResponse calculated = calculator.calculate(context.businessId(), activity.recoveryIntent(), request.plannedRecovery(), request.lines());
        List<LineRecord> lineRecords = calculated.lines().stream().map(line -> new LineRecord(
                UUID.randomUUID(),
                context.businessId(),
                activityId,
                planId,
                line.lineNumber(),
                line.category(),
                line.description(),
                line.rawQuantity(),
                line.unit(),
                line.rawUnitCost(),
                line.rawPlannedLineCost()
        )).toList();

        boolean replaced = planRepository.replaceDraft(
                context.businessId(),
                activityId,
                planId,
                request.expectedVersion(),
                calculated.rawTotals().plannedRecovery(),
                calculated.rawTotals().plannedCost(),
                calculated.rawTotals().plannedContribution(),
                calculated.unavailableReasons().plannedContribution(),
                lineRecords,
                context.userId(),
                now
        );
        if (!replaced) {
            throw new SampleException(
                    "stale_plan_version",
                    409,
                    "This draft plan changed since you loaded it. Reload before saving."
            );
        }
        activityRepository.incrementVersion(context.businessId(), activityId, context.userId(), now);

        SamplePlanVersionRecord updatedRecord = planRepository
                .findAllByActivityOrderByRevisionDesc(context.businessId(), activityId)
                .stream()
                .filter(b -> b.id().equals(planId))
                .findFirst()
                .orElseThrow();

        audit.logSuccess(
                AuditEventType.SAMPLE_PLAN_UPDATED,
                context.userId(),
                null,
                context.businessId(),
                "activity_plan_version",
                planId.toString(),
                Map.of(
                        "activityId", activityId.toString(),
                        "revision", updatedRecord.revision(),
                        "version", updatedRecord.version(),
                        "plannedRecoveryKnown", calculated.plannedRecovery() != null,
                        "plannedCost", calculated.plannedCost()
                )
        );
        return SamplePlanVersionResponse.fromRecord(updatedRecord);
    }

    @Transactional
    public void deleteDraftPlan(
            BusinessAccessContext context,
            UUID activityId,
            UUID planId,
            Long expectedActivityVersion,
            Long expectedPlanVersion
    ) {
        if (expectedActivityVersion == null || expectedActivityVersion < 0) {
            throw new SampleValidationException("expectedActivityVersion", "version_invalid");
        }
        if (expectedPlanVersion == null || expectedPlanVersion < 0) {
            throw new SampleValidationException("expectedPlanVersion", "version_invalid");
        }
        // Lock Level 2 (activity) -> Lock Level 3 (plans)
        SampleRecord activity = activityRepository.findForUpdate(context.businessId(), activityId)
                .orElseThrow(() -> new SampleException("activity_not_found", 404, "Sample not found."));
        if (!activity.status().allowsPlanningMutations()) {
            throw new SampleException(
                    "invalid_activity_state",
                    409,
                    "Plans cannot be modified on completed, cancelled, or archived activities."
            );
        }
        if (activity.version() != expectedActivityVersion) {
            throw new SampleException(
                    "stale_activity_version",
                    409,
                    "This activity changed since you loaded it. Reload before discarding the draft plan."
            );
        }

        List<SamplePlanVersionRecord> plans =
                planRepository.lockAllByActivityOrderByRevisionAsc(context.businessId(), activityId);
        SamplePlanVersionRecord target = plans.stream()
                .filter(b -> b.id().equals(planId))
                .findFirst()
                .orElseThrow(() -> new SampleException("plan_not_found", 404, "Plan version not found."));

        if (!target.status().canDeleteDraft()) {
            throw new SampleException(
                    "plan_immutable",
                    409,
                    "Approved and superseded plan versions are permanent history and cannot be deleted."
            );
        }
        if (target.version() != expectedPlanVersion) {
            throw new SampleException(
                    "stale_plan_version",
                    409,
                    "This draft plan changed since you loaded it. Reload before discarding."
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        boolean deleted = planRepository.deleteDraft(context.businessId(), activityId, planId, expectedPlanVersion);
        if (!deleted) {
            throw new SampleException("stale_plan_version", 409, "This draft plan changed since you loaded it. Reload before discarding.");
        }
        activityRepository.incrementVersion(context.businessId(), activityId, context.userId(), now);

        audit.logSuccess(
                AuditEventType.SAMPLE_PLAN_DELETED,
                context.userId(),
                null,
                context.businessId(),
                "activity_plan_version",
                planId.toString(),
                Map.of(
                        "activityId", activityId.toString(),
                        "revision", target.revision(),
                        "deletedStatus", target.status().name()
                )
        );
    }

    @Transactional
    public SamplePlanVersionResponse approvePlan(
            BusinessAccessContext context,
            UUID activityId,
            UUID planId,
            SamplePlanApprovalRequest request
    ) {
        if (request == null) {
            throw new SampleValidationException("request", "required");
        }
        if (request.expectedActivityVersion() == null || request.expectedActivityVersion() < 0) {
            throw new SampleValidationException("expectedActivityVersion", "version_invalid");
        }
        if (request.expectedPlanVersion() == null || request.expectedPlanVersion() < 0) {
            throw new SampleValidationException("expectedPlanVersion", "version_invalid");
        }

        // Lock Level 2 (activity) -> Lock Level 3 (plans ordered by revision ASC, id ASC)
        SampleRecord activity = activityRepository.findForUpdate(context.businessId(), activityId)
                .orElseThrow(() -> new SampleException("activity_not_found", 404, "Sample not found."));
        if (!activity.status().allowsPlanningMutations()) {
            throw new SampleException(
                    "invalid_activity_state",
                    409,
                    "Plans cannot be approved on completed, cancelled, or archived activities."
            );
        }
        if (activity.version() != request.expectedActivityVersion()) {
            throw new SampleException(
                    "stale_activity_version",
                    409,
                    "This activity changed since you loaded it. Reload before approving the plan."
            );
        }

        List<SamplePlanVersionRecord> plans =
                planRepository.lockAllByActivityOrderByRevisionAsc(context.businessId(), activityId);
        SamplePlanVersionRecord target = plans.stream()
                .filter(b -> b.id().equals(planId))
                .findFirst()
                .orElseThrow(() -> new SampleException("plan_not_found", 404, "Plan version not found."));

        if (target.status() != SamplePlanStatus.DRAFT) {
            throw new SampleException(
                    "invalid_plan_transition",
                    409,
                    "Only a DRAFT plan version can be approved."
            );
        }
        if (target.version() != request.expectedPlanVersion()) {
            throw new SampleException(
                    "stale_plan_version",
                    409,
                    "This draft plan changed since you loaded it. Reload before approving."
            );
        }

        SamplePlanVersionRecord currentApproved = plans.stream()
                .filter(b -> b.status() == SamplePlanStatus.APPROVED)
                .findFirst()
                .orElse(null);

        OffsetDateTime now = OffsetDateTime.now();
        // Step 1: Supersede prior APPROVED row first so uq_sample_cost_plan_versions_one_approved is never violated.
        if (currentApproved != null) {
            boolean superseded = planRepository.supersedeApproved(
                    context.businessId(),
                    activityId,
                    currentApproved.id(),
                    target.id(),
                    context.userId(),
                    now
            );
            if (!superseded) {
                throw new SampleException("stale_plan_version", 409, "Plan state changed concurrently. Reload before approving.");
            }
        }

        // Step 2: Approve target DRAFT row.
        boolean approved = planRepository.approveDraft(
                context.businessId(),
                activityId,
                target.id(),
                request.expectedPlanVersion(),
                context.userId(),
                now
        );
        if (!approved) {
            throw new SampleException("stale_plan_version", 409, "This draft plan changed since you loaded it. Reload before approving.");
        }

        // Step 3: Increment parent activity version.
        activityRepository.incrementVersion(context.businessId(), activityId, context.userId(), now);

        SamplePlanVersionRecord approvedRecord = planRepository
                .findAllByActivityOrderByRevisionDesc(context.businessId(), activityId)
                .stream()
                .filter(b -> b.id().equals(target.id()))
                .findFirst()
                .orElseThrow();
        SamplePlanVersionResponse response = SamplePlanVersionResponse.fromRecord(approvedRecord);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("activityId", activityId.toString());
        auditDetails.put("revision", approvedRecord.revision());
        auditDetails.put("version", approvedRecord.version());
        auditDetails.put("plannedRecovery", response.plannedRecovery());
        auditDetails.put("plannedCost", response.plannedCost());
        auditDetails.put("plannedContribution", response.plannedContribution());
        if (currentApproved != null) {
            auditDetails.put("supersededPlanId", currentApproved.id().toString());
            auditDetails.put("supersededRevision", currentApproved.revision());
        }
        audit.logSuccess(
                AuditEventType.SAMPLE_PLAN_APPROVED,
                context.userId(),
                null,
                context.businessId(),
                "activity_plan_version",
                target.id().toString(),
                auditDetails
        );
        return response;
    }

    @Transactional(readOnly = true)
    public List<SamplePlanVersionResponse> listPlans(BusinessAccessContext context, UUID activityId) {
        activityRepository.findById(context.businessId(), activityId)
                .orElseThrow(() -> new SampleException("activity_not_found", 404, "Sample not found."));
        return planRepository.findAllByActivityOrderByRevisionDesc(context.businessId(), activityId)
                .stream()
                .map(SamplePlanVersionResponse::fromRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public SamplePlanVersionResponse getPlan(BusinessAccessContext context, UUID activityId, UUID planId) {
        return listPlans(context, activityId).stream().filter(p -> p.id().equals(planId)).findFirst()
                .orElseThrow(() -> new SampleException("plan_not_found", 404, "Plan version not found."));
    }

    @Transactional(readOnly = true)
    public SamplePlanningSummaryResponse getPlanningSummary(BusinessAccessContext context, UUID activityId) {
        SampleRecord activity = activityRepository.findById(context.businessId(), activityId)
                .orElseThrow(() -> new SampleException("activity_not_found", 404, "Sample not found."));
        List<SamplePlanVersionRecord> plans =
                planRepository.findAllByActivityOrderByRevisionDesc(context.businessId(), activityId);

        SampleResponse activityResponse = assembleSampleResponse(activity, plans);
        int supersededCount = (int) plans.stream()
                .filter(b -> b.status() == SamplePlanStatus.SUPERSEDED)
                .count();
        String planStatusReason = activityResponse.activeApprovedPlan() != null
                ? null
                : (activityResponse.latestDraftPlan() != null ? "DRAFT_NOT_YET_APPROVED" : "NO_PLAN_DEFINED");

        return new SamplePlanningSummaryResponse(
                context.businessId(),
                true,
                "PLANNED_ONLY",
                SamplePlanCalculator.BASE_CURRENCY,
                SamplePlanCalculator.CURRENCY_BASIS,
                ACTUAL_FINANCIALS_STATUS,
                activityResponse,
                activityResponse.activeApprovedPlan(),
                activityResponse.latestDraftPlan(),
                planStatusReason,
                plans.size(),
                supersededCount,
                new SamplePlanCalculationResponse.Calculation(
                        SamplePlanCalculator.CALCULATION_VERSION,
                        null
                )
        );
    }

    private SampleResponse assembleSampleResponse(
            SampleRecord activity,
            List<SamplePlanVersionRecord> plans
    ) {
        SamplePlanVersionResponse activeApproved = plans.stream()
                .filter(b -> b.status() == SamplePlanStatus.APPROVED)
                .findFirst()
                .map(SamplePlanVersionResponse::fromRecord)
                .orElse(null);
        SamplePlanVersionResponse latestDraft = plans.stream()
                .filter(b -> b.status() == SamplePlanStatus.DRAFT)
                .findFirst()
                .map(SamplePlanVersionResponse::fromRecord)
                .orElse(null);
        boolean hasImmutableHistory = plans.stream().anyMatch(b -> b.status().isImmutable());
        return SampleResponse.fromRecord(activity, activeApproved, latestDraft, plans.size(), hasImmutableHistory);
    }
}
