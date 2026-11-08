package com.app.sme_health_backend.commission.service;

import com.app.sme_health_backend.commission.dto.CommissionRuleCreateRequest;
import com.app.sme_health_backend.commission.dto.CommissionRuleResponse;
import com.app.sme_health_backend.commission.entity.CommissionRule;
import com.app.sme_health_backend.commission.entity.SalespersonProfile;
import com.app.sme_health_backend.commission.exception.CommissionRuleNotFoundException;
import com.app.sme_health_backend.commission.exception.CommissionRuleValidationException;
import com.app.sme_health_backend.commission.model.CalculationType;
import com.app.sme_health_backend.commission.model.CommissionRuleStatus;
import com.app.sme_health_backend.commission.model.EarningBasis;
import com.app.sme_health_backend.commission.repository.CommissionRuleRepository;
import com.app.sme_health_backend.commission.repository.SalespersonProfileRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class CommissionRuleService {

    private final CommissionRuleRepository ruleRepository;
    private final SalespersonProfileRepository salespersonRepository;

    public CommissionRuleService(
            CommissionRuleRepository ruleRepository,
            SalespersonProfileRepository salespersonRepository
    ) {
        this.ruleRepository = ruleRepository;
        this.salespersonRepository = salespersonRepository;
    }

    public CommissionRuleResponse createRule(BusinessAccessContext context, CommissionRuleCreateRequest request) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(request, "request must not be null");

        String ruleName = request.ruleName() != null ? request.ruleName().trim() : "";
        if (ruleName.isBlank()) {
            throw new CommissionRuleValidationException("ruleName", "Rule name is required");
        }

        if (ruleRepository.existsByBusinessIdAndRuleNameIgnoreCase(context.businessId(), ruleName)) {
            throw new CommissionRuleValidationException("ruleName", "A commission rule with this name already exists in this business");
        }

        if (request.calculationType() == null) {
            throw new CommissionRuleValidationException("calculationType", "Calculation type is required");
        }

        BigDecimal ratePercentage = null;
        BigDecimal fixedAmount = null;

        if (request.calculationType() == CalculationType.PERCENTAGE) {
            ratePercentage = request.ratePercentage();
            if (ratePercentage == null || ratePercentage.compareTo(BigDecimal.ZERO) <= 0 || ratePercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new CommissionRuleValidationException("ratePercentage", "Percentage rate must be greater than 0% and at most 100%");
            }
        } else if (request.calculationType() == CalculationType.FIXED) {
            fixedAmount = request.fixedAmount();
            if (fixedAmount == null || fixedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CommissionRuleValidationException("fixedAmount", "Fixed commission amount must be greater than zero");
            }
        }

        if (request.effectiveStartDate() == null) {
            throw new CommissionRuleValidationException("effectiveStartDate", "Effective start date is required");
        }

        if (request.effectiveEndDate() != null && request.effectiveEndDate().isBefore(request.effectiveStartDate())) {
            throw new CommissionRuleValidationException("effectiveEndDate", "Effective end date cannot precede start date");
        }

        String salespersonName = null;
        if (request.salespersonId() != null) {
            SalespersonProfile profile = salespersonRepository.findByIdAndBusinessId(request.salespersonId(), context.businessId())
                    .orElseThrow(() -> new CommissionRuleValidationException("salespersonId", "Salesperson not found in this business: " + request.salespersonId()));
            salespersonName = profile.getDisplayName();
        }

        EarningBasis basis = request.earningBasis() != null ? request.earningBasis() : EarningBasis.COLLECTION_BASED;
        String currency = request.currency() != null && !request.currency().isBlank() ? request.currency().trim().toUpperCase(Locale.ROOT) : "PKR";

        CommissionRule rule = new CommissionRule(
                context.businessId(),
                request.salespersonId(),
                ruleName,
                request.calculationType(),
                basis,
                ratePercentage,
                fixedAmount,
                currency,
                request.effectiveStartDate(),
                request.effectiveEndDate()
        );

        CommissionRule saved = ruleRepository.save(rule);
        return mapToResponse(saved, salespersonName);
    }

    @Transactional(readOnly = true)
    public List<CommissionRuleResponse> listRules(BusinessAccessContext context, CommissionRuleStatus statusFilter) {
        Objects.requireNonNull(context, "context must not be null");

        List<CommissionRule> rules;
        if (statusFilter != null) {
            rules = ruleRepository.findByBusinessIdAndStatus(context.businessId(), statusFilter);
        } else {
            rules = ruleRepository.findByBusinessId(context.businessId());
        }

        Map<UUID, String> salespersonNames = getSalespersonNames(context.businessId(), rules);

        return rules.stream()
                .sorted(Comparator.comparing(CommissionRule::getCreatedAt).reversed())
                .map(r -> mapToResponse(r, r.getSalespersonId() != null ? salespersonNames.get(r.getSalespersonId()) : null))
                .toList();
    }

    @Transactional(readOnly = true)
    public CommissionRuleResponse getRule(BusinessAccessContext context, UUID id) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(id, "id must not be null");

        CommissionRule rule = ruleRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new CommissionRuleNotFoundException("Commission rule not found: " + id));

        String salespersonName = null;
        if (rule.getSalespersonId() != null) {
            salespersonName = salespersonRepository.findByIdAndBusinessId(rule.getSalespersonId(), context.businessId())
                    .map(SalespersonProfile::getDisplayName)
                    .orElse(null);
        }

        return mapToResponse(rule, salespersonName);
    }

    public CommissionRuleResponse updateRuleStatus(BusinessAccessContext context, UUID id, CommissionRuleStatus status) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");

        CommissionRule rule = ruleRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new CommissionRuleNotFoundException("Commission rule not found: " + id));

        rule.setStatus(status);
        CommissionRule saved = ruleRepository.save(rule);

        String salespersonName = null;
        if (saved.getSalespersonId() != null) {
            salespersonName = salespersonRepository.findByIdAndBusinessId(saved.getSalespersonId(), context.businessId())
                    .map(SalespersonProfile::getDisplayName)
                    .orElse(null);
        }

        return mapToResponse(saved, salespersonName);
    }

    @Transactional(readOnly = true)
    public Optional<CommissionRule> findApplicableRule(UUID businessId, UUID salespersonId, LocalDate eventDate) {
        List<CommissionRule> activeRules = ruleRepository.findByBusinessIdAndStatus(businessId, CommissionRuleStatus.ACTIVE);

        // 1. First check if a salesperson-specific rule is effective on the event date
        if (salespersonId != null) {
            Optional<CommissionRule> specific = activeRules.stream()
                    .filter(r -> salespersonId.equals(r.getSalespersonId()))
                    .filter(r -> r.isEffectiveOn(eventDate))
                    .max(Comparator.comparing(CommissionRule::getCreatedAt));

            if (specific.isPresent()) {
                return specific;
            }
        }

        // 2. Fall back to business-wide default rule (salespersonId is null)
        return activeRules.stream()
                .filter(r -> r.getSalespersonId() == null)
                .filter(r -> r.isEffectiveOn(eventDate))
                .max(Comparator.comparing(CommissionRule::getCreatedAt));
    }

    private Map<UUID, String> getSalespersonNames(UUID businessId, List<CommissionRule> rules) {
        Set<UUID> ids = rules.stream()
                .map(CommissionRule::getSalespersonId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) {
            return Map.of();
        }

        return salespersonRepository.findAllById(ids).stream()
                .filter(s -> s.getBusinessId().equals(businessId))
                .collect(Collectors.toMap(SalespersonProfile::getId, SalespersonProfile::getDisplayName));
    }

    private CommissionRuleResponse mapToResponse(CommissionRule rule, String salespersonName) {
        return new CommissionRuleResponse(
                rule.getId(),
                rule.getBusinessId(),
                rule.getSalespersonId(),
                salespersonName,
                rule.getRuleName(),
                rule.getCalculationType(),
                rule.getEarningBasis(),
                rule.getRatePercentage(),
                rule.getFixedAmount(),
                rule.getCurrency(),
                rule.getEffectiveStartDate(),
                rule.getEffectiveEndDate(),
                rule.getStatus(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
