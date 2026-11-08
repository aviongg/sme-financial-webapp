package com.app.sme_health_backend.commission.service;

import com.app.sme_health_backend.commission.dto.*;
import com.app.sme_health_backend.commission.entity.CommissionEarning;
import com.app.sme_health_backend.commission.entity.CommissionRule;
import com.app.sme_health_backend.commission.entity.SalesAssignment;
import com.app.sme_health_backend.commission.entity.SalespersonProfile;
import com.app.sme_health_backend.commission.exception.CommissionEarningConflictException;
import com.app.sme_health_backend.commission.exception.CommissionEarningNotFoundException;
import com.app.sme_health_backend.commission.exception.CommissionEarningValidationException;
import com.app.sme_health_backend.commission.model.*;
import com.app.sme_health_backend.commission.repository.CommissionAdvanceRepository;
import com.app.sme_health_backend.commission.repository.CommissionEarningRepository;
import com.app.sme_health_backend.commission.repository.CommissionPayoutRepository;
import com.app.sme_health_backend.commission.repository.CommissionRuleRepository;
import com.app.sme_health_backend.commission.repository.SalesAssignmentRepository;
import com.app.sme_health_backend.commission.repository.SalespersonProfileRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.obligation.AllocationStatus;
import com.app.sme_health_backend.obligation.AllocationType;
import com.app.sme_health_backend.obligation.FinancialObligation;
import com.app.sme_health_backend.obligation.FinancialObligationRepository;
import com.app.sme_health_backend.obligation.ObligationPaymentAllocation;
import com.app.sme_health_backend.obligation.ObligationPaymentAllocationRepository;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class CommissionEarningService {

    private final CommissionEarningRepository earningRepository;
    private final CommissionRuleRepository ruleRepository;
    private final CommissionRuleService ruleService;
    private final CommissionCalculationService calculationService;
    private final SalespersonProfileRepository salespersonRepository;
    private final SalesAssignmentRepository assignmentRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final ObligationPaymentAllocationRepository allocationRepository;
    private final FinancialObligationRepository obligationRepository;
    private final CommissionPayoutRepository payoutRepository;
    private final CommissionAdvanceRepository advanceRepository;

    public CommissionEarningService(
            CommissionEarningRepository earningRepository,
            CommissionRuleRepository ruleRepository,
            CommissionRuleService ruleService,
            CommissionCalculationService calculationService,
            SalespersonProfileRepository salespersonRepository,
            SalesAssignmentRepository assignmentRepository,
            CounterpartyRepository counterpartyRepository,
            ObligationPaymentAllocationRepository allocationRepository,
            FinancialObligationRepository obligationRepository
    ) {
        this(earningRepository, ruleRepository, ruleService, calculationService, salespersonRepository, assignmentRepository, counterpartyRepository, allocationRepository, obligationRepository, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CommissionEarningService(
            CommissionEarningRepository earningRepository,
            CommissionRuleRepository ruleRepository,
            CommissionRuleService ruleService,
            CommissionCalculationService calculationService,
            SalespersonProfileRepository salespersonRepository,
            SalesAssignmentRepository assignmentRepository,
            CounterpartyRepository counterpartyRepository,
            ObligationPaymentAllocationRepository allocationRepository,
            FinancialObligationRepository obligationRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) CommissionPayoutRepository payoutRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) CommissionAdvanceRepository advanceRepository
    ) {
        this.earningRepository = earningRepository;
        this.ruleRepository = ruleRepository;
        this.ruleService = ruleService;
        this.calculationService = calculationService;
        this.salespersonRepository = salespersonRepository;
        this.assignmentRepository = assignmentRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.allocationRepository = allocationRepository;
        this.obligationRepository = obligationRepository;
        this.payoutRepository = payoutRepository;
        this.advanceRepository = advanceRepository;
    }

    @Transactional(readOnly = true)
    public List<EligibleCollectionResponse> discoverEligibleCollections(BusinessAccessContext context) {
        Objects.requireNonNull(context, "context must not be null");

        // 1. Fetch effective CASH allocations for this business
        List<ObligationPaymentAllocation> allocations = allocationRepository.findByBusinessId(context.businessId())
                .stream()
                .filter(a -> a.getStatus() == AllocationStatus.EFFECTIVE)
                .filter(a -> a.getAllocationType() == AllocationType.CASH)
                .toList();

        if (allocations.isEmpty()) {
            return List.of();
        }

        // Cache lookups
        Map<UUID, FinancialObligation> obligations = obligationRepository.findByBusinessId(context.businessId())
                .stream()
                .collect(Collectors.toMap(FinancialObligation::getId, o -> o));

        Map<UUID, Counterparty> counterparties = counterpartyRepository.findAllByBusinessIdAndStatusOrderByDisplayNameAsc(
                context.businessId(), com.app.sme_health_backend.counterparty.CounterpartyStatus.ACTIVE
        ).stream().collect(Collectors.toMap(Counterparty::getId, c -> c));

        List<SalesAssignment> allAssignments = assignmentRepository.findByBusinessId(context.businessId());
        Map<UUID, SalespersonProfile> salespersons = salespersonRepository.findByBusinessId(context.businessId())
                .stream().collect(Collectors.toMap(SalespersonProfile::getId, s -> s));

        List<CommissionEarning> existingEarnings = earningRepository.findByBusinessId(context.businessId());
        Map<String, CommissionEarning> earningsByFingerprint = existingEarnings.stream()
                .collect(Collectors.toMap(CommissionEarning::getSourceFingerprint, e -> e, (a, b) -> a));

        List<EligibleCollectionResponse> result = new ArrayList<>();

        for (ObligationPaymentAllocation alloc : allocations) {
            FinancialObligation obligation = obligations.get(alloc.getObligationId());
            if (obligation == null || obligation.getDirection() != ObligationDirection.RECEIVABLE) {
                continue; // Only customer receivables qualify for collection commissions
            }

            Counterparty customer = counterparties.get(obligation.getCounterpartyId());
            String customerName = customer != null ? customer.getDisplayName() : "Unknown Customer";

            // Find salesperson assigned to this customer on the collection date
            LocalDate collectionDate = alloc.getEffectiveDate();
            Optional<SalesAssignment> activeAssignment = allAssignments.stream()
                    .filter(a -> a.getCounterpartyId().equals(obligation.getCounterpartyId()))
                    .filter(a -> !collectionDate.isBefore(a.getEffectiveStartDate()))
                    .filter(a -> a.getEffectiveEndDate() == null || !collectionDate.isAfter(a.getEffectiveEndDate()))
                    .findFirst();

            if (activeAssignment.isEmpty()) {
                continue; // No customer assignment active on collection date
            }

            SalespersonProfile salesperson = salespersons.get(activeAssignment.get().getSalespersonId());
            if (salesperson == null || salesperson.getStatus() != SalespersonStatus.ACTIVE) {
                continue; // Salesperson not active
            }

            // Find applicable commission rule
            Optional<CommissionRule> applicableRule = ruleService.findApplicableRule(
                    context.businessId(), salesperson.getId(), collectionDate
            );

            UUID ruleId = null;
            String ruleName = null;
            CalculationType calcType = null;
            BigDecimal rateOrAmount = null;
            BigDecimal estimatedCommission = BigDecimal.ZERO;

            if (applicableRule.isPresent()) {
                CommissionRule r = applicableRule.get();
                ruleId = r.getId();
                ruleName = r.getRuleName();
                calcType = r.getCalculationType();
                rateOrAmount = r.getCalculationType() == CalculationType.PERCENTAGE ? r.getRatePercentage() : r.getFixedAmount();
                estimatedCommission = calculationService.calculateCommission(r, alloc.getAmount());
            }

            String fingerprint = "ALLOCATION:" + alloc.getId();
            CommissionEarning existingEarning = earningsByFingerprint.get(fingerprint);
            boolean alreadyEarned = existingEarning != null;
            UUID existingEarningId = existingEarning != null ? existingEarning.getId() : null;

            result.add(new EligibleCollectionResponse(
                    alloc.getId(),
                    obligation.getId(),
                    obligation.getCounterpartyId(),
                    customerName,
                    salesperson.getId(),
                    salesperson.getDisplayName(),
                    alloc.getAmount(),
                    collectionDate,
                    obligation.getCurrency(),
                    ruleId,
                    ruleName,
                    calcType,
                    rateOrAmount,
                    estimatedCommission,
                    alreadyEarned,
                    existingEarningId
            ));
        }

        return result.stream()
                .sorted(Comparator.comparing(EligibleCollectionResponse::collectionDate).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public CommissionCalculationPreviewResponse previewCalculation(BusinessAccessContext context, CommissionCalculationRequest request) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(request, "request must not be null");

        UUID customerId = request.counterpartyId();
        UUID salespersonId = request.salespersonId();
        BigDecimal saleAmount = request.saleAmount();
        LocalDate eventDate = request.eventDate() != null ? request.eventDate() : LocalDate.now();
        String currency = request.currency() != null ? request.currency() : "PKR";

        // If source is OBLIGATION_ALLOCATION, hydrate details from database
        if (request.sourceType() == CommissionSourceType.OBLIGATION_ALLOCATION && request.sourceId() != null) {
            UUID allocId;
            try {
                allocId = UUID.fromString(request.sourceId());
            } catch (IllegalArgumentException e) {
                return ineligiblePreview("Invalid allocation UUID: " + request.sourceId());
            }

            Optional<ObligationPaymentAllocation> allocOpt = allocationRepository.findByIdAndBusinessId(allocId, context.businessId());
            if (allocOpt.isEmpty()) {
                return ineligiblePreview("Obligation allocation not found: " + allocId);
            }
            ObligationPaymentAllocation alloc = allocOpt.get();
            if (alloc.getStatus() != AllocationStatus.EFFECTIVE || alloc.getAllocationType() != AllocationType.CASH) {
                return ineligiblePreview("Allocation is not an effective cash collection");
            }

            Optional<FinancialObligation> obligOpt = obligationRepository.findByIdAndBusinessId(alloc.getObligationId(), context.businessId());
            if (obligOpt.isEmpty() || obligOpt.get().getDirection() != ObligationDirection.RECEIVABLE) {
                return ineligiblePreview("Allocation does not belong to a customer receivable");
            }

            FinancialObligation oblig = obligOpt.get();
            customerId = oblig.getCounterpartyId();
            saleAmount = alloc.getAmount();
            eventDate = alloc.getEffectiveDate();
            currency = oblig.getCurrency();

            // Check if already earned
            if (earningRepository.existsByBusinessIdAndSourceFingerprint(context.businessId(), "ALLOCATION:" + alloc.getId())) {
                return ineligiblePreview("Commission has already been earned for this collection");
            }

            // Derive salesperson assignment if not specified
            if (salespersonId == null) {
                final LocalDate colDate = eventDate;
                Optional<SalesAssignment> assignOpt = assignmentRepository.findByBusinessIdAndCounterpartyId(context.businessId(), customerId)
                        .stream()
                        .filter(a -> !colDate.isBefore(a.getEffectiveStartDate()))
                        .filter(a -> a.getEffectiveEndDate() == null || !colDate.isAfter(a.getEffectiveEndDate()))
                        .findFirst();

                if (assignOpt.isEmpty()) {
                    return ineligiblePreview("No salesperson assigned to customer on collection date: " + colDate);
                }
                salespersonId = assignOpt.get().getSalespersonId();
            }
        }

        if (customerId == null) {
            return ineligiblePreview("Counterparty / customer ID is required");
        }
        if (salespersonId == null) {
            return ineligiblePreview("Salesperson ID is required");
        }
        if (saleAmount == null || saleAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ineligiblePreview("Collection amount must be greater than zero");
        }

        Counterparty customer = counterpartyRepository.findByIdAndBusinessId(customerId, context.businessId()).orElse(null);
        String customerName = customer != null ? customer.getDisplayName() : "Unknown Customer";

        SalespersonProfile salesperson = salespersonRepository.findByIdAndBusinessId(salespersonId, context.businessId()).orElse(null);
        if (salesperson == null || salesperson.getStatus() != SalespersonStatus.ACTIVE) {
            return ineligiblePreview("Salesperson not found or is inactive/suspended");
        }

        Optional<CommissionRule> ruleOpt = ruleService.findApplicableRule(context.businessId(), salespersonId, eventDate);
        if (ruleOpt.isEmpty()) {
            return ineligiblePreview("No effective commission rule found for salesperson on date: " + eventDate);
        }

        CommissionRule rule = ruleOpt.get();
        BigDecimal calculatedCommission = calculationService.calculateCommission(rule, saleAmount);

        return new CommissionCalculationPreviewResponse(
                salespersonId,
                salesperson.getDisplayName(),
                customerId,
                customerName,
                rule.getId(),
                rule.getRuleName(),
                rule.getCalculationType(),
                rule.getRatePercentage(),
                rule.getFixedAmount(),
                saleAmount,
                calculatedCommission,
                currency,
                Instant.now(),
                true,
                null
        );
    }

    public CommissionEarningResponse recordEarning(BusinessAccessContext context, CommissionEarningCreateRequest request) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (request.sourceType() == null) {
            throw new CommissionEarningValidationException("sourceType", "Source type is required");
        }
        if (request.sourceId() == null || request.sourceId().isBlank()) {
            throw new CommissionEarningValidationException("sourceId", "Source ID is required");
        }

        UUID salespersonId = request.salespersonId();
        UUID customerId;
        BigDecimal saleAmount;
        LocalDate eventDate;
        String currency;
        String fingerprint;

        if (request.sourceType() == CommissionSourceType.OBLIGATION_ALLOCATION) {
            UUID allocId;
            try {
                allocId = UUID.fromString(request.sourceId().trim());
            } catch (IllegalArgumentException e) {
                throw new CommissionEarningValidationException("sourceId", "Invalid allocation UUID: " + request.sourceId());
            }

            ObligationPaymentAllocation alloc = allocationRepository.findByIdAndBusinessId(allocId, context.businessId())
                    .orElseThrow(() -> new CommissionEarningValidationException("sourceId", "Obligation allocation not found: " + allocId));

            if (alloc.getStatus() != AllocationStatus.EFFECTIVE || alloc.getAllocationType() != AllocationType.CASH) {
                throw new CommissionEarningValidationException("sourceId", "Allocation is not an effective cash collection");
            }

            FinancialObligation obligation = obligationRepository.findByIdAndBusinessId(alloc.getObligationId(), context.businessId())
                    .orElseThrow(() -> new CommissionEarningValidationException("obligation", "Receivable obligation not found for allocation"));

            if (obligation.getDirection() != ObligationDirection.RECEIVABLE) {
                throw new CommissionEarningValidationException("obligation", "Allocation does not belong to a customer receivable");
            }

            fingerprint = "ALLOCATION:" + alloc.getId();
            if (earningRepository.existsByBusinessIdAndSourceFingerprint(context.businessId(), fingerprint)) {
                throw new CommissionEarningConflictException("Commission has already been earned for collection allocation: " + alloc.getId());
            }

            customerId = obligation.getCounterpartyId();
            saleAmount = alloc.getAmount();
            eventDate = alloc.getEffectiveDate();
            currency = obligation.getCurrency();

            // Resolve assigned salesperson on the collection date
            if (salespersonId == null) {
                Optional<SalesAssignment> assignOpt = assignmentRepository.findByBusinessIdAndCounterpartyId(context.businessId(), customerId)
                        .stream()
                        .filter(a -> !eventDate.isBefore(a.getEffectiveStartDate()))
                        .filter(a -> a.getEffectiveEndDate() == null || !eventDate.isAfter(a.getEffectiveEndDate()))
                        .findFirst();

                if (assignOpt.isEmpty()) {
                    throw new CommissionEarningValidationException("salespersonId", "No salesperson assigned to customer on collection date: " + eventDate);
                }
                salespersonId = assignOpt.get().getSalespersonId();
            }
        } else {
            // MANUAL_SALE_DECLARATION
            fingerprint = "MANUAL_SALE:" + request.sourceId().trim();
            if (earningRepository.existsByBusinessIdAndSourceFingerprint(context.businessId(), fingerprint)) {
                throw new CommissionEarningConflictException("Commission has already been earned for source: " + request.sourceId());
            }
            throw new CommissionEarningValidationException("sourceType", "Only OBLIGATION_ALLOCATION source type is currently supported for automatic earning");
        }

        final UUID targetSalespersonId = salespersonId;
        SalespersonProfile salesperson = salespersonRepository.findByIdAndBusinessId(targetSalespersonId, context.businessId())
                .orElseThrow(() -> new CommissionEarningValidationException("salespersonId", "Salesperson not found in this business: " + targetSalespersonId));

        if (salesperson.getStatus() != SalespersonStatus.ACTIVE) {
            throw new CommissionEarningValidationException("salesperson", "Cannot record earnings for inactive or suspended salesperson");
        }

        Counterparty customer = counterpartyRepository.findByIdAndBusinessId(customerId, context.businessId())
                .orElseThrow(() -> new CommissionEarningValidationException("counterpartyId", "Customer not found: " + customerId));

        // Resolve rule
        CommissionRule rule;
        if (request.ruleId() != null) {
            rule = ruleRepository.findByIdAndBusinessId(request.ruleId(), context.businessId())
                    .orElseThrow(() -> new CommissionEarningValidationException("ruleId", "Commission rule not found: " + request.ruleId()));
            if (!rule.isEffectiveOn(eventDate)) {
                throw new CommissionEarningValidationException("ruleId", "Specified commission rule was not effective on collection date: " + eventDate);
            }
        } else {
            rule = ruleService.findApplicableRule(context.businessId(), salesperson.getId(), eventDate)
                    .orElseThrow(() -> new CommissionEarningValidationException("rule", "No effective commission rule found for salesperson on date: " + eventDate));
        }

        BigDecimal commissionAmount = calculationService.calculateCommission(rule, saleAmount);
        BigDecimal rateSnapshot = rule.getCalculationType() == CalculationType.PERCENTAGE ? rule.getRatePercentage() : null;

        CommissionEarning earning = new CommissionEarning(
                context.businessId(),
                salesperson.getId(),
                rule.getId(),
                customer.getId(),
                request.sourceType(),
                request.sourceId(),
                fingerprint,
                saleAmount,
                rateSnapshot,
                commissionAmount,
                currency,
                CommissionEarningStatus.EARNED,
                request.notes() != null ? request.notes().trim() : null
        );

        CommissionEarning saved = earningRepository.save(earning);
        return mapToResponse(saved, salesperson.getDisplayName(), rule.getRuleName(), customer.getDisplayName());
    }

    @Transactional(readOnly = true)
    public List<CommissionEarningResponse> listEarnings(BusinessAccessContext context, UUID salespersonIdFilter, CommissionEarningStatus statusFilter) {
        Objects.requireNonNull(context, "context must not be null");

        UUID effectiveSalespersonId = salespersonIdFilter;

        // RESTRICTED SALESPERSON ROW-LEVEL ENFORCEMENT
        if (context.role() == MembershipRole.SALESPERSON) {
            SalespersonProfile profile = salespersonRepository.findByBusinessIdAndUserId(context.businessId(), context.userId())
                    .orElseThrow(() -> new AccessDeniedException("No salesperson profile linked to user"));

            if (profile.getStatus() != SalespersonStatus.ACTIVE) {
                throw new AccessDeniedException("Salesperson account is inactive or suspended");
            }
            if (salespersonIdFilter != null && !salespersonIdFilter.equals(profile.getId())) {
                throw new AccessDeniedException("Salesperson cannot view earnings for another salesperson");
            }
            effectiveSalespersonId = profile.getId();
        }

        List<CommissionEarning> earnings;
        if (effectiveSalespersonId != null) {
            earnings = earningRepository.findByBusinessIdAndSalespersonId(context.businessId(), effectiveSalespersonId);
        } else {
            earnings = earningRepository.findByBusinessId(context.businessId());
        }

        if (statusFilter != null) {
            earnings = earnings.stream().filter(e -> e.getStatus() == statusFilter).toList();
        }

        Map<UUID, String> salespersonNames = getSalespersonNames(context.businessId(), earnings);
        Map<UUID, String> ruleNames = getRuleNames(context.businessId(), earnings);
        Map<UUID, String> counterpartyNames = getCounterpartyNames(context.businessId(), earnings);

        return earnings.stream()
                .sorted(Comparator.comparing(CommissionEarning::getCreatedAt).reversed())
                .map(e -> mapToResponse(
                        e,
                        salespersonNames.getOrDefault(e.getSalespersonId(), "Unknown Salesperson"),
                        ruleNames.getOrDefault(e.getRuleId(), "Unknown Rule"),
                        counterpartyNames.getOrDefault(e.getCounterpartyId(), "Unknown Customer")
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public CommissionEarningResponse getEarning(BusinessAccessContext context, UUID id) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(id, "id must not be null");

        CommissionEarning earning = earningRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new CommissionEarningNotFoundException("Commission earning not found: " + id));

        // RESTRICTED SALESPERSON ROW-LEVEL ENFORCEMENT
        if (context.role() == MembershipRole.SALESPERSON) {
            SalespersonProfile profile = salespersonRepository.findByBusinessIdAndUserId(context.businessId(), context.userId())
                    .orElseThrow(() -> new AccessDeniedException("No salesperson profile linked to user"));

            if (profile.getStatus() != SalespersonStatus.ACTIVE || !earning.getSalespersonId().equals(profile.getId())) {
                throw new AccessDeniedException("Access denied to commission earning");
            }
        }

        String salespersonName = salespersonRepository.findByIdAndBusinessId(earning.getSalespersonId(), context.businessId())
                .map(SalespersonProfile::getDisplayName).orElse("Unknown Salesperson");
        String ruleName = ruleRepository.findByIdAndBusinessId(earning.getRuleId(), context.businessId())
                .map(CommissionRule::getRuleName).orElse("Unknown Rule");
        String counterpartyName = counterpartyRepository.findByIdAndBusinessId(earning.getCounterpartyId(), context.businessId())
                .map(Counterparty::getDisplayName).orElse("Unknown Customer");

        return mapToResponse(earning, salespersonName, ruleName, counterpartyName);
    }

    @Transactional(readOnly = true)
    public CommissionSummaryResponse getSummary(BusinessAccessContext context, UUID salespersonIdFilter) {
        Objects.requireNonNull(context, "context must not be null");

        UUID effectiveSalespersonId = salespersonIdFilter;
        if (context.role() == MembershipRole.SALESPERSON) {
            SalespersonProfile profile = salespersonRepository.findByBusinessIdAndUserId(context.businessId(), context.userId())
                    .orElseThrow(() -> new AccessDeniedException("No salesperson profile linked to user"));

            if (profile.getStatus() != SalespersonStatus.ACTIVE) {
                throw new AccessDeniedException("Salesperson account is inactive or suspended");
            }
            if (salespersonIdFilter != null && !salespersonIdFilter.equals(profile.getId())) {
                throw new AccessDeniedException("Salesperson cannot view summary for another salesperson");
            }
            effectiveSalespersonId = profile.getId();
        }

        List<CommissionEarning> list;
        if (effectiveSalespersonId != null) {
            list = earningRepository.findByBusinessIdAndSalespersonId(context.businessId(), effectiveSalespersonId);
        } else {
            list = earningRepository.findByBusinessId(context.businessId());
        }

        BigDecimal totalEarned = BigDecimal.ZERO;
        BigDecimal totalApproved = BigDecimal.ZERO;
        long earningsCount = 0;
        long approvedCount = 0;
        long settledCount = 0;

        for (CommissionEarning e : list) {
            if (e.getStatus() == CommissionEarningStatus.VOID || e.getStatus() == CommissionEarningStatus.REVERSED) continue;
            earningsCount++;
            totalEarned = totalEarned.add(e.getCommissionAmount());
            if (e.getStatus() == CommissionEarningStatus.APPROVED || e.getStatus() == CommissionEarningStatus.PARTIALLY_SETTLED || e.getStatus() == CommissionEarningStatus.SETTLED || e.getStatus() == CommissionEarningStatus.PAID) {
                approvedCount++;
                totalApproved = totalApproved.add(e.getApprovedAmount() != null ? e.getApprovedAmount() : e.getCommissionAmount());
            }
            if (e.getStatus() == CommissionEarningStatus.SETTLED || e.getStatus() == CommissionEarningStatus.PAID) {
                settledCount++;
            }
        }

        BigDecimal totalAdvancesPaid = (effectiveSalespersonId != null)
                ? (advanceRepository != null ? advanceRepository.sumTotalAdvancesBySalesperson(context.businessId(), effectiveSalespersonId) : BigDecimal.ZERO)
                : (advanceRepository != null ? advanceRepository.sumTotalAdvances(context.businessId()) : BigDecimal.ZERO);

        BigDecimal totalAdvancesApplied = (effectiveSalespersonId != null)
                ? (advanceRepository != null ? advanceRepository.sumTotalAdvancesAppliedBySalesperson(context.businessId(), effectiveSalespersonId) : BigDecimal.ZERO)
                : (advanceRepository != null ? advanceRepository.sumTotalAdvancesApplied(context.businessId()) : BigDecimal.ZERO);

        BigDecimal totalCashPaid = (effectiveSalespersonId != null)
                ? (payoutRepository != null ? payoutRepository.sumTotalCashPayoutsBySalesperson(context.businessId(), effectiveSalespersonId) : BigDecimal.ZERO)
                : (payoutRepository != null ? payoutRepository.sumTotalCashPayouts(context.businessId()) : BigDecimal.ZERO);

        BigDecimal totalSettled = (effectiveSalespersonId != null)
                ? (payoutRepository != null ? payoutRepository.sumEffectiveSettlementsBySalesperson(context.businessId(), effectiveSalespersonId) : BigDecimal.ZERO)
                : totalAdvancesApplied.add(totalCashPaid);

        BigDecimal totalOutstandingPayable = totalApproved.subtract(totalSettled);
        if (totalOutstandingPayable.compareTo(BigDecimal.ZERO) < 0) totalOutstandingPayable = BigDecimal.ZERO;

        BigDecimal totalPaidDisplay = totalCashPaid.add(totalAdvancesApplied);
        long advancesCount = (effectiveSalespersonId != null)
                ? (advanceRepository != null ? advanceRepository.findByBusinessIdAndSalespersonId(context.businessId(), effectiveSalespersonId).size() : 0)
                : (advanceRepository != null ? advanceRepository.findByBusinessId(context.businessId()).size() : 0);

        return new CommissionSummaryResponse(
                totalEarned.setScale(2, java.math.RoundingMode.HALF_UP),
                totalApproved.setScale(2, java.math.RoundingMode.HALF_UP),
                totalAdvancesPaid.setScale(2, java.math.RoundingMode.HALF_UP),
                totalAdvancesApplied.setScale(2, java.math.RoundingMode.HALF_UP),
                totalCashPaid.setScale(2, java.math.RoundingMode.HALF_UP),
                totalOutstandingPayable.setScale(2, java.math.RoundingMode.HALF_UP),
                totalPaidDisplay.setScale(2, java.math.RoundingMode.HALF_UP),
                earningsCount,
                approvedCount,
                settledCount,
                advancesCount,
                "PKR"
        );
    }

    private CommissionCalculationPreviewResponse ineligiblePreview(String reason) {
        return new CommissionCalculationPreviewResponse(
                null, null, null, null, null, null, null, null, null, null,
                BigDecimal.ZERO, "PKR", Instant.now(), false, reason
        );
    }

    private Map<UUID, String> getSalespersonNames(UUID businessId, List<CommissionEarning> earnings) {
        Set<UUID> ids = earnings.stream().map(CommissionEarning::getSalespersonId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return salespersonRepository.findAllById(ids).stream()
                .filter(s -> s.getBusinessId().equals(businessId))
                .collect(Collectors.toMap(SalespersonProfile::getId, SalespersonProfile::getDisplayName));
    }

    private Map<UUID, String> getRuleNames(UUID businessId, List<CommissionEarning> earnings) {
        Set<UUID> ids = earnings.stream().map(CommissionEarning::getRuleId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return ruleRepository.findAllById(ids).stream()
                .filter(r -> r.getBusinessId().equals(businessId))
                .collect(Collectors.toMap(CommissionRule::getId, CommissionRule::getRuleName));
    }

    private Map<UUID, String> getCounterpartyNames(UUID businessId, List<CommissionEarning> earnings) {
        Set<UUID> ids = earnings.stream().map(CommissionEarning::getCounterpartyId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return counterpartyRepository.findAllById(ids).stream()
                .filter(c -> c.getBusinessId().equals(businessId))
                .collect(Collectors.toMap(Counterparty::getId, Counterparty::getDisplayName));
    }

    private CommissionEarningResponse mapToResponse(CommissionEarning e, String salespersonName, String ruleName, String counterpartyName) {
        BigDecimal approvedAmt = e.getApprovedAmount() != null ? e.getApprovedAmount() : e.getCommissionAmount();
        BigDecimal settledAmt = payoutRepository != null ? payoutRepository.sumEffectiveSettlementsByEarning(e.getBusinessId(), e.getId()) : BigDecimal.ZERO;
        BigDecimal remaining = approvedAmt.subtract(settledAmt);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        return new CommissionEarningResponse(
                e.getId(),
                e.getBusinessId(),
                e.getSalespersonId(),
                salespersonName,
                e.getRuleId(),
                ruleName,
                e.getCounterpartyId(),
                counterpartyName,
                e.getSourceType(),
                e.getSourceId(),
                e.getSourceFingerprint(),
                e.getSaleAmount(),
                e.getCommissionRateSnapshot(),
                e.getCommissionAmount(),
                e.getApprovedAmount(),
                settledAmt,
                remaining,
                e.getCurrency(),
                e.getStatus(),
                e.getApprovedAt(),
                e.getApprovedByUserId(),
                e.getPaidAt(),
                e.getNotes(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
