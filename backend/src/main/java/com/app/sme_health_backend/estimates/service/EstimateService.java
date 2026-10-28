package com.app.sme_health_backend.estimates.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.estimates.dto.EstimateConversionResponse;
import com.app.sme_health_backend.estimates.dto.EstimateCreateRequest;
import com.app.sme_health_backend.estimates.dto.EstimateDetailResponse;
import com.app.sme_health_backend.estimates.dto.EstimateLineRequest;
import com.app.sme_health_backend.estimates.dto.EstimateListResponse;
import com.app.sme_health_backend.estimates.dto.EstimateResponse;
import com.app.sme_health_backend.estimates.dto.EstimateRevisionRequest;
import com.app.sme_health_backend.estimates.dto.EstimateRevisionResponse;
import com.app.sme_health_backend.estimates.dto.EstimateTransitionRequest;
import com.app.sme_health_backend.estimates.dto.EstimateUpdateRequest;
import com.app.sme_health_backend.estimates.dto.PricingPreviewRequest;
import com.app.sme_health_backend.estimates.model.EstimateConversionRecord;
import com.app.sme_health_backend.estimates.model.EstimateRecord;
import com.app.sme_health_backend.estimates.model.EstimateRevisionRecord;
import com.app.sme_health_backend.estimates.model.EstimateRevisionStatus;
import com.app.sme_health_backend.estimates.model.EstimateStatus;
import com.app.sme_health_backend.estimates.repository.EstimateRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * F06 Phase B persisted planning estimates. Calculations are exactly the f06-preview-v1 {@link PricingCalculator};
 * this service only stores and versions them. It touches the estimate tables only and never posts money or reads
 * or writes MonthlyRecord, scores, ledger, cash, receivables, payables, invoices/bills, inventory or project actuals.
 * Business and actor always come from the authenticated {@link BusinessAccessContext}.
 */
