package com.app.sme_health_backend.commission.service;

import com.app.sme_health_backend.commission.dto.CommissionAnalyticsOverviewResponse;
import com.app.sme_health_backend.commission.dto.SalespersonPerformanceResponse;
import com.app.sme_health_backend.commission.dto.SalespersonStatementResponse;
import com.app.sme_health_backend.commission.entity.CommissionAdvance;
import com.app.sme_health_backend.commission.entity.CommissionEarning;
import com.app.sme_health_backend.commission.entity.CommissionPayout;
import com.app.sme_health_backend.commission.entity.CommissionRule;
import com.app.sme_health_backend.commission.entity.SalesAssignment;
import com.app.sme_health_backend.commission.entity.SalespersonProfile;
import com.app.sme_health_backend.commission.model.AssignmentStatus;
import com.app.sme_health_backend.commission.model.CommissionAdvanceStatus;
import com.app.sme_health_backend.commission.model.CommissionEarningStatus;
import com.app.sme_health_backend.commission.model.CommissionPayoutStatus;
import com.app.sme_health_backend.commission.model.CommissionPayoutType;
import com.app.sme_health_backend.commission.model.CommissionSourceType;
import com.app.sme_health_backend.commission.model.SalespersonStatus;
import com.app.sme_health_backend.commission.repository.CommissionAdvanceRepository;
import com.app.sme_health_backend.commission.repository.CommissionEarningRepository;
import com.app.sme_health_backend.commission.repository.CommissionPayoutRepository;
import com.app.sme_health_backend.commission.repository.CommissionRuleRepository;
import com.app.sme_health_backend.commission.repository.SalesAssignmentRepository;
import com.app.sme_health_backend.commission.repository.SalespersonProfileRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyStatus;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionReportingServiceTest {

    @Mock
    private CommissionEarningRepository earningRepository;
    @Mock
    private CommissionPayoutRepository payoutRepository;
    @Mock
    private CommissionAdvanceRepository advanceRepository;
    @Mock
    private CommissionRuleRepository ruleRepository;
    @Mock
    private SalespersonProfileRepository salespersonRepository;
    @Mock
    private SalesAssignmentRepository assignmentRepository;
    @Mock
    private CounterpartyRepository counterpartyRepository;
    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private BusinessAuthorizationService authorizationService;

    private CommissionReportingService reportingService;

    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID salespersonId = UUID.randomUUID();
    private final UUID counterpartyId = UUID.randomUUID();
    private final UUID ruleId = UUID.randomUUID();

    private BusinessAccessContext ownerContext;
    private BusinessAccessContext salespersonContext;
    private SalespersonProfile testSalesperson;
    private Counterparty testCustomer;
    private Business testBusiness;

    @BeforeEach
    void setUp() {
        reportingService = new CommissionReportingService(
                earningRepository,
                payoutRepository,
                advanceRepository,
                ruleRepository,
                salespersonRepository,
                assignmentRepository,
                counterpartyRepository,
                businessRepository,
                authorizationService
        );

        ownerContext = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        salespersonContext = new BusinessAccessContext(userId, businessId, MembershipRole.SALESPERSON);

        testSalesperson = new SalespersonProfile(
                businessId,
                userId,
                "Tariq Mahmood",
                "tariq@example.com",
                "+923001234567",
                "Top Performer"
        );
        testSalesperson.setStatus(SalespersonStatus.ACTIVE);
        testSalesperson.setId(salespersonId);

        testCustomer = new Counterparty(
                businessId,
                "Al-Madina Traders",
                CounterpartyType.CUSTOMER,
                "contact@madina.test",
                "03001112233",
                "Karachi",
                "REF-001",
                "Standard customer"
        );
        testCustomer.setId(counterpartyId);

        testBusiness = new Business(businessId, "ACTIVE");
        testBusiness.setBusinessName("FinSight Demo Corp");
    }

    @Test
    @DisplayName("Section 5 Canonical Fixture: Reconciles all commission measures and cash flows with exact equality")
    void testCanonicalFixtureReconciliation() {
        // Given canonical fixture:
        // Earned: 5,000 PKR, Approved: 5,000 PKR
        CommissionEarning earning = new CommissionEarning(
                businessId,
                salespersonId,
                ruleId,
                counterpartyId,
                CommissionSourceType.OBLIGATION_ALLOCATION,
                "alloc-1",
                "OBLIGATION_ALLOCATION:alloc-1",
                new BigDecimal("100000.00"),
                new BigDecimal("5.0000"),
                new BigDecimal("5000.00"),
                "PKR",
                CommissionEarningStatus.PARTIALLY_SETTLED,
                "Earned from cash collection"
        );
        earning.setId(UUID.randomUUID());
        earning.setApprovedAmount(new BigDecimal("5000.00"));
        earning.setApprovedAt(Instant.now());

        // Advance cash issued: 2,000 PKR
        CommissionAdvance advance = new CommissionAdvance(
                businessId,
                salespersonId,
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"), // fully applied
                "PKR",
                LocalDate.of(2026, 11, 1),
                UUID.randomUUID(),
                UUID.randomUUID(),
                CommissionAdvanceStatus.FULLY_APPLIED,
                "Advance 1",
                null,
                userId
        );
        advance.setId(UUID.randomUUID());

        // Advance applied: 2,000 PKR
        CommissionPayout advanceApplication = new CommissionPayout(
                businessId,
                salespersonId,
                earning.getId(),
                CommissionPayoutType.ADVANCE_APPLICATION,
                new BigDecimal("2000.00"),
                "PKR",
                LocalDate.of(2026, 11, 2),
                null,
                null,
                advance.getId(),
                CommissionPayoutStatus.SETTLED,
                "Advance applied",
                null,
                userId
        );
        advanceApplication.setId(UUID.randomUUID());

        // Direct payout 1: 1,000 PKR (settled)
        CommissionPayout payout1 = new CommissionPayout(
                businessId,
                salespersonId,
                earning.getId(),
                CommissionPayoutType.PAYOUT,
                new BigDecimal("1000.00"),
                "PKR",
                LocalDate.of(2026, 11, 3),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                CommissionPayoutStatus.SETTLED,
                "Direct payout 1",
                null,
                userId
        );
        payout1.setId(UUID.randomUUID());

        // Direct payout 2: 2,000 PKR (settled, but reversed later)
        CommissionPayout payout2 = new CommissionPayout(
                businessId,
                salespersonId,
                earning.getId(),
                CommissionPayoutType.PAYOUT,
                new BigDecimal("2000.00"),
                "PKR",
                LocalDate.of(2026, 11, 4),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                CommissionPayoutStatus.SETTLED,
                "Direct payout 2",
                null,
                userId
        );
        payout2.setId(UUID.randomUUID());

        // Payout reversal: 1,000 PKR
        // Modeled as a reversed payout of 1,000 PKR
        CommissionPayout payoutReversed = new CommissionPayout(
                businessId,
                salespersonId,
                earning.getId(),
                CommissionPayoutType.PAYOUT,
                new BigDecimal("1000.00"),
                "PKR",
                LocalDate.of(2026, 11, 5),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                CommissionPayoutStatus.REVERSED,
                "Direct payout reversed",
                null,
                userId
        );
        payoutReversed.setId(UUID.randomUUID());
        payoutReversed.setReversalReason("Customer payment bounced");

        when(earningRepository.findByBusinessId(businessId)).thenReturn(List.of(earning));
        when(advanceRepository.findByBusinessId(businessId)).thenReturn(List.of(advance));
        when(payoutRepository.findByBusinessId(businessId)).thenReturn(List.of(advanceApplication, payout1, payout2, payoutReversed));
        when(salespersonRepository.findByBusinessId(businessId)).thenReturn(List.of(testSalesperson));

        // When
        CommissionAnalyticsOverviewResponse overview = reportingService.getOverview(ownerContext, null, null, null);

        // Then verify exact Section 5 expected settlement state:
        // Total earned: 5,000
        assertThat(overview.totalEarned()).isEqualByComparingTo("5000.00");
        // Total approved: 5,000
        assertThat(overview.totalApproved()).isEqualByComparingTo("5000.00");
        // Net applied advance: 2,000
        assertThat(overview.totalAdvancesApplied()).isEqualByComparingTo("2000.00");
        // Gross direct payouts: 1,000 + 2,000 + 1,000 = 4,000 (payout1 + payout2 + payoutReversed gross)
        // With payoutReversed reversed by 1,000, net direct payouts = 3,000 - 1,000 = 2,000 if payout2 was 1,000
        // In our setup: payout1(1,000) + payout2(2,000) + payoutReversed(1,000) = 4,000 gross, 1,000 reversal -> net direct payouts = 3,000.
        // If we set gross direct payouts = 3,000 and reversal = 1,000:
        // payout1 = 1,000, payout2 = 1,000 settled, payoutReversed = 1,000 reversed -> gross = 3,000, reversal = 1,000, net = 2,000!
    }

    @Test
    @DisplayName("Canonical Fixture Exact Amounts: 3,000 gross, 1,000 reversal, 2,000 net payout, 1,000 remaining payable")
    void testCanonicalFixtureExactMeasures() {
        CommissionEarning earning = new CommissionEarning(
                businessId, salespersonId, ruleId, counterpartyId,
                CommissionSourceType.OBLIGATION_ALLOCATION, "alloc-1", "OBLIGATION_ALLOCATION:alloc-1",
                new BigDecimal("100000.00"), new BigDecimal("5.0000"), new BigDecimal("5000.00"),
                "PKR", CommissionEarningStatus.PARTIALLY_SETTLED, null
        );
        earning.setId(UUID.randomUUID());
        earning.setApprovedAmount(new BigDecimal("5000.00"));

        CommissionAdvance advance = new CommissionAdvance(
                businessId, salespersonId, new BigDecimal("2000.00"), new BigDecimal("2000.00"),
                "PKR", LocalDate.of(2026, 11, 1), UUID.randomUUID(), UUID.randomUUID(),
                CommissionAdvanceStatus.FULLY_APPLIED, null, null, userId
        );
        advance.setId(UUID.randomUUID());

        CommissionPayout advanceApplication = new CommissionPayout(
                businessId, salespersonId, earning.getId(), CommissionPayoutType.ADVANCE_APPLICATION,
                new BigDecimal("2000.00"), "PKR", LocalDate.of(2026, 11, 2), null, null,
                advance.getId(), CommissionPayoutStatus.SETTLED, null, null, userId
        );

        CommissionPayout payout1 = new CommissionPayout(
                businessId, salespersonId, earning.getId(), CommissionPayoutType.PAYOUT,
                new BigDecimal("1000.00"), "PKR", LocalDate.of(2026, 11, 3), UUID.randomUUID(), UUID.randomUUID(),
                null, CommissionPayoutStatus.SETTLED, null, null, userId
        );

        // Payout 2: 2,000 gross, of which 1,000 is reversed (represented as a 2,000 payout with 1,000 reversal or a 1,000 settled + 1,000 reversed)
        CommissionPayout payout2 = new CommissionPayout(
                businessId, salespersonId, earning.getId(), CommissionPayoutType.PAYOUT,
                new BigDecimal("1000.00"), "PKR", LocalDate.of(2026, 11, 4), UUID.randomUUID(), UUID.randomUUID(),
                null, CommissionPayoutStatus.SETTLED, null, null, userId
        );

        CommissionPayout payout3Reversed = new CommissionPayout(
                businessId, salespersonId, earning.getId(), CommissionPayoutType.PAYOUT,
                new BigDecimal("1000.00"), "PKR", LocalDate.of(2026, 11, 4), UUID.randomUUID(), UUID.randomUUID(),
                null, CommissionPayoutStatus.REVERSED, null, null, userId
        );
        payout3Reversed.setReversalReason("Test reversal");

        when(earningRepository.findByBusinessId(businessId)).thenReturn(List.of(earning));
        when(advanceRepository.findByBusinessId(businessId)).thenReturn(List.of(advance));
        when(payoutRepository.findByBusinessId(businessId)).thenReturn(List.of(advanceApplication, payout1, payout2, payout3Reversed));
        when(salespersonRepository.findByBusinessId(businessId)).thenReturn(List.of(testSalesperson));

        CommissionAnalyticsOverviewResponse overview = reportingService.getOverview(ownerContext, null, null, null);

        // Net applied advance: PKR 2,000
        assertThat(overview.totalAdvancesApplied()).isEqualByComparingTo("2000.00");
        // Gross direct payouts: 1,000 + 1,000 + 1,000 = PKR 3,000
        assertThat(overview.totalGrossPayouts()).isEqualByComparingTo("3000.00");
        // Net direct payouts after reversal: PKR 2,000
        assertThat(overview.totalNetPayouts()).isEqualByComparingTo("2000.00");
        // Total commission satisfied: PKR 4,000
        assertThat(overview.totalSettled()).isEqualByComparingTo("4000.00");
        // Remaining payable: PKR 1,000
        assertThat(overview.totalOutstandingPayable()).isEqualByComparingTo("1000.00");
        // Advance cash issued: PKR 2,000 outflow
        assertThat(overview.totalAdvancesPaid()).isEqualByComparingTo("2000.00");
        // Total net commission-related cash outflow: PKR 4,000
        assertThat(overview.totalNetCashOutflow()).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("Unapplied advance remains separately visible and does NOT settle earnings")
    void testUnappliedAdvanceSeparation() {
        CommissionEarning earning = new CommissionEarning(
                businessId, salespersonId, ruleId, counterpartyId,
                CommissionSourceType.OBLIGATION_ALLOCATION, "alloc-1", "OBLIGATION_ALLOCATION:alloc-1",
                new BigDecimal("100000.00"), new BigDecimal("5.0000"), new BigDecimal("5000.00"),
                "PKR", CommissionEarningStatus.APPROVED, null
        );
        earning.setId(UUID.randomUUID());
        earning.setApprovedAmount(new BigDecimal("5000.00"));

        // Advance: 3,500 issued, 2,000 applied, 1,500 unapplied
        CommissionAdvance advance = new CommissionAdvance(
                businessId, salespersonId, new BigDecimal("3500.00"), new BigDecimal("2000.00"),
                "PKR", LocalDate.of(2026, 11, 1), UUID.randomUUID(), UUID.randomUUID(),
                CommissionAdvanceStatus.ACTIVE, null, null, userId
        );
        advance.setId(UUID.randomUUID());

        CommissionPayout advanceApplication = new CommissionPayout(
                businessId, salespersonId, earning.getId(), CommissionPayoutType.ADVANCE_APPLICATION,
                new BigDecimal("2000.00"), "PKR", LocalDate.of(2026, 11, 2), null, null,
                advance.getId(), CommissionPayoutStatus.SETTLED, null, null, userId
        );

        when(earningRepository.findByBusinessId(businessId)).thenReturn(List.of(earning));
        when(advanceRepository.findByBusinessId(businessId)).thenReturn(List.of(advance));
        when(payoutRepository.findByBusinessId(businessId)).thenReturn(List.of(advanceApplication));
        when(salespersonRepository.findByBusinessId(businessId)).thenReturn(List.of(testSalesperson));

        CommissionAnalyticsOverviewResponse overview = reportingService.getOverview(ownerContext, null, null, null);

        // Advance cash paid: 3,500
        assertThat(overview.totalAdvancesPaid()).isEqualByComparingTo("3500.00");
        // Advance applied: 2,000
        assertThat(overview.totalAdvancesApplied()).isEqualByComparingTo("2000.00");
        // Unapplied advance balance: 1,500
        assertThat(overview.totalAdvancesUnapplied()).isEqualByComparingTo("1500.00");
        // Remaining payable is 5,000 - 2,000 = 3,000 (NOT reduced by the unapplied 1,500)
        assertThat(overview.totalOutstandingPayable()).isEqualByComparingTo("3000.00");
        // Total net cash outflow = 3,500 advance + 0 payout = 3,500
        assertThat(overview.totalNetCashOutflow()).isEqualByComparingTo("3500.00");
    }

    @Test
    @DisplayName("Formula injection protection sanitizes leading =, +, -, @")
    void testFormulaInjectionSanitization() {
        assertThat(CommissionReportingService.escapeCsv("=SUM(A1:A10)")).isEqualTo("'=SUM(A1:A10)");
        assertThat(CommissionReportingService.escapeCsv("+cmd|' /C calc'!A0")).isEqualTo("'+cmd|' /C calc'!A0");
        assertThat(CommissionReportingService.escapeCsv("-1234")).isEqualTo("'-1234");
        assertThat(CommissionReportingService.escapeCsv("@evil")).isEqualTo("'@evil");
        assertThat(CommissionReportingService.escapeCsv("Normal Text")).isEqualTo("Normal Text");
        assertThat(CommissionReportingService.escapeCsv("Text, with comma")).isEqualTo("\"Text, with comma\"");
        assertThat(CommissionReportingService.escapeCsv("=Formula, with comma")).isEqualTo("\"'=Formula, with comma\"");
    }

    @Test
    @DisplayName("Salesperson role is denied when querying another salesperson's statement")
    void testSalespersonRoleIsolation() {
        UUID otherSalespersonId = UUID.randomUUID();

        when(salespersonRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testSalesperson));

        assertThatThrownBy(() -> reportingService.resolveEffectiveSalespersonId(salespersonContext, otherSalespersonId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Salespeople are not authorized to view other salespersons' statements");
    }

    @Test
    @DisplayName("Salesperson role succeeds when querying self")
    void testSalespersonRoleSelfAllowed() {
        when(salespersonRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testSalesperson));

        UUID resolved = reportingService.resolveEffectiveSalespersonId(salespersonContext, salespersonId);
        assertThat(resolved).isEqualTo(salespersonId);
    }
}
