package com.app.sme_health_backend.projectattribution.service;

import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.commission.facade.CommissionCostEvidence;
import com.app.sme_health_backend.commission.facade.CommissionCostEvidenceFacade;
import com.app.sme_health_backend.fx.dto.FxSettlementReconciliationResponse;
import com.app.sme_health_backend.fx.dto.FxSettlementReportFilter;
import com.app.sme_health_backend.fx.dto.FxSettlementReportItem;
import com.app.sme_health_backend.fx.facade.FxFinancialEvidenceFacade;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.projectattribution.dto.AttributionCreateRequest;
import com.app.sme_health_backend.projectattribution.dto.AttributionResponse;
import com.app.sme_health_backend.projectattribution.dto.AttributionReverseRequest;
import com.app.sme_health_backend.projectattribution.dto.EligibleSourceItem;
import com.app.sme_health_backend.projectattribution.entity.ProjectFinancialAttribution;
import com.app.sme_health_backend.projectattribution.exception.AttributionNotFoundException;
import com.app.sme_health_backend.projectattribution.exception.AttributionValidationException;
import com.app.sme_health_backend.projectattribution.model.*;
import com.app.sme_health_backend.projects.model.ProjectRecord;
import com.app.sme_health_backend.projects.repository.ProjectRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class ProjectFinancialAttributionService {

    private final com.app.sme_health_backend.projectattribution.repository.ProjectFinancialAttributionRepository attributionRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final ProjectRepository projectRepository;
    private final CommissionCostEvidenceFacade commissionCostEvidenceFacade;
    private final FxFinancialEvidenceFacade fxFinancialEvidenceFacade;
    private final JdbcTemplate jdbcTemplate;

    public ProjectFinancialAttributionService(
            com.app.sme_health_backend.projectattribution.repository.ProjectFinancialAttributionRepository attributionRepository,
            FinancialTransactionRepository transactionRepository,
            ProjectRepository projectRepository,
            CommissionCostEvidenceFacade commissionCostEvidenceFacade,
            FxFinancialEvidenceFacade fxFinancialEvidenceFacade,
            JdbcTemplate jdbcTemplate
    ) {
        this.attributionRepository = attributionRepository;
        this.transactionRepository = transactionRepository;
        this.projectRepository = projectRepository;
        this.commissionCostEvidenceFacade = commissionCostEvidenceFacade;
        this.fxFinancialEvidenceFacade = fxFinancialEvidenceFacade;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<EligibleSourceItem> getEligibleSources(UUID businessId, UUID projectId) {
        ProjectRecord project = projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found with id: " + projectId));

        List<EligibleSourceItem> eligibleItems = new ArrayList<>();

        // 1. Discover eligible financial transactions
        String txSql = """
                SELECT t.id, t.event_type, t.movement_direction, t.amount, t.currency,
                       t.effective_date, t.description, cp.display_name AS counterparty_name
                FROM financial_transactions t
                LEFT JOIN counterparties cp ON cp.id = t.counterparty_id AND cp.business_id = t.business_id
                WHERE t.business_id = ?
                  AND t.event_type IN ('OPERATING_EXPENSE_PAYMENT', 'PAYABLE_SETTLEMENT', 'DIRECT_SALE_RECEIPT', 'RECEIVABLE_COLLECTION')
                  AND t.reversed_transaction_id IS NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM financial_transactions r
                      WHERE r.business_id = t.business_id
                        AND r.reversed_transaction_id = t.id
                  )
                ORDER BY t.effective_date DESC, t.created_at DESC
                """;

        jdbcTemplate.query(txSql, rs -> {
            UUID txId = rs.getObject("id", UUID.class);
            BigDecimal totalAmount = rs.getBigDecimal("amount");
            String currency = rs.getString("currency").trim();
            String directionStr = rs.getString("movement_direction").trim();
            AttributionDirection direction = directionStr.equals("INCREASE") ? AttributionDirection.INFLOW : AttributionDirection.OUTFLOW;
            LocalDate eventDate = rs.getDate("effective_date").toLocalDate();
            String description = rs.getString("description");
            String cpName = rs.getString("counterparty_name");

            BigDecimal activeAttributed = attributionRepository.sumAttributedAmountBySource(
                    businessId, AttributionSourceType.TRANSACTION, txId, AttributionStatus.ACTIVE
            );
            BigDecimal remaining = totalAmount.subtract(activeAttributed);
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                eligibleItems.add(new EligibleSourceItem(
                        AttributionSourceType.TRANSACTION,
                        txId,
                        direction,
                        currency,
                        totalAmount,
                        activeAttributed,
                        remaining,
                        eventDate,
                        description,
                        cpName,
                        EconomicClassification.POSTED_CASH_ACTUAL,
                        txId.toString().substring(0, 8)
                ));
            }
        }, businessId);

        // 2. Discover eligible commission cost evidence
        try {
            BusinessAccessContext authContext = new BusinessAccessContext(UUID.randomUUID(), businessId, MembershipRole.ACCOUNTANT);
            List<CommissionCostEvidence> commissions = commissionCostEvidenceFacade.listCommissionCostEvidence(
                    authContext, null, null, null, null
            );
            for (CommissionCostEvidence c : commissions) {
                if ("FULLY_REVERSED".equalsIgnoreCase(c.reversalState())) {
                    continue;
                }
                BigDecimal totalAmount = c.approvedAmount() != null && c.approvedAmount().compareTo(BigDecimal.ZERO) > 0
                        ? c.approvedAmount() : c.earnedAmount();
                if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal activeAttributed = attributionRepository.sumAttributedAmountBySource(
                        businessId, AttributionSourceType.COMMISSION_RECOGNITION, c.earningId(), AttributionStatus.ACTIVE
                );
                BigDecimal remaining = totalAmount.subtract(activeAttributed);
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    LocalDate eventDate = c.recognizedAt() != null
                            ? LocalDate.ofInstant(c.recognizedAt(), ZoneId.systemDefault()) : LocalDate.now();
                    eligibleItems.add(new EligibleSourceItem(
                            AttributionSourceType.COMMISSION_RECOGNITION,
                            c.earningId(),
                            AttributionDirection.OUTFLOW,
                            c.currency() != null ? c.currency() : "PKR",
                            totalAmount,
                            activeAttributed,
                            remaining,
                            eventDate,
                            "Commission for " + (c.salespersonName() != null ? c.salespersonName() : "Salesperson"),
                            c.counterpartyName(),
                            EconomicClassification.RECOGNIZED_ECONOMIC_COST,
                            c.earningId().toString().substring(0, 8)
                    ));
                }
            }
        } catch (Exception ignored) {
            // Facade access may return empty if no commissions exist
        }

        // 3. Discover eligible FX settlement fee & difference evidence
        try {
            List<FxSettlementReportItem> settlements = fxFinancialEvidenceFacade.getSettlementsReport(
                    businessId, new FxSettlementReportFilter(null, null, null, null, null, null, null, 100, 0)
            );
            for (FxSettlementReportItem s : settlements) {
                if (s.feeAmount() != null && s.feeAmount().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal activeAttributed = attributionRepository.sumAttributedAmountBySource(
                            businessId, AttributionSourceType.FX_DIFFERENCE, s.settlementId(), AttributionStatus.ACTIVE
                    );
                    BigDecimal remaining = s.feeAmount().subtract(activeAttributed);
                    if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                        eligibleItems.add(new EligibleSourceItem(
                                AttributionSourceType.FX_DIFFERENCE,
                                s.settlementId(),
                                AttributionDirection.OUTFLOW,
                                s.feeCurrency() != null ? s.feeCurrency() : s.accountCurrency(),
                                s.feeAmount(),
                                activeAttributed,
                                remaining,
                                s.settlementDate(),
                                "FX Settlement Bank Fee (" + s.obligationCurrency() + " -> " + s.accountCurrency() + ")",
                                s.counterpartyName(),
                                EconomicClassification.NONCASH_FINANCIAL_EVIDENCE,
                                s.settlementId().toString().substring(0, 8)
                        ));
                    }
                }
            }
        } catch (Exception ignored) {
            // Facade access may return empty if no FX settlements exist
        }

        return eligibleItems;
    }

    @Transactional
    public AttributionResponse createAttribution(
            UUID businessId,
            UUID projectId,
            AttributionCreateRequest request,
            UUID userId,
            String idempotencyKey
    ) {
        ProjectRecord project = projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found with id: " + projectId));

        // 1. Enforce Idempotency
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<ProjectFinancialAttribution> existing = attributionRepository.findByBusinessIdAndIdempotencyKey(businessId, idempotencyKey.trim());
            if (existing.isPresent()) {
                ProjectFinancialAttribution e = existing.get();
                if (e.getProjectId().equals(projectId)
                        && e.getSourceType() == request.sourceType()
                        && e.getSourceId().equals(request.sourceId())
                        && e.getAttributedAmount().compareTo(request.amount()) == 0
                        && e.getCostCategory() == request.costCategory()) {
                    return AttributionResponse.fromEntity(e);
                }
                throw new AttributionValidationException("Idempotency key reused with conflicting attribution payload");
            }
        }

        // 2. Validate and Lock Source
        BigDecimal sourceEligibleAmount;
        String sourceCurrency;
        AttributionDirection direction;
        LocalDate sourceEventDate;
        EconomicClassification economicClassification;
        String sourceProvenance;

        if (request.sourceType() == AttributionSourceType.TRANSACTION) {
            FinancialTransaction tx = transactionRepository.findForUpdateByIdAndBusinessId(request.sourceId(), businessId)
                    .orElseThrow(() -> new AttributionNotFoundException("Financial transaction not found with id: " + request.sourceId()));

            if (tx.getEventType() == TransactionEventType.INTERNAL_TRANSFER) {
                throw new AttributionValidationException("Internal transfers cannot be attributed as project financial costs or revenues");
            }
            if (tx.getReversedTransactionId() != null) {
                throw new AttributionValidationException("Reversal transactions cannot be attributed as project financial costs");
            }
            Optional<FinancialTransaction> reversalTx = transactionRepository.findByReversedTransactionIdAndBusinessId(tx.getId(), businessId);
            if (reversalTx.isPresent()) {
                throw new AttributionValidationException("Transaction has been reversed and cannot be attributed as active financial evidence");
            }

            sourceEligibleAmount = tx.getAmount();
            sourceCurrency = tx.getCurrency().trim();
            direction = tx.getMovementDirection() == MovementDirection.INCREASE ? AttributionDirection.INFLOW : AttributionDirection.OUTFLOW;
            sourceEventDate = tx.getEffectiveDate();
            economicClassification = EconomicClassification.POSTED_CASH_ACTUAL;
            sourceProvenance = "TRANSACTION:" + tx.getEventType().name() + ":" + tx.getId();
        } else if (request.sourceType() == AttributionSourceType.COMMISSION_RECOGNITION) {
            BusinessAccessContext authContext = new BusinessAccessContext(userId != null ? userId : UUID.randomUUID(), businessId, MembershipRole.ACCOUNTANT);
            CommissionCostEvidence evidence = commissionCostEvidenceFacade.getCommissionCostEvidence(authContext, request.sourceId())
                    .orElseThrow(() -> new AttributionNotFoundException("Commission cost evidence not found with id: " + request.sourceId()));

            if ("FULLY_REVERSED".equalsIgnoreCase(evidence.reversalState())) {
                throw new AttributionValidationException("Commission earning has been fully reversed and cannot be attributed");
            }

            sourceEligibleAmount = evidence.approvedAmount() != null && evidence.approvedAmount().compareTo(BigDecimal.ZERO) > 0
                    ? evidence.approvedAmount() : evidence.earnedAmount();
            sourceCurrency = evidence.currency() != null ? evidence.currency().trim() : "PKR";
            direction = AttributionDirection.OUTFLOW;
            sourceEventDate = evidence.recognizedAt() != null
                    ? LocalDate.ofInstant(evidence.recognizedAt(), ZoneId.systemDefault()) : LocalDate.now();
            economicClassification = EconomicClassification.RECOGNIZED_ECONOMIC_COST;
            sourceProvenance = "COMMISSION:" + evidence.earningId();
        } else if (request.sourceType() == AttributionSourceType.FX_DIFFERENCE) {
            FxSettlementReconciliationResponse settlement = fxFinancialEvidenceFacade.getSettlementReconciliation(businessId, request.sourceId())
                    .orElseThrow(() -> new AttributionNotFoundException("FX settlement evidence not found with id: " + request.sourceId()));

            sourceEligibleAmount = settlement.feeAmount() != null && settlement.feeAmount().compareTo(BigDecimal.ZERO) > 0
                    ? settlement.feeAmount()
                    : (settlement.realizedFxDifference() != null ? settlement.realizedFxDifference().abs() : BigDecimal.ZERO);
            if (sourceEligibleAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new AttributionValidationException("FX settlement has no eligible fee or realized difference amount");
            }

            sourceCurrency = settlement.feeCurrency() != null ? settlement.feeCurrency() : settlement.accountCurrency();
            direction = (settlement.realizedFxDifference() != null && settlement.realizedFxDifference().compareTo(BigDecimal.ZERO) < 0)
                    ? AttributionDirection.OUTFLOW : AttributionDirection.INFLOW;
            sourceEventDate = settlement.settlementDate();
            economicClassification = EconomicClassification.NONCASH_FINANCIAL_EVIDENCE;
            sourceProvenance = "FX_SETTLEMENT:" + settlement.settlementId();
        } else {
            throw new AttributionValidationException("Unsupported attribution source type: " + request.sourceType());
        }

        // 3. Enforce Split Allocation Limits Across All Projects
        BigDecimal currentActiveAttributed = attributionRepository.sumAttributedAmountBySource(
                businessId, request.sourceType(), request.sourceId(), AttributionStatus.ACTIVE
        );
        BigDecimal remainingEligible = sourceEligibleAmount.subtract(currentActiveAttributed);
        if (request.amount().compareTo(remainingEligible) > 0) {
            throw new AttributionValidationException(
                    "Attributed amount (" + request.amount() + ") exceeds remaining eligible source amount (" + remainingEligible + ") for source " + request.sourceId()
            );
        }

        // 4. Currency and Rate Conversion Validation
        String projectCurrency = project.baseCurrency() != null ? project.baseCurrency().trim() : "PKR";
        BigDecimal exchangeRate = request.exchangeRate();
        BigDecimal baseAmount;

        if (sourceCurrency.equalsIgnoreCase(projectCurrency)) {
            exchangeRate = BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP);
            baseAmount = request.amount().setScale(4, RoundingMode.HALF_UP);
        } else {
            if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
                throw new AttributionValidationException(
                        "Foreign currency source (" + sourceCurrency + ") requires historical exchange rate evidence to attribute to " + projectCurrency + " project"
                );
            }
            baseAmount = request.amount().multiply(exchangeRate).setScale(4, RoundingMode.HALF_UP);
        }

        // 5. Persist Attribution
        ProjectFinancialAttribution attribution = new ProjectFinancialAttribution();
        attribution.setBusinessId(businessId);
        attribution.setProjectId(projectId);
        attribution.setSourceType(request.sourceType());
        attribution.setSourceId(request.sourceId());
        attribution.setSourceDirection(direction);
        attribution.setSourceCurrency(sourceCurrency);
        attribution.setSourceEligibleAmount(sourceEligibleAmount);
        attribution.setAttributedAmount(request.amount().setScale(4, RoundingMode.HALF_UP));
        attribution.setAttributedCurrency(sourceCurrency);
        attribution.setCostCategory(request.costCategory());
        attribution.setEconomicClassification(economicClassification);
        attribution.setSourceEventDate(sourceEventDate);
        attribution.setAttributionDate(request.attributionDate() != null ? request.attributionDate() : LocalDate.now());
        attribution.setNotes(request.notes());
        attribution.setStatus(AttributionStatus.ACTIVE);
        attribution.setIdempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey.trim() : null);
        attribution.setSourceProvenance(sourceProvenance);
        attribution.setExchangeRate(exchangeRate);
        if (sourceCurrency.equalsIgnoreCase(projectCurrency)) {
            attribution.setRateSource("BASE_CURRENCY_IDENTITY");
            attribution.setRateConvention("1_TO_1");
            attribution.setRateVerified(true);
            attribution.setRateDate(sourceEventDate);
        } else {
            attribution.setRateSource("DECLARED_USER_ASSUMPTION");
            attribution.setRateConvention("MULTIPLY_SOURCE_TO_BASE");
            attribution.setRateVerified(false);
            attribution.setRateDate(request.attributionDate() != null ? request.attributionDate() : LocalDate.now());
        }
        attribution.setBaseAmount(baseAmount);
        attribution.setCreatedByUserId(userId);
        attribution.setCreatedAt(Instant.now());

        ProjectFinancialAttribution saved = attributionRepository.save(attribution);
        return AttributionResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<AttributionResponse> listAttributions(UUID businessId, UUID projectId) {
        projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found with id: " + projectId));

        return attributionRepository.findByBusinessIdAndProjectIdOrderByAttributionDateDescCreatedAtDesc(businessId, projectId)
                .stream()
                .map(AttributionResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttributionResponse getAttributionDetail(UUID businessId, UUID projectId, UUID attributionId) {
        ProjectFinancialAttribution attribution = attributionRepository.findByBusinessIdAndProjectIdAndId(businessId, projectId, attributionId)
                .orElseThrow(() -> new AttributionNotFoundException("Attribution not found with id: " + attributionId));
        return AttributionResponse.fromEntity(attribution);
    }

    @Transactional
    public AttributionResponse reverseAttribution(
            UUID businessId,
            UUID projectId,
            UUID attributionId,
            AttributionReverseRequest request,
            UUID userId
    ) {
        ProjectFinancialAttribution attribution = attributionRepository.findByBusinessIdAndProjectIdAndId(businessId, projectId, attributionId)
                .orElseThrow(() -> new AttributionNotFoundException("Attribution not found with id: " + attributionId));

        if (attribution.getStatus() == AttributionStatus.REVERSED) {
            throw new AttributionValidationException("Attribution is already reversed");
        }

        attribution.setStatus(AttributionStatus.REVERSED);
        attribution.setReversalReason(request.reversalReason());
        attribution.setReversedAt(Instant.now());
        attribution.setReversedBy(userId);

        ProjectFinancialAttribution updated = attributionRepository.save(attribution);
        return AttributionResponse.fromEntity(updated);
    }

    @Transactional
    public void invalidateAttributionsForReversedTransaction(UUID businessId, UUID transactionId, String reason, UUID userId) {
        List<ProjectFinancialAttribution> attributions = attributionRepository.findByBusinessIdAndSourceTypeAndSourceIdAndStatus(
                businessId, AttributionSourceType.TRANSACTION, transactionId, AttributionStatus.ACTIVE
        );
        for (ProjectFinancialAttribution a : attributions) {
            a.setStatus(AttributionStatus.REVERSED);
            a.setReversalReason("Underlying financial transaction reversed: " + (reason != null ? reason : "Source reversed"));
            a.setReversedAt(Instant.now());
            a.setReversedBy(userId);
            attributionRepository.save(a);
        }
    }
}
