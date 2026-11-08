package com.app.sme_health_backend.commission;

import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.commission.dto.*;
import com.app.sme_health_backend.commission.entity.CommissionEarning;
import com.app.sme_health_backend.commission.exception.CommissionSettlementConflictException;
import com.app.sme_health_backend.commission.model.*;
import com.app.sme_health_backend.commission.service.CommissionEarningService;
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
import com.app.sme_health_backend.obligation.dto.*;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CommissionSettlementPostgreSqlIT extends DisposablePostgres {

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
        ownerUser.setEmail("settle.owner." + suffix + "@example.test");
        ownerUser.setFullName("Business Owner Settle");
        ownerUser.setPasswordHash("hash-owner");
        ownerUser = appUserRepository.saveAndFlush(ownerUser);
        ownerUserId = ownerUser.getId();

        businessId = businessService.createBusiness(
                new CreateBusinessRequest("Settlement Test Enterprise " + suffix, "retail", "en", null, false, null, null, null),
                ownerUserId
        ).businessId();

        ownerContext = new BusinessAccessContext(ownerUserId, businessId, MembershipRole.OWNER);

        // 2. Create Salesperson User & Business Membership
        AppUser salesUser = new AppUser();
        salesUser.setEmail("settle.sales." + suffix + "@example.test");
        salesUser.setFullName("Tariq Sales Rep");
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
                businessId, "Settlement Meezan Account " + suffix, AccountType.BANK, "PKR", 2,
                new BigDecimal("1000000.00"), today.minusDays(60), AccountStatus.ACTIVE,
                null, null, "Operating Account"
        );
        account = accountRepository.saveAndFlush(account);
        bankAccountId = account.getId();

        // 4. Create Salesperson Profile linked to salesUser
        SalespersonResponse spRes = salespersonService.createSalesperson(
                ownerContext,
                new SalespersonCreateRequest("Tariq Mahmood", "settle.sales." + suffix + "@example.test", "+92 300 9988776", salesUserId, "Key territory rep")
        );
        salespersonId = spRes.id();

        // 5. Create Counterparty (Customer)
        CounterpartyDetailResponse cpRes = counterpartyService.createCounterparty(
                ownerContext,
                new CounterpartyCreateRequest("Allied Medical Supplies " + suffix, "CUSTOMER")
        );
        customerId = cpRes.id();

        // 6. Assign Customer to Salesperson
        salespersonService.assignCustomer(
                ownerContext,
                salespersonId,
                new SalesAssignmentCreateRequest(customerId, today.minusDays(30), null, "Direct hospital sales")
        );
    }

    @Test
    @DisplayName("IT-COMM-C-01: Section 8 Mandatory Numerical Acceptance Fixture")
    void testMandatoryNumericalAcceptanceFixture() {
        // 1. Create a 10% commission rule
        ruleService.createRule(ownerContext, new CommissionRuleCreateRequest(
                salespersonId, "10% Standard Medical Rule", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("10.0000"), null, "PKR", today.minusDays(30), null
        ));

        // 2. Create customer receivable for 100,000 PKR and allocate 100,000 PKR collection
        ObligationDetailResponse ob = obligationService.createObligation(ownerContext, new ObligationCreateRequest(
                customerId, "RECEIVABLE", "100000.00", today.minusDays(10).toString(), today.plusDays(20).toString(),
                "INV-NUM-001", "Medical machinery sale", null, null
        ));

        PaymentAllocateResponse alloc = paymentAllocationService.allocatePayment(
                ownerContext,
                "IDEMP-COLLECT-" + UUID.randomUUID(),
                new PaymentAllocateRequest(
                        customerId, "RECEIVABLE", bankAccountId, today.toString(),
                        "100000.00",
                        List.of(new AllocationItemRequest(ob.id(), "100000.00")),
                        "RCP-NUM-001", "Collection receipt", false
                )
        );
        UUID allocId = alloc.allocations().get(0).allocationId();

        // Bank balance after 100,000 collection = 1,000,000 + 100,000 = 1,100,000
        BigDecimal bankBalanceAfterCollection = getAccountBalance(bankAccountId);
        assertThat(bankBalanceAfterCollection).isEqualByComparingTo("1100000.00");

        // 3. Discover collection and record earning (10% on 100,000 = 10,000 PKR)
        CommissionEarningResponse earned = earningService.recordEarning(
                ownerContext,
                new CommissionEarningCreateRequest(CommissionSourceType.OBLIGATION_ALLOCATION, allocId.toString(), salespersonId, null, "10% collection earned")
        );
        assertThat(earned.commissionAmount()).isEqualByComparingTo("10000.00");
        assertThat(earned.status()).isEqualTo(CommissionEarningStatus.EARNED);

        // Commission earning recognition moved ZERO cash!
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo("1100000.00");

        // 4. Initial Position:
        // Approved earned commission: PKR 10,000.00
        CommissionEarningResponse approved = settlementService.approveEarning(ownerContext, earned.id(), null);
        assertThat(approved.status()).isEqualTo(CommissionEarningStatus.APPROVED);
        assertThat(approved.approvedAmount()).isEqualByComparingTo("10000.00");
        assertThat(approved.remainingPayable()).isEqualByComparingTo("10000.00");

        // Previously paid advance: PKR 3,000.00
        CommissionAdvanceResponse adv = settlementService.createAdvance(
                ownerContext,
                "IDEMP-ADV-" + UUID.randomUUID(),
                new CommissionAdvanceCreateRequest(salespersonId, bankAccountId, new BigDecimal("3000.00"), today, "Advance against commissions")
        );
        assertThat(adv.amount()).isEqualByComparingTo("3000.00");
        assertThat(adv.unappliedAmount()).isEqualByComparingTo("3000.00");

        // Cash outflow from advance: 3,000.00 PKR -> Bank balance = 1,100,000 - 3,000 = 1,097,000.00
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo("1097000.00");

        // --- STEP A: Apply advance ---
        // Apply the PKR 3,000.00 existing advance
        CommissionPayoutResponse stepA = settlementService.applyAdvance(
                ownerContext,
                "IDEMP-APPLY-ADV-1",
                earned.id(),
                new CommissionAdvanceApplyRequest(adv.id(), new BigDecimal("3000.00"), "Apply advance to Q1 commission")
        );
        assertThat(stepA.payoutType()).isEqualTo(CommissionPayoutType.ADVANCE_APPLICATION);
        assertThat(stepA.amount()).isEqualByComparingTo("3000.00");

        // Expected Step A assertions:
        // - Approved commission: PKR 10,000.00
        // - Advance applied: PKR 3,000.00
        // - Remaining commission payable: PKR 7,000.00
        // - Additional cash movement from application: PKR 0.00
        CommissionEarningResponse earningAfterStepA = earningService.getEarning(ownerContext, earned.id());
        assertThat(earningAfterStepA.approvedAmount()).isEqualByComparingTo("10000.00");
        assertThat(earningAfterStepA.settledAmount()).isEqualByComparingTo("3000.00");
        assertThat(earningAfterStepA.remainingPayable()).isEqualByComparingTo("7000.00");
        assertThat(earningAfterStepA.status()).isEqualTo(CommissionEarningStatus.PARTIALLY_SETTLED);
        // Bank balance unchanged!
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo("1097000.00");

        // --- STEP B: Partial payout ---
        // Pay PKR 2,000.00 from authorized business cash account
        CommissionPayoutResponse stepB = settlementService.recordPayout(
                ownerContext,
                "IDEMP-PAYOUT-1",
                earned.id(),
                new CommissionPayoutCreateRequest(bankAccountId, new BigDecimal("2000.00"), today, "Partial payout installment 1")
        );
        assertThat(stepB.payoutType()).isEqualTo(CommissionPayoutType.PAYOUT);
        assertThat(stepB.amount()).isEqualByComparingTo("2000.00");

        // Expected Step B assertions:
        // - Additional actual cash outflow: PKR 2,000.00
        // - Total satisfied commission: PKR 5,000.00
        // - Remaining payable: PKR 5,000.00
        // - Status: PARTIALLY_SETTLED
        CommissionEarningResponse earningAfterStepB = earningService.getEarning(ownerContext, earned.id());
        assertThat(earningAfterStepB.settledAmount()).isEqualByComparingTo("5000.00");
        assertThat(earningAfterStepB.remainingPayable()).isEqualByComparingTo("5000.00");
        assertThat(earningAfterStepB.status()).isEqualTo(CommissionEarningStatus.PARTIALLY_SETTLED);
        // Bank balance reduced by exactly 2,000.00 -> 1,095,000.00
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo("1095000.00");

        // --- STEP C: Final payout ---
        // Pay the remaining PKR 5,000.00
        CommissionPayoutResponse stepC = settlementService.recordPayout(
                ownerContext,
                "IDEMP-PAYOUT-2",
                earned.id(),
                new CommissionPayoutCreateRequest(bankAccountId, new BigDecimal("5000.00"), today, "Final payout settling balance")
        );
        assertThat(stepC.payoutType()).isEqualTo(CommissionPayoutType.PAYOUT);
        assertThat(stepC.amount()).isEqualByComparingTo("5000.00");

        // Expected Step C assertions:
        // - Additional actual cash outflow: PKR 5,000.00
        // - Total commission satisfied: PKR 10,000.00
        // - Remaining payable: PKR 0.00
        // - Status: SETTLED
        CommissionEarningResponse earningAfterStepC = earningService.getEarning(ownerContext, earned.id());
        assertThat(earningAfterStepC.settledAmount()).isEqualByComparingTo("10000.00");
        assertThat(earningAfterStepC.remainingPayable()).isEqualByComparingTo("0.00");
        assertThat(earningAfterStepC.status()).isEqualTo(CommissionEarningStatus.SETTLED);
        // Bank balance reduced by exactly 5,000.00 -> 1,090,000.00
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo("1090000.00");

        // --- STEP D: Duplicate replay ---
        // Repeat the previous payout request with the same idempotency key
        CommissionPayoutResponse stepD = settlementService.recordPayout(
                ownerContext,
                "IDEMP-PAYOUT-2",
                earned.id(),
                new CommissionPayoutCreateRequest(bankAccountId, new BigDecimal("5000.00"), today, "Final payout settling balance")
        );
        // Expected Step D assertions:
        // - Contract-consistent replay response
        // - No additional transaction
        // - No additional cash movement
        // - No change to remaining payable
        assertThat(stepD.id()).isEqualTo(stepC.id());
        assertThat(stepD.amount()).isEqualByComparingTo("5000.00");
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo("1090000.00");

        // --- STEP E: Overpayment attempt ---
        // Attempt another PKR 1,000.00 payout
        assertThatThrownBy(() -> settlementService.recordPayout(
                ownerContext,
                "IDEMP-OVERPAY-" + UUID.randomUUID(),
                earned.id(),
                new CommissionPayoutCreateRequest(bankAccountId, new BigDecimal("1000.00"), today, "Excess payout attempt")
        )).isInstanceOf(CommissionSettlementConflictException.class);

        // Expected Step E assertions:
        // - Rejected
        // - No cash movement
        // - No change to commission state
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo("1090000.00");
        CommissionEarningResponse earningAfterStepE = earningService.getEarning(ownerContext, earned.id());
        assertThat(earningAfterStepE.status()).isEqualTo(CommissionEarningStatus.SETTLED);
        assertThat(earningAfterStepE.remainingPayable()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("IT-COMM-C-02: Reversal restores remaining payable and posts compensating cash movement")
    void testPayoutReversalWorkflow() {
        CommissionRuleResponse rule = ruleService.createRule(ownerContext, new CommissionRuleCreateRequest(
                salespersonId, "10% Reversal Test Rule", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("10.0000"), null, "PKR", today.minusDays(30), null
        ));

        // 1. Direct commission earning approved for 20,000 PKR
        UUID earningId = jdbcTemplate.queryForObject(
                "INSERT INTO commission_earnings (id, business_id, salesperson_id, rule_id, counterparty_id, source_type, source_id, source_fingerprint, sale_amount, commission_rate_snapshot, commission_amount, currency, status, created_at, updated_at) VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'EARNED', now(), now()) RETURNING id",
                UUID.class,
                businessId, salespersonId, rule.id(), customerId,
                CommissionSourceType.MANUAL_SALE_DECLARATION.name(), "SALE-REV-001", "REV-FP-" + UUID.randomUUID(),
                new BigDecimal("200000.00"), new BigDecimal("10.0000"), new BigDecimal("20000.00"), "PKR"
        );

        settlementService.approveEarning(ownerContext, earningId, null);

        // 2. Pay 8,000.00 PKR
        BigDecimal balanceBefore = getAccountBalance(bankAccountId);
        CommissionPayoutResponse payout = settlementService.recordPayout(
                ownerContext,
                "IDEMP-PAY-REV-1",
                earningId,
                new CommissionPayoutCreateRequest(bankAccountId, new BigDecimal("8000.00"), today, "Erroneous payout to reverse")
        );

        assertThat(payout.status()).isEqualTo(CommissionPayoutStatus.SETTLED);
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo(balanceBefore.subtract(new BigDecimal("8000.00")));

        CommissionEarningResponse eBeforeRev = earningService.getEarning(ownerContext, earningId);
        assertThat(eBeforeRev.remainingPayable()).isEqualByComparingTo("12000.00");
        assertThat(eBeforeRev.status()).isEqualTo(CommissionEarningStatus.PARTIALLY_SETTLED);

        // 3. Reverse the 8,000.00 PKR payout
        CommissionPayoutResponse reversed = settlementService.reversePayout(
                ownerContext,
                "IDEMP-REV-1",
                payout.id(),
                new CommissionPayoutReverseRequest("Incorrect salesperson bank account specified")
        );

        assertThat(reversed.status()).isEqualTo(CommissionPayoutStatus.REVERSED);
        assertThat(reversed.reversalTransactionId()).isNotNull();

        // Cash restored!
        assertThat(getAccountBalance(bankAccountId)).isEqualByComparingTo(balanceBefore);

        // Remaining payable restored to 20,000.00 and status returned to APPROVED!
        CommissionEarningResponse eAfterRev = earningService.getEarning(ownerContext, earningId);
        assertThat(eAfterRev.remainingPayable()).isEqualByComparingTo("20000.00");
        assertThat(eAfterRev.settledAmount()).isEqualByComparingTo("0.00");
        assertThat(eAfterRev.status()).isEqualTo(CommissionEarningStatus.APPROVED);

        // Double reversal blocked
        assertThatThrownBy(() -> settlementService.reversePayout(
                ownerContext,
                "IDEMP-REV-2",
                payout.id(),
                new CommissionPayoutReverseRequest("Duplicate attempt")
        )).isInstanceOf(CommissionSettlementConflictException.class)
          .hasMessageContaining("already reversed");
    }

    @Test
    @DisplayName("IT-COMM-C-03: Role and Tenant Scoping Restrictions")
    void testRoleAndTenantScoping() {
        // Salesperson cannot approve commissions (Forbidden)
        assertThatThrownBy(() -> settlementService.approveEarning(
                salespersonContext, UUID.randomUUID(), null
        )).isInstanceOf(AccessDeniedException.class);

        // Salesperson cannot create advances (Forbidden)
        assertThatThrownBy(() -> settlementService.createAdvance(
                salespersonContext, "KEY", new CommissionAdvanceCreateRequest(salespersonId, bankAccountId, new BigDecimal("1000.00"), today, "Test")
        )).isInstanceOf(AccessDeniedException.class);

        // Salesperson cannot record payouts (Forbidden)
        assertThatThrownBy(() -> settlementService.recordPayout(
                salespersonContext, "KEY", UUID.randomUUID(), new CommissionPayoutCreateRequest(bankAccountId, new BigDecimal("1000.00"), today, "Test")
        )).isInstanceOf(AccessDeniedException.class);

        // Salesperson cannot access another salesperson's settlements
        UUID otherSalespersonId = UUID.randomUUID();
        assertThatThrownBy(() -> settlementService.getSalespersonSettlementSummary(
                salespersonContext, otherSalespersonId
        )).isInstanceOf(AccessDeniedException.class);

        // Salesperson CAN access their own settlement summary
        SalespersonSettlementSummaryResponse summary = settlementService.getSalespersonSettlementSummary(
                salespersonContext, salespersonId
        );
        assertThat(summary.salespersonId()).isEqualTo(salespersonId);
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