@Service
public class EstimateService {
    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9._/-]{0,39}");

    private final EstimateRepository repository;
    private final PricingCalculator calculator;
    private final SecurityAuditService audit;

    public EstimateService(EstimateRepository repository, PricingCalculator calculator, SecurityAuditService audit) {
        this.repository = repository;
        this.calculator = calculator;
        this.audit = audit;
    }

    // ---- reads ----

    @Transactional(readOnly = true)
    public EstimateListResponse list(BusinessAccessContext context, EstimateStatus status, boolean includeArchived) {
        List<EstimateRecord> estimates = repository.listEstimates(context.businessId(), status, includeArchived);
        List<UUID> ids = estimates.stream().map(EstimateRecord::id).toList();
        Map<UUID, List<EstimateRevisionRecord>> revisions = repository.findRevisionHeaders(context.businessId(), ids);
        Map<UUID, Integer> conversions = repository.countConversions(context.businessId(), ids);
        List<EstimateResponse> items = estimates.stream().map(estimate -> summarize(estimate,
                revisions.getOrDefault(estimate.id(), List.of()), conversions.getOrDefault(estimate.id(), 0))).toList();
        return new EstimateListResponse(context.businessId(), "PKR", "APPLICATION_FIXED_PKR", true, items);
    }

    @Transactional(readOnly = true)
    public EstimateDetailResponse get(BusinessAccessContext context, UUID estimateId) {
        return detail(context, find(context, estimateId));
    }

    @Transactional(readOnly = true)
    public List<EstimateRevisionResponse> listRevisions(BusinessAccessContext context, UUID estimateId) {
        find(context, estimateId);
        Map<UUID, EstimateConversionRecord> conversions = conversionsByRevision(context, estimateId);
        return repository.findRevisions(context.businessId(), estimateId).stream()
                .map(revision -> respond(revision, conversions.get(revision.id()))).toList();
    }

    @Transactional(readOnly = true)
    public EstimateRevisionResponse getRevision(BusinessAccessContext context, UUID estimateId, UUID revisionId) {
        find(context, estimateId);
        EstimateRevisionRecord revision = repository.findRevision(context.businessId(), estimateId, revisionId)
                .orElseThrow(EstimateService::revisionNotFound);
        return respond(revision, repository.findConversionByRevision(context.businessId(), revisionId).orElse(null));
    }

    // ---- estimate identity ----

    @Transactional
    public EstimateDetailResponse create(BusinessAccessContext context, EstimateCreateRequest request) {
        if (request == null) throw new EstimateValidationException("request", "required");
        String code = code(request.code());
        String name = requiredText(request.name(), "name", 160);
        String description = optionalText(request.description(), "description", 1000);
        PricingCalculator.Result draft = request.draft() == null ? null : calculator.calculate(request.draft());
        if (repository.codeExists(context.businessId(), code, null)) throw duplicateCode();

        OffsetDateTime now = OffsetDateTime.now();
        UUID id = UUID.randomUUID();
        try {
            repository.insertEstimate(new EstimateRecord(id, context.businessId(), code, name, description, EstimateStatus.ACTIVE,
                    "PKR", 0L, context.userId(), context.userId(), null, null, now, now));
        } catch (DataIntegrityViolationException ex) {
            throw duplicateCode();
        }
        EstimateRecord created = find(context, id);
        auditEstimate(AuditEventType.ESTIMATE_CREATED, context, created);
        if (draft != null) {
            insertDraft(context, created, draft, 1, now);
            repository.incrementVersion(context.businessId(), id, context.userId(), now);
        }
        return detail(context, find(context, id));
    }

    @Transactional
    public EstimateDetailResponse update(BusinessAccessContext context, UUID estimateId, EstimateUpdateRequest request) {
        if (request == null) throw new EstimateValidationException("request", "required");
        long expectedVersion = version(request.expectedVersion(), "expectedVersion");
        String code = code(request.code());
        String name = requiredText(request.name(), "name", 160);
        String description = optionalText(request.description(), "description", 1000);
        EstimateRecord current = lockActive(context, estimateId, "Archived estimates are read-only.");
        requireVersion(current.version(), expectedVersion, staleEstimate());
        if (repository.codeExists(context.businessId(), code, estimateId)) throw duplicateCode();
        try {
            if (!repository.updateMetadata(context.businessId(), estimateId, expectedVersion, code, name, description,
                    context.userId(), OffsetDateTime.now())) throw staleEstimate();
        } catch (DataIntegrityViolationException ex) {
            throw duplicateCode();
        }
        EstimateRecord updated = find(context, estimateId);
        auditEstimate(AuditEventType.ESTIMATE_UPDATED, context, updated);
        return detail(context, updated);
    }

    @Transactional
    public void delete(BusinessAccessContext context, UUID estimateId, Long expectedVersion) {
        long version = version(expectedVersion, "expectedVersion");
        EstimateRecord current = lockActive(context, estimateId, "Archived estimates are permanent and cannot be deleted.");
        List<EstimateRevisionRecord> revisions = repository.lockRevisions(context.businessId(), estimateId);
        if (revisions.stream().anyMatch(revision -> revision.status().isImmutable())) {
            throw new EstimateException("estimate_delete_forbidden", 409,
                    "Estimates with approved revision history cannot be deleted. Archive the estimate instead.");
        }
        requireVersion(current.version(), version, staleEstimate());
        if (!repository.deleteEstimate(context.businessId(), estimateId, version)) throw staleEstimate();
        auditEstimate(AuditEventType.ESTIMATE_DELETED, context, current);
    }

    @Transactional
    public EstimateDetailResponse archive(BusinessAccessContext context, UUID estimateId, EstimateTransitionRequest request) {
        long version = version(request == null ? null : request.expectedVersion(), "expectedVersion");
        EstimateRecord current = lockActive(context, estimateId, "This estimate is already archived.");
        List<EstimateRevisionRecord> revisions = repository.lockRevisions(context.businessId(), estimateId);
        requireVersion(current.version(), version, staleEstimate());
        if (revisions.stream().anyMatch(revision -> revision.status() == EstimateRevisionStatus.DRAFT)) {
            throw new EstimateException("open_draft_revision_exists", 409,
                    "Approve or discard the open draft revision before archiving this estimate.");
        }
        if (!repository.archive(context.businessId(), estimateId, version, context.userId(), OffsetDateTime.now())) throw staleEstimate();
        EstimateRecord archived = find(context, estimateId);
        auditEstimate(AuditEventType.ESTIMATE_ARCHIVED, context, archived);
        return detail(context, archived);
    }

    // ---- revisions ----

    @Transactional
    public EstimateRevisionResponse createRevision(BusinessAccessContext context, UUID estimateId, EstimateRevisionRequest request) {
        if (request == null) throw new EstimateValidationException("request", "required");
        long expectedEstimateVersion = version(request.expectedVersion(), "expectedVersion");
        PricingCalculator.Result result = calculator.calculate(request.content());
        // Lock order: estimate row, then all of its revisions.
        EstimateRecord estimate = lockActive(context, estimateId, "Archived estimates are read-only.");
        requireVersion(estimate.version(), expectedEstimateVersion, staleEstimate());
        List<EstimateRevisionRecord> revisions = repository.lockRevisions(context.businessId(), estimateId);
        if (revisions.stream().anyMatch(revision -> revision.status() == EstimateRevisionStatus.DRAFT)) throw draftExists();
        int next = revisions.stream().mapToInt(EstimateRevisionRecord::revision).max().orElse(0) + 1;
        OffsetDateTime now = OffsetDateTime.now();
        UUID id = insertDraft(context, estimate, result, next, now);
        repository.incrementVersion(context.businessId(), estimateId, context.userId(), now);
        return respond(repository.findRevision(context.businessId(), estimateId, id).orElseThrow(), null);
    }

    @Transactional
    public EstimateRevisionResponse updateRevision(BusinessAccessContext context, UUID estimateId, UUID revisionId,
                                                   EstimateRevisionRequest request) {
        if (request == null) throw new EstimateValidationException("request", "required");
        long expectedVersion = version(request.expectedVersion(), "expectedVersion");
        PricingCalculator.Result result = calculator.calculate(request.content());
        lockActive(context, estimateId, "Archived estimates are read-only.");
        EstimateRevisionRecord target = lockedRevision(context, estimateId, revisionId);
        if (target.status().isImmutable()) throw revisionImmutable();
        requireVersion(target.version(), expectedVersion, staleRevision());
        OffsetDateTime now = OffsetDateTime.now();
        if (!repository.replaceDraftRevision(context.businessId(), estimateId, revisionId, expectedVersion, result,
                context.userId(), now)) throw staleRevision();
        repository.incrementVersion(context.businessId(), estimateId, context.userId(), now);
        EstimateRevisionRecord updated = repository.findRevision(context.businessId(), estimateId, revisionId).orElseThrow();
        auditRevision(AuditEventType.ESTIMATE_REVISION_UPDATED, context, updated, null);
        return respond(updated, null);
    }

    @Transactional
    public void deleteRevision(BusinessAccessContext context, UUID estimateId, UUID revisionId, Long expectedVersion) {
        long version = version(expectedVersion, "expectedVersion");
        lockActive(context, estimateId, "Archived estimates are read-only.");
        EstimateRevisionRecord target = lockedRevision(context, estimateId, revisionId);
        if (target.status().isImmutable()) {
            throw new EstimateException("revision_immutable", 409,
                    "Approved and superseded revisions are permanent history and cannot be deleted.");
        }
        requireVersion(target.version(), version, staleRevision());
        if (!repository.deleteDraftRevision(context.businessId(), estimateId, revisionId, version)) throw staleRevision();
        repository.incrementVersion(context.businessId(), estimateId, context.userId(), OffsetDateTime.now());
        auditRevision(AuditEventType.ESTIMATE_REVISION_DELETED, context, target, null);
    }

    /** Approval is a planning decision only; it posts nothing. The previous APPROVED revision becomes SUPERSEDED atomically. */
    @Transactional
    public EstimateRevisionResponse approve(BusinessAccessContext context, UUID estimateId, UUID revisionId,
                                            EstimateTransitionRequest request) {
        long expectedVersion = version(request == null ? null : request.expectedVersion(), "expectedVersion");
        // Lock order: estimate row, then all of its revisions in (revision, id) order.
        lockActive(context, estimateId, "Archived estimates are read-only.");
        List<EstimateRevisionRecord> revisions = repository.lockRevisions(context.businessId(), estimateId);
        EstimateRevisionRecord target = revisions.stream().filter(revision -> revision.id().equals(revisionId)).findFirst()
                .orElseThrow(EstimateService::revisionNotFound);
        if (target.status() != EstimateRevisionStatus.DRAFT) {
            throw new EstimateException("invalid_revision_transition", 409, "Only a DRAFT revision can be approved.");
        }
        requireVersion(target.version(), expectedVersion, staleRevision());
        // The stored outputs must still be exactly what f06-preview-v1 produces for the stored inputs.
        if (!target.matches(calculator.calculate(contentOf(target)))) {
            throw new EstimateException("revision_calculation_mismatch", 409,
                    "This revision's stored calculation no longer matches its inputs. Save the draft again before approving.");
        }
        EstimateRevisionRecord previous = revisions.stream()
                .filter(revision -> revision.status() == EstimateRevisionStatus.APPROVED).findFirst().orElse(null);
        OffsetDateTime now = OffsetDateTime.now();
        if (previous != null && !repository.supersedeApproved(context.businessId(), estimateId, previous.id(), revisionId,
                context.userId(), now)) throw staleRevision();
        if (!repository.approveDraft(context.businessId(), estimateId, revisionId, expectedVersion, context.userId(), now)) {
            throw staleRevision();
        }
        repository.incrementVersion(context.businessId(), estimateId, context.userId(), now);
        EstimateRevisionRecord approved = repository.findRevision(context.businessId(), estimateId, revisionId).orElseThrow();
        auditRevision(AuditEventType.ESTIMATE_REVISION_APPROVED, context, approved, previous);
        return respond(approved, null);
    }

    // ---- shared helpers (also used by EstimateConversionService) ----

    EstimateRecord find(BusinessAccessContext context, UUID estimateId) {
        return repository.findEstimate(context.businessId(), estimateId).orElseThrow(EstimateService::estimateNotFound);
    }

    EstimateRevisionResponse respond(EstimateRevisionRecord revision, EstimateConversionRecord conversion) {
        var issues = ProjectConversionValidator.validate(revision);
        return new EstimateRevisionResponse(revision.id(), revision.estimateId(), revision.businessId(), revision.revision(),
                revision.status(), true, revision.version(), revision.createdByUserId(), revision.updatedByUserId(),
                revision.createdAt(), revision.updatedAt(), revision.approvedByUserId(), revision.approvedAt(),
                revision.supersededByVersionId(), revision.supersededAt(),
                PricingCalculator.response(revision.businessId(), stored(revision)), EstimateConversionResponse.from(conversion),
                new EstimateRevisionResponse.ConversionReadiness(issues.isEmpty(),
                        issues.isEmpty() ? null : ProjectConversionValidator.CODE, issues));
    }

    /** Rebuilds the f06-preview-v1 result from stored exact values, without recalculating anything. */
    public static PricingCalculator.Result stored(EstimateRevisionRecord revision) {
        return new PricingCalculator.Result(EstimateCostCalculator.assemble(revision.lines()), revision.pricingMode(),
                revision.pricingValue(), revision.saleableQuantity(), revision.plannedBatchPrice(), revision.plannedContribution(),
                revision.plannedMargin(), revision.plannedMarkup(), revision.plannedPerUnitPrice());
    }

    private static PricingPreviewRequest contentOf(EstimateRevisionRecord revision) {
        return new PricingPreviewRequest(revision.lines().stream().map(line -> new EstimateLineRequest(
                line.costGroup(), line.description(), EstimateDecimals.decimal(line.quantity()), line.unit(),
                EstimateDecimals.decimal(line.unitCost()))).toList(),
                EstimateDecimals.decimal(revision.saleableQuantity()), revision.pricingMode(), EstimateDecimals.decimal(revision.pricingValue()));
    }

    private UUID insertDraft(BusinessAccessContext context, EstimateRecord estimate, PricingCalculator.Result result, int revision,
                             OffsetDateTime now) {
        UUID id = UUID.randomUUID();
        EstimateRevisionRecord draft = new EstimateRevisionRecord(id, context.businessId(), estimate.id(), revision,
                EstimateRevisionStatus.DRAFT, result.pricingMode(), result.pricingValue(), result.saleableQuantity(),
                result.costs().total(), result.price(), result.contribution(), result.margin(), result.markup(), result.perUnit(),
                PricingCalculator.CALCULATION_VERSION, 0L, context.userId(), context.userId(), now, now, null, null, null, null,
                result.costs().rawLines());
        try {
            repository.insertDraftRevision(draft);
        } catch (DataIntegrityViolationException ex) {
            throw draftExists();
        }
        auditRevision(AuditEventType.ESTIMATE_REVISION_CREATED, context, draft, null);
        return id;
    }

    private EstimateRecord lockActive(BusinessAccessContext context, UUID estimateId, String archivedMessage) {
        EstimateRecord estimate = repository.lockEstimate(context.businessId(), estimateId).orElseThrow(EstimateService::estimateNotFound);
        if (!estimate.status().allowsChanges()) throw new EstimateException("estimate_archived", 409, archivedMessage);
        return estimate;
    }

    private EstimateRevisionRecord lockedRevision(BusinessAccessContext context, UUID estimateId, UUID revisionId) {
        return repository.lockRevisions(context.businessId(), estimateId).stream()
                .filter(revision -> revision.id().equals(revisionId)).findFirst().orElseThrow(EstimateService::revisionNotFound);
    }

    private EstimateDetailResponse detail(BusinessAccessContext context, EstimateRecord estimate) {
        List<EstimateRevisionRecord> revisions = repository.findRevisions(context.businessId(), estimate.id());
        List<EstimateConversionRecord> conversions = repository.findConversions(context.businessId(), estimate.id());
        Map<UUID, EstimateConversionRecord> byRevision = conversions.stream()
                .collect(Collectors.toMap(EstimateConversionRecord::estimateVersionId, Function.identity()));
        return new EstimateDetailResponse(summarize(estimate, revisions, conversions.size()),
                revisions.stream().map(revision -> respond(revision, byRevision.get(revision.id()))).toList(),
                conversions.stream().map(EstimateConversionResponse::from).toList());
    }

    private Map<UUID, EstimateConversionRecord> conversionsByRevision(BusinessAccessContext context, UUID estimateId) {
        return repository.findConversions(context.businessId(), estimateId).stream()
                .collect(Collectors.toMap(EstimateConversionRecord::estimateVersionId, Function.identity()));
    }

    private static EstimateResponse summarize(EstimateRecord estimate, List<EstimateRevisionRecord> revisions, int conversions) {
        return new EstimateResponse(estimate.id(), estimate.businessId(), estimate.code(), estimate.name(), estimate.description(),
                estimate.status(), estimate.baseCurrency(), true, estimate.version(), estimate.createdByUserId(),
                estimate.updatedByUserId(), estimate.createdAt(), estimate.updatedAt(), estimate.archivedByUserId(),
                estimate.archivedAt(), revisions.size(), summary(revisions, EstimateRevisionStatus.APPROVED),
                summary(revisions, EstimateRevisionStatus.DRAFT), conversions,
                revisions.stream().anyMatch(revision -> revision.status().isImmutable()));
    }

    private static EstimateResponse.RevisionSummary summary(List<EstimateRevisionRecord> revisions, EstimateRevisionStatus status) {
        return revisions.stream().filter(revision -> revision.status() == status).findFirst()
                .map(revision -> new EstimateResponse.RevisionSummary(revision.id(), revision.revision(), revision.status(),
                        revision.version(), EstimateDecimals.amount(revision.totalPlannedCost()),
                        EstimateDecimals.amount(revision.plannedBatchPrice()))).orElse(null);
    }

    private void auditEstimate(AuditEventType type, BusinessAccessContext context, EstimateRecord estimate) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code", estimate.code());
        details.put("status", estimate.status().name());
        details.put("version", estimate.version());
        details.put("planningOnly", true);
        audit.logSuccess(type, context.userId(), null, context.businessId(), "estimate", estimate.id().toString(), details);
    }

    private void auditRevision(AuditEventType type, BusinessAccessContext context, EstimateRevisionRecord revision,
                               EstimateRevisionRecord superseded) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("estimateId", revision.estimateId().toString());
        details.put("revision", revision.revision());
        details.put("status", revision.status().name());
        details.put("version", revision.version());
        details.put("totalPlannedCost", EstimateDecimals.amount(revision.totalPlannedCost()));
        details.put("plannedBatchPrice", EstimateDecimals.amount(revision.plannedBatchPrice()));
        details.put("calculationVersion", revision.calculationVersion());
        details.put("planningOnly", true);
        if (superseded != null) {
            details.put("supersededRevisionId", superseded.id().toString());
            details.put("supersededRevision", superseded.revision());
        }
        audit.logSuccess(type, context.userId(), null, context.businessId(), "estimate_version", revision.id().toString(), details);
    }

    private static String code(String raw) {
        if (raw == null || raw.isBlank()) throw new EstimateValidationException("code", "required");
        String code = raw.strip().toUpperCase(Locale.ROOT);
        if (code.length() > 40 || !CODE.matcher(code).matches()) throw new EstimateValidationException("code", "code_invalid");
        return code;
    }

    private static String requiredText(String value, String field, int maximum) {
        if (value == null || value.isBlank()) throw new EstimateValidationException(field, "required");
        return optionalText(value, field, maximum);
    }

    private static String optionalText(String value, String field, int maximum) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > maximum || value.codePoints().anyMatch(Character::isISOControl))
            throw new EstimateValidationException(field, "text_invalid");
        return value.strip();
    }

    private static long version(Long value, String field) {
        if (value == null || value < 0) throw new EstimateValidationException(field, "version_invalid");
        return value;
    }

    private static void requireVersion(long actual, long expected, EstimateException stale) {
        if (actual != expected) throw stale;
    }

    static EstimateException estimateNotFound() { return new EstimateException("estimate_not_found", 404, "Estimate not found."); }
    static EstimateException revisionNotFound() { return new EstimateException("revision_not_found", 404, "Estimate revision not found."); }
    private static EstimateException duplicateCode() {
        return new EstimateException("duplicate_estimate_code", 409, "An estimate with this code already exists.");
    }
    private static EstimateException draftExists() {
        return new EstimateException("draft_revision_already_exists", 409,
                "An open draft revision already exists for this estimate. Edit, approve, or discard that draft first.");
    }
    private static EstimateException revisionImmutable() {
        return new EstimateException("revision_immutable", 409,
                "Approved and superseded revisions are immutable. Create a new revision instead.");
    }
    private static EstimateException staleEstimate() {
        return new EstimateException("stale_estimate_version", 409, "This estimate changed since you loaded it. Reload before trying again.");
    }
    private static EstimateException staleRevision() {
        return new EstimateException("stale_revision_version", 409, "This revision changed since you loaded it. Reload before trying again.");
    }
}
