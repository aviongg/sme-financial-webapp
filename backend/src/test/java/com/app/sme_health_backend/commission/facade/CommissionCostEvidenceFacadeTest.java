package com.app.sme_health_backend.commission.facade;

import com.app.sme_health_backend.commission.entity.CommissionAdvance;
import com.app.sme_health_backend.commission.entity.CommissionEarning;
import com.app.sme_health_backend.commission.entity.CommissionPayout;
import com.app.sme_health_backend.commission.entity.CommissionRule;
import com.app.sme_health_backend.commission.entity.SalespersonProfile;
import com.app.sme_health_backend.commission.model.CalculationType;
import com.app.sme_health_backend.commission.model.CommissionEarningStatus;
import com.app.sme_health_backend.commission.model.CommissionPayoutStatus;
import com.app.sme_health_backend.commission.model.CommissionPayoutType;
import com.app.sme_health_backend.commission.model.CommissionSourceType;
import com.app.sme_health_backend.commission.model.EarningBasis;
import com.app.sme_health_backend.commission.model.SalespersonStatus;
import com.app.sme_health_backend.commission.repository.CommissionAdvanceRepository;
import com.app.sme_health_backend.commission.repository.CommissionEarningRepository;
import com.app.sme_health_backend.commission.repository.CommissionPayoutRepository;
import com.app.sme_health_backend.commission.repository.CommissionRuleRepository;
import com.app.sme_health_backend.commission.repository.SalespersonProfileRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionCostEvidenceFacadeTest {

    @Mock
    private CommissionEarningRepository earningRepository;
    @Mock
    private CommissionPayoutRepository payoutRepository;
    @Mock
    private CommissionAdvanceRepository advanceRepository;
    @Mock
    private SalespersonProfileRepository salespersonRepository;
    @Mock
    private CounterpartyRepository counterpartyRepository;
    @Mock
    private CommissionRuleRepository ruleRepository;

    private CommissionCostEvidenceFacade facade;

    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID salespersonId = UUID.randomUUID();
    private final UUID counterpartyId = UUID.randomUUID();
    private final UUID ruleId = UUID.randomUUID();

    private BusinessAccessContext context;
    private SalespersonProfile salesperson;
    private Counterparty counterparty;
    private CommissionRule rule;

    @BeforeEach
    void setUp() {
        facade = new CommissionCostEvidenceFacadeImpl(
                earningRepository,
                payoutRepository,
                advanceRepository,
                salespersonRepository,
                counterpartyRepository,
                ruleRepository
        );

        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);

        salesperson = new SalespersonProfile(
                businessId,
                userId,
                "Zubair Ahmed",
                "zubair@example.com",
                null,
                null
        );
        salesperson.setStatus(SalespersonStatus.ACTIVE);
        salesperson.setId(salespersonId);

        counterparty = new Counterparty(
                businessId,
                "Apex Industries",
                CounterpartyType.CUSTOMER,
                null,
                null,
                null,
                null,
                null
        );
        counterparty.setId(counterpartyId);

        rule = new CommissionRule(
                businessId,
                null,
                "5% Collection Rule",
                CalculationType.PERCENTAGE,
                EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.0000"),
                null,
                "PKR",
                LocalDate.of(2026, 1, 1),
                null
        );
        rule.setId(ruleId);
    }

    @Test
    @DisplayName("getCommissionCostEvidence: Returns populated immutable cost evidence record")
    void testGetCommissionCostEvidence() {
        UUID earningId = UUID.randomUUID();
        CommissionEarning earning = new CommissionEarning(
                businessId,
                salespersonId,
                ruleId,
                counterpartyId,
                CommissionSourceType.OBLIGATION_ALLOCATION,
                "alloc-99",
                "OBLIGATION_ALLOCATION:alloc-99",
                new BigDecimal("200000.00"),
                new BigDecimal("5.0000"),
                new BigDecimal("10000.00"),
                "PKR",
                CommissionEarningStatus.PARTIALLY_SETTLED,
                null
        );
        earning.setId(earningId);
        earning.setApprovedAmount(new BigDecimal("10000.00"));

        CommissionPayout payout = new CommissionPayout(
                businessId,
                salespersonId,
                earningId,
                CommissionPayoutType.PAYOUT,
                new BigDecimal("4000.00"),
                "PKR",
                LocalDate.of(2026, 11, 2),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                CommissionPayoutStatus.SETTLED,
                null,
                null,
                userId
        );

        when(earningRepository.findByIdAndBusinessId(earningId, businessId)).thenReturn(Optional.of(earning));
        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.of(salesperson));
        when(counterpartyRepository.findByIdAndBusinessId(counterpartyId, businessId)).thenReturn(Optional.of(counterparty));
        when(ruleRepository.findByIdAndBusinessId(ruleId, businessId)).thenReturn(Optional.of(rule));
        when(payoutRepository.findByBusinessIdAndEarningId(businessId, earningId)).thenReturn(List.of(payout));

        Optional<CommissionCostEvidence> evidenceOpt = facade.getCommissionCostEvidence(context, earningId);

        assertThat(evidenceOpt).isPresent();
        CommissionCostEvidence evidence = evidenceOpt.get();
        assertThat(evidence.earningId()).isEqualTo(earningId);
        assertThat(evidence.salespersonName()).isEqualTo("Zubair Ahmed");
        assertThat(evidence.counterpartyName()).isEqualTo("Apex Industries");
        assertThat(evidence.saleAmount()).isEqualByComparingTo("200000.00");
        assertThat(evidence.earnedAmount()).isEqualByComparingTo("10000.00");
        assertThat(evidence.approvedAmount()).isEqualByComparingTo("10000.00");
        assertThat(evidence.settledAmount()).isEqualByComparingTo("4000.00");
        assertThat(evidence.netPaidAmount()).isEqualByComparingTo("4000.00");
        assertThat(evidence.reversalState()).isEqualTo("NONE");
        assertThat(evidence.recognitionBasis()).isEqualTo("COLLECTION_BASED");
    }

    @Test
    @DisplayName("getSalespersonCostSummary: Summarizes cost evidence over period")
    void testGetSalespersonCostSummary() {
        CommissionEarning earning = new CommissionEarning(
                businessId,
                salespersonId,
                ruleId,
                counterpartyId,
                CommissionSourceType.OBLIGATION_ALLOCATION,
                "alloc-101",
                "OBLIGATION_ALLOCATION:alloc-101",
                new BigDecimal("100000.00"),
                new BigDecimal("5.0000"),
                new BigDecimal("5000.00"),
                "PKR",
                CommissionEarningStatus.APPROVED,
                null
        );
        earning.setId(UUID.randomUUID());
        earning.setApprovedAmount(new BigDecimal("5000.00"));

        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.of(salesperson));
        when(earningRepository.findByBusinessIdAndSalespersonId(businessId, salespersonId)).thenReturn(List.of(earning));
        when(payoutRepository.findByBusinessIdAndSalespersonId(businessId, salespersonId)).thenReturn(List.of());
        when(advanceRepository.sumUnappliedAdvancesBySalesperson(businessId, salespersonId)).thenReturn(new BigDecimal("1500.00"));

        SalespersonCostSummary summary = facade.getSalespersonCostSummary(
                context, salespersonId, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)
        );

        assertThat(summary.salespersonName()).isEqualTo("Zubair Ahmed");
        assertThat(summary.totalEarnedAmount()).isEqualByComparingTo("5000.00");
        assertThat(summary.totalApprovedAmount()).isEqualByComparingTo("5000.00");
        assertThat(summary.totalSettledAmount()).isEqualByComparingTo("0.00");
        assertThat(summary.unappliedAdvanceBalance()).isEqualByComparingTo("1500.00");
    }
}
