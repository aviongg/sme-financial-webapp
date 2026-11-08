package com.app.sme_health_backend.commission;

import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.commission.dto.CommissionAdvanceApplyRequest;
import com.app.sme_health_backend.commission.dto.CommissionAdvanceCreateRequest;
import com.app.sme_health_backend.commission.dto.CommissionAdvanceResponse;
import com.app.sme_health_backend.commission.dto.CommissionAnalyticsOverviewResponse;
import com.app.sme_health_backend.commission.dto.CommissionApprovalRequest;
import com.app.sme_health_backend.commission.dto.CommissionAuditEventResponse;
import com.app.sme_health_backend.commission.dto.CommissionEarningCreateRequest;
import com.app.sme_health_backend.commission.dto.CommissionEarningResponse;
import com.app.sme_health_backend.commission.dto.CommissionPayoutCreateRequest;
import com.app.sme_health_backend.commission.dto.CommissionPayoutResponse;
import com.app.sme_health_backend.commission.dto.CommissionPayoutReverseRequest;
import com.app.sme_health_backend.commission.dto.CommissionRuleCreateRequest;
import com.app.sme_health_backend.commission.dto.CommissionRuleResponse;
import com.app.sme_health_backend.commission.dto.SalesAssignmentCreateRequest;
import com.app.sme_health_backend.commission.dto.SalespersonCreateRequest;
import com.app.sme_health_backend.commission.dto.SalespersonPerformanceResponse;
import com.app.sme_health_backend.commission.dto.SalespersonResponse;
import com.app.sme_health_backend.commission.dto.SalespersonStatementResponse;
import com.app.sme_health_backend.commission.facade.CommissionCostEvidence;
import com.app.sme_health_backend.commission.facade.CommissionCostEvidenceFacade;
import com.app.sme_health_backend.commission.model.CalculationType;
import com.app.sme_health_backend.commission.model.CommissionEarningStatus;
import com.app.sme_health_backend.commission.model.CommissionSourceType;
import com.app.sme_health_backend.commission.model.EarningBasis;
import com.app.sme_health_backend.commission.service.CommissionEarningService;
import com.app.sme_health_backend.commission.service.CommissionReportingService;
import com.app.sme_health_backend.commission.service.CommissionRuleService;
import com.app.sme_health_backend.commission.service.CommissionSettlementService;
import com.app.sme_health_backend.commission.service.SalespersonService;
import com.app.sme_health_backend.counterparty.CounterpartyService;
import com.app.sme_health_backend.counterparty.dto.CounterpartyCreateRequest;
import com.app.sme_health_backend.counterparty.dto.CounterpartyDetailResponse;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.obligation.FinancialObligationService;
import com.app.sme_health_backend.obligation.PaymentAllocationService;
import com.app.sme_health_backend.obligation.dto.AllocationItemRequest;
import com.app.sme_health_backend.obligation.dto.ObligationCreateRequest;
import com.app.sme_health_backend.obligation.dto.ObligationDetailResponse;
import com.app.sme_health_backend.obligation.dto.PaymentAllocateRequest;
import com.app.sme_health_backend.obligation.dto.PaymentAllocateResponse;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CommissionReportingPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private CommissionReportingService reportingService;

    @Autowired
    private CommissionCostEvidenceFacade costEvidenceFacade;

    @Autowired
    private CommissionSettlementService settlementService;

    @Autowired
    private CommissionEarningService earningService;

    @Autowired
    private CommissionRuleService ruleService;

    @Autowired
    private SalespersonService salespersonService;

    @Autowired
    private CounterpartyService counterpartyService;

    @Autowired
    private FinancialObligationService obligationService;

    @Autowired
    private PaymentAllocationService paymentAllocationService;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");

    private UUID businessId;
    private UUID ownerUserId;
    private UUID salesUserId;
    private BusinessAccessContext ownerContext;
    private BusinessAccessContext salespersonContext;
    private UUID bankAccountId;
    private UUID salespersonId;
    private UUID customerId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(KARACHI_ZONE);
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Owner User & Business
        AppUser ownerUser = new AppUser();
        ownerUser.setEmail("rep.owner." + suffix + "@example.test");
        ownerUser.setFullName("Business Owner Rep");
        ownerUser.setPasswordHash("hash-owner");
        ownerUser = appUserRepository.saveAndFlush(ownerUser);
        ownerUserId = ownerUser.getId();

        businessId = businessService.createBusiness(
                new CreateBusinessRequest("Reporting Test Corp " + suffix, "retail", "en", null, false, null, null, null),
                ownerUserId
        ).businessId();

        ownerContext = new BusinessAccessContext(ownerUserId, businessId, MembershipRole.OWNER);

        // 2. Create Salesperson User & Business Membership
        AppUser salesUser = new AppUser();
        salesUser.setEmail("rep.sales." + suffix + "@example.test");
        salesUser.setFullName("Tariq Mahmood Rep");
        salesUser.setPasswordHash("hash-rep");
        salesUser = appUserRepository.saveAndFlush(salesUser);
        salesUserId = salesUser.getId();

        jdbcTemplate.update(
                "INSERT INTO business_memberships (id, business_id, user_id, role, status, created_at, updated_at) VALUES (?, ?, ?, 'SALESPERSON', 'ACTIVE', now(), now())",
                UUID.randomUUID(), businessId, salesUserId
        );
        salespersonContext = new BusinessAccessContext(salesUserId, businessId, MembershipRole.SALESPERSON);

        // 3. Create Operating Financial Account (Initial Balance: PKR 1,000,000.00)
        FinancialAccount account = new FinancialAccount(
                businessId, "Reporting Meezan Account " + suffix, AccountType.BANK, "PKR", 2,
                new BigDecimal("1000000.00"), today.minusDays(60), AccountStatus.ACTIVE,
                null, null, "Operating Account"
        );
        account = accountRepository.saveAndFlush(account);
        bankAccountId = account.getId();

        // 4. Create Salesperson Profile linked to salesUser
        SalespersonResponse spRes = salespersonService.createSalesperson(
                ownerContext,
                new SalespersonCreateRequest("Tariq Mahmood", "rep.sales." + suffix + "@example.test", "+92 300 9988776", salesUserId, "Key territory rep")
        );
        salespersonId = spRes.id();

        // 5. Create Counterparty (Customer)
        CounterpartyDetailResponse cpRes = counterpartyService.createCounterparty(
                ownerContext,
                new CounterpartyCreateRequest("Al-Madina Traders " + suffix, "CUSTOMER")
        );
        customerId = cpRes.id();

        // 6. Assign Customer to Salesperson
        salespersonService.assignCustomer(
                ownerContext,
                salespersonId,
                new SalesAssignmentCreateRequest(customerId, today.minusDays(30), null, "Exclusive territory")
        );
    }

    @Test
    @DisplayName("IT-COMM-D-01: Section 5 Numerical Reconciliation and Reporting Verification")
    void testSection5NumericalReportingReconciliation() {
        // 1. Create a 5% collection-based commission rule
        ruleService.createRule(ownerContext, new CommissionRuleCreateRequest(
                salespersonId, "5% Standard Collection Rule", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.0000"), null, "PKR", today.minusDays(30), null
        ));

        // 2. Customer collection of 100,000 PKR
        ObligationDetailResponse ob = obligationService.createObligation(ownerContext, new ObligationCreateRequest(
                customerId, "RECEIVABLE", "100000.00", today.minusDays(10).toString(), today.plusDays(20).toString(),
                "INV-REP-001", "Bulk shipment", null, null
        ));

        PaymentAllocateResponse alloc = paymentAllocationService.allocatePayment(
                ownerContext,
                "IDEMP-COLLECT-" + UUID.randomUUID(),
                new PaymentAllocateRequest(
                        customerId, "RECEIVABLE", bankAccountId, today.toString(),
                        "100000.00",
                        List.of(new AllocationItemRequest(ob.id(), "100000.00")),
                        "RCP-REP-001", "Collection receipt", false
                )
        );
        UUID allocId = alloc.allocations().get(0).allocationId();

        // Bank balance after collection: 1,100,000.00 PKR
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo("1100000.00");

        // 3. Earned commission: 5% of 100,000 = 5,000.00 PKR
        CommissionEarningResponse earned = earningService.recordEarning(
                ownerContext,
                new CommissionEarningCreateRequest(CommissionSourceType.OBLIGATION_ALLOCATION, allocId.toString(), salespersonId, null, "5% collection commission")
        );
        assertThat(earned.commissionAmount()).isEqualByComparingTo("5000.00");
        assertThat(earned.status()).isEqualTo(CommissionEarningStatus.EARNED);

        // 4. Approved amount: 5,000.00 PKR
        CommissionEarningResponse approved = settlementService.approveEarning(ownerContext, earned.id(), null);
        assertThat(approved.approvedAmount()).isEqualByComparingTo("5000.00");
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo("1100000.00"); // 0 cash moved

        // 5. Advance cash issued: 2,000.00 PKR
        CommissionAdvanceResponse adv = settlementService.createAdvance(
                ownerContext,
                "IDEMP-ADV-" + UUID.randomUUID(),
                new CommissionAdvanceCreateRequest(salespersonId, bankAccountId, new BigDecimal("2000.00"), today, "Advance against commission")
        );
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo("1098000.00"); // Cash outflow 2,000

        // 6. Advance applied to earning: 2,000.00 PKR
        settlementService.applyAdvance(
                ownerContext,
                "IDEMP-APPLY-" + UUID.randomUUID(),
                earned.id(),
                new CommissionAdvanceApplyRequest(adv.id(), new BigDecimal("2000.00"), "Apply advance")
        );
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo("1098000.00"); // 0 additional cash moved

        // 7. Direct payout 1: 1,000.00 PKR
        CommissionPayoutResponse p1 = settlementService.recordPayout(
                ownerContext,
                "IDEMP-PAY1-" + UUID.randomUUID(),
                earned.id(),
                new CommissionPayoutCreateRequest(bankAccountId, new BigDecimal("1000.00"), today, "Payout 1")
        );
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo("1097000.00"); // Cash outflow 1,000

        // 8. Direct payout 2: 2,000.00 PKR (settles commission completely)
        CommissionPayoutResponse p2 = settlementService.recordPayout(
                ownerContext,
                "IDEMP-PAY2-" + UUID.randomUUID(),
                earned.id(),
                new CommissionPayoutCreateRequest(bankAccountId, new BigDecimal("2000.00"), today, "Payout 2")
        );
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo("1095000.00"); // Cash outflow 2,000

        // 9. Payout reversal of payout 1 (1,000.00 PKR)
        settlementService.reversePayout(
                ownerContext,
                "IDEMP-REV1-" + UUID.randomUUID(),
                p1.id(),
                new CommissionPayoutReverseRequest("Reversal of payout 1")
        );
        // Bank balance after reversal: 1,095,000 + 1,000 = 1,096,000.00 PKR
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo("1096000.00");

        // --- VERIFY REPORTING OVERVIEW ---
        CommissionAnalyticsOverviewResponse overview = reportingService.getOverview(ownerContext, null, null, null);
        assertThat(overview.totalEarned()).isEqualByComparingTo("5000.00");
        assertThat(overview.totalApproved()).isEqualByComparingTo("5000.00");
        assertThat(overview.totalAdvancesPaid()).isEqualByComparingTo("2000.00");
        assertThat(overview.totalAdvancesApplied()).isEqualByComparingTo("2000.00");
        assertThat(overview.totalAdvancesUnapplied()).isEqualByComparingTo("0.00");
        assertThat(overview.totalGrossPayouts()).isEqualByComparingTo("3000.00");
        assertThat(overview.totalReversals()).isEqualByComparingTo("1000.00");
        assertThat(overview.totalNetPayouts()).isEqualByComparingTo("2000.00");
        assertThat(overview.totalSettled()).isEqualByComparingTo("4000.00");
        assertThat(overview.totalOutstandingPayable()).isEqualByComparingTo("1000.00");
        assertThat(overview.totalNetCashOutflow()).isEqualByComparingTo("4000.00");

        // --- VERIFY SALESPERSON STATEMENT ---
        SalespersonStatementResponse statement = reportingService.getSalespersonStatement(
                ownerContext, salespersonId, today.minusDays(15), today.plusDays(15)
        );
        assertThat(statement.salespersonName()).isEqualTo("Tariq Mahmood");
        assertThat(statement.periodEarned()).isEqualByComparingTo("5000.00");
        assertThat(statement.periodApproved()).isEqualByComparingTo("5000.00");
        assertThat(statement.periodAdvancesIssued()).isEqualByComparingTo("2000.00");
        assertThat(statement.periodAdvancesApplied()).isEqualByComparingTo("2000.00");
        assertThat(statement.periodGrossPayouts()).isEqualByComparingTo("3000.00");
        assertThat(statement.periodPayoutReversals()).isEqualByComparingTo("1000.00");
        assertThat(statement.periodNetPayouts()).isEqualByComparingTo("2000.00");
        assertThat(statement.closingSettlementPosition()).isEqualByComparingTo("1000.00");
        assertThat(statement.earnings()).hasSize(1);
        assertThat(statement.settlements()).hasSize(5); // Advance issued, Advance applied, Payout 1, Payout 2, Payout 1 reversal

        // --- VERIFY CSV EXPORT ---
        byte[] csvBytes = reportingService.exportStatementCsv(ownerContext, salespersonId, today.minusDays(15), today.plusDays(15));
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csv).contains("FINSIGHT COMMISSION STATEMENT");
        assertThat(csv).contains("Tariq Mahmood");
        assertThat(csv).contains("Closing Settlement Position,1000.00");

        // --- VERIFY DEVELOPER B FACADE ---
        Optional<CommissionCostEvidence> evidence = costEvidenceFacade.getCommissionCostEvidence(ownerContext, earned.id());
        assertThat(evidence).isPresent();
        assertThat(evidence.get().earnedAmount()).isEqualByComparingTo("5000.00");
        assertThat(evidence.get().approvedAmount()).isEqualByComparingTo("5000.00");
        assertThat(evidence.get().salespersonName()).isEqualTo("Tariq Mahmood");

        // --- VERIFY AUDIT LEDGER ---
        List<CommissionAuditEventResponse> events = reportingService.getAuditLedger(ownerContext, null, null, null, null);
        assertThat(events).isNotEmpty();
    }

    @Test
    @DisplayName("IT-COMM-D-02: Salesperson Role Isolation and Business-Wide Access Denial")
    void testSalespersonRoleIsolation() {
        // Salesperson can access own statement via getSalespersonStatement
        SalespersonStatementResponse selfStmt = reportingService.getSalespersonStatement(
                salespersonContext, null, today.minusDays(10), today
        );
        assertThat(selfStmt.salespersonId()).isEqualTo(salespersonId);
        assertThat(selfStmt.salespersonName()).isEqualTo("Tariq Mahmood");

        // Salesperson attempting to query a different ID is rejected
        UUID unauthorizedId = UUID.randomUUID();
        assertThatThrownBy(() -> reportingService.getSalespersonStatement(salespersonContext, unauthorizedId, today.minusDays(10), today))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Salespeople are not authorized to view other salespersons' statements");
    }

    private BigDecimal getAccountBalance(UUID accId) {
        BigDecimal opening = jdbcTemplate.queryForObject(
                "SELECT opening_balance FROM financial_accounts WHERE id = ?",
                BigDecimal.class, accId
        );
        BigDecimal net = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN movement_direction = 'INCREASE' THEN amount ELSE -amount END), 0) FROM account_movements WHERE account_id = ?",
                BigDecimal.class, accId
        );
        return opening.add(net != null ? net : BigDecimal.ZERO);
    }
}
