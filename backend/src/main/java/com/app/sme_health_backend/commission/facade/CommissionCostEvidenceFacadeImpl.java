package com.app.sme_health_backend.commission.facade;

import com.app.sme_health_backend.commission.entity.CommissionEarning;
import com.app.sme_health_backend.commission.entity.CommissionPayout;
import com.app.sme_health_backend.commission.entity.CommissionRule;
import com.app.sme_health_backend.commission.entity.SalespersonProfile;
import com.app.sme_health_backend.commission.model.CommissionPayoutStatus;
import com.app.sme_health_backend.commission.model.CommissionPayoutType;
import com.app.sme_health_backend.commission.repository.CommissionAdvanceRepository;
import com.app.sme_health_backend.commission.repository.CommissionEarningRepository;
import com.app.sme_health_backend.commission.repository.CommissionPayoutRepository;
import com.app.sme_health_backend.commission.repository.CommissionRuleRepository;
import com.app.sme_health_backend.commission.repository.SalespersonProfileRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CommissionCostEvidenceFacadeImpl implements CommissionCostEvidenceFacade {

    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");

    private final CommissionEarningRepository earningRepository;
    private final CommissionPayoutRepository payoutRepository;
    private final CommissionAdvanceRepository advanceRepository;
    private final SalespersonProfileRepository salespersonRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final CommissionRuleRepository ruleRepository;

    public CommissionCostEvidenceFacadeImpl(
            CommissionEarningRepository earningRepository,
            CommissionPayoutRepository payoutRepository,
            CommissionAdvanceRepository advanceRepository,
            SalespersonProfileRepository salespersonRepository,
            CounterpartyRepository counterpartyRepository,
            CommissionRuleRepository ruleRepository
    ) {
        this.earningRepository = earningRepository;
        this.payoutRepository = payoutRepository;
        this.advanceRepository = advanceRepository;
        this.salespersonRepository = salespersonRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.ruleRepository = ruleRepository;
    }

    @Override
    public Optional<CommissionCostEvidence> getCommissionCostEvidence(BusinessAccessContext context, UUID earningId) {
        if (context == null || earningId == null) {
            return Optional.empty();
        }

        return earningRepository.findByIdAndBusinessId(earningId, context.businessId())
                .map(earning -> mapToCostEvidence(context.businessId(), earning));
    }

    @Override
    public List<CommissionCostEvidence> listCommissionCostEvidence(
            BusinessAccessContext context,
            LocalDate fromDate,
            LocalDate toDate,
            UUID salespersonId,
            UUID counterpartyId
    ) {
        if (context == null) {
            return List.of();
        }

        List<CommissionEarning> earnings = (salespersonId != null)
                ? earningRepository.findByBusinessIdAndSalespersonId(context.businessId(), salespersonId)
                : earningRepository.findByBusinessId(context.businessId());

        return earnings.stream()
                .filter(e -> {
                    LocalDate earningDate = e.getCreatedAt().atZone(KARACHI_ZONE).toLocalDate();
                    if (fromDate != null && earningDate.isBefore(fromDate)) return false;
                    if (toDate != null && earningDate.isAfter(toDate)) return false;
                    if (counterpartyId != null && !e.getCounterpartyId().equals(counterpartyId)) return false;
                    return true;
                })
                .map(e -> mapToCostEvidence(context.businessId(), e))
                .toList();
    }

    @Override
    public SalespersonCostSummary getSalespersonCostSummary(
            BusinessAccessContext context,
            UUID salespersonId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (context == null || salespersonId == null) {
            throw new IllegalArgumentException("BusinessAccessContext and salespersonId must not be null");
        }

        SalespersonProfile sp = salespersonRepository.findByIdAndBusinessId(salespersonId, context.businessId())
                .orElseThrow(() -> new IllegalArgumentException("Salesperson not found: " + salespersonId));

        List<CommissionEarning> earnings = earningRepository.findByBusinessIdAndSalespersonId(context.businessId(), salespersonId);
        List<CommissionPayout> payouts = payoutRepository.findByBusinessIdAndSalespersonId(context.businessId(), salespersonId);

        BigDecimal totalEarned = BigDecimal.ZERO;
        BigDecimal totalApproved = BigDecimal.ZERO;

        for (CommissionEarning e : earnings) {
            LocalDate d = e.getCreatedAt().atZone(KARACHI_ZONE).toLocalDate();
            if (fromDate != null && d.isBefore(fromDate)) continue;
            if (toDate != null && d.isAfter(toDate)) continue;

            totalEarned = totalEarned.add(e.getCommissionAmount());
            if (e.getApprovedAmount() != null) {
                totalApproved = totalApproved.add(e.getApprovedAmount());
            } else if (e.getStatus().name().contains("APPROVED") || e.getStatus().name().contains("SETTLED")) {
                totalApproved = totalApproved.add(e.getCommissionAmount());
            }
        }

        BigDecimal totalSettled = BigDecimal.ZERO;
        BigDecimal netPaid = BigDecimal.ZERO;

        for (CommissionPayout p : payouts) {
            LocalDate d = p.getPaymentDate();
            if (fromDate != null && d.isBefore(fromDate)) continue;
            if (toDate != null && d.isAfter(toDate)) continue;

            if (p.getStatus() == CommissionPayoutStatus.SETTLED) {
                totalSettled = totalSettled.add(p.getAmount());
                if (p.getPayoutType() == CommissionPayoutType.PAYOUT) {
                    netPaid = netPaid.add(p.getAmount());
                }
            } else if (p.getStatus() == CommissionPayoutStatus.REVERSED && p.getPayoutType() == CommissionPayoutType.PAYOUT) {
                // Deduct reversed payouts from net paid
                netPaid = netPaid.subtract(p.getAmount());
            }
        }

        BigDecimal unappliedAdvance = advanceRepository.sumUnappliedAdvancesBySalesperson(context.businessId(), salespersonId);

        return new SalespersonCostSummary(
                context.businessId(),
                salespersonId,
                sp.getDisplayName(),
                fromDate,
                toDate,
                totalEarned,
                totalApproved,
                totalSettled,
                netPaid,
                unappliedAdvance,
                "PKR"
        );
    }

    private CommissionCostEvidence mapToCostEvidence(UUID businessId, CommissionEarning e) {
        String spName = salespersonRepository.findByIdAndBusinessId(e.getSalespersonId(), businessId)
                .map(SalespersonProfile::getDisplayName)
                .orElse("Unknown Salesperson");

        String cpName = counterpartyRepository.findByIdAndBusinessId(e.getCounterpartyId(), businessId)
                .map(Counterparty::getDisplayName)
                .orElse("Unknown Customer");

        String ruleBasis = ruleRepository.findByIdAndBusinessId(e.getRuleId(), businessId)
                .map(r -> r.getEarningBasis().name())
                .orElse("COLLECTION_BASED");

        List<CommissionPayout> payouts = payoutRepository.findByBusinessIdAndEarningId(businessId, e.getId());

        BigDecimal settledAmt = BigDecimal.ZERO;
        BigDecimal netPaid = BigDecimal.ZERO;
        boolean hasReversals = false;
        boolean hasSettled = false;

        for (CommissionPayout p : payouts) {
            if (p.getStatus() == CommissionPayoutStatus.SETTLED) {
                settledAmt = settledAmt.add(p.getAmount());
                hasSettled = true;
                if (p.getPayoutType() == CommissionPayoutType.PAYOUT) {
                    netPaid = netPaid.add(p.getAmount());
                }
            } else if (p.getStatus() == CommissionPayoutStatus.REVERSED) {
                hasReversals = true;
            }
        }

        String reversalState = "NONE";
        if (hasReversals) {
            reversalState = hasSettled ? "PARTIALLY_REVERSED" : "FULLY_REVERSED";
        }

        return new CommissionCostEvidence(
                e.getId(),
                businessId,
                e.getSalespersonId(),
                spName,
                e.getCounterpartyId(),
                cpName,
                e.getSourceType(),
                e.getSourceId(),
                e.getSourceFingerprint(),
                e.getSaleAmount(),
                e.getCommissionRateSnapshot(),
                e.getCommissionAmount(),
                e.getApprovedAmount() != null ? e.getApprovedAmount() : e.getCommissionAmount(),
                e.getStatus(),
                settledAmt,
                netPaid,
                e.getCurrency(),
                e.getCreatedAt(),
                e.getApprovedAt(),
                reversalState,
                ruleBasis
        );
    }
}
