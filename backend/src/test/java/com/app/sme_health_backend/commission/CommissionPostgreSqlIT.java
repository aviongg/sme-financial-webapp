package com.app.sme_health_backend.commission;

import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.commission.dto.*;
import com.app.sme_health_backend.commission.exception.CommissionEarningConflictException;
import com.app.sme_health_backend.commission.model.*;
import com.app.sme_health_backend.commission.service.CommissionEarningService;
import com.app.sme_health_backend.commission.service.CommissionRuleService;
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
class CommissionPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private CommissionRuleService ruleService;

    @Autowired
    private CommissionEarningService earningService;

    @Autowired
    private SalespersonService salespersonService;

    @Autowired
    private FinancialObligationService obligationService;

    @Autowired
    private PaymentAllocationService paymentAllocationService;

    @Autowired
    private CounterpartyService counterpartyService;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userOwnerA;
    private UUID businessA;
    private BusinessAccessContext ownerContextA;

    private UUID userSalespersonA;
    private BusinessAccessContext salespersonContextA;

    private UUID userOwnerB;
    private UUID businessB;
    private BusinessAccessContext ownerContextB;

    private UUID customerAId;
    private UUID salespersonAId;
    private UUID bankAccountId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Karachi"));
        String suffixA = UUID.randomUUID().toString().substring(0, 8);

        // Owner A
        AppUser uA = new AppUser();
        uA.setEmail("owner-a-" + suffixA + "@example.test");
        uA.setFullName("Business A Owner");
        uA.setPasswordHash("hash-a");
        userOwnerA = userRepository.saveAndFlush(uA).getId();

        businessA = businessService.createBusiness(
                new CreateBusinessRequest("Biz Commission A", "retail", "en", null, false, null, null, null),
                userOwnerA
        ).businessId();
        ownerContextA = new BusinessAccessContext(userOwnerA, businessA, MembershipRole.OWNER);

        // Salesperson User A
        AppUser uRep = new AppUser();
        uRep.setEmail("rep-" + suffixA + "@example.test");
        uRep.setFullName("Sales Rep Tariq");
        uRep.setPasswordHash("hash-rep");
        userSalespersonA = userRepository.saveAndFlush(uRep).getId();
        salespersonContextA = new BusinessAccessContext(userSalespersonA, businessA, MembershipRole.SALESPERSON);

        // Business B
        String suffixB = UUID.randomUUID().toString().substring(0, 8);
        AppUser uB = new AppUser();
        uB.setEmail("owner-b-" + suffixB + "@example.test");
        uB.setFullName("Business B Owner");
        uB.setPasswordHash("hash-b");
        userOwnerB = userRepository.saveAndFlush(uB).getId();

        businessB = businessService.createBusiness(
                new CreateBusinessRequest("Biz Commission B", "services", "en", null, false, null, null, null),
                userOwnerB
        ).businessId();
        ownerContextB = new BusinessAccessContext(userOwnerB, businessB, MembershipRole.OWNER);

        // Customer in Business A
        CounterpartyDetailResponse cust = counterpartyService.createCounterparty(ownerContextA, new CounterpartyCreateRequest(
                "Acme Fabrics " + suffixA, "CUSTOMER"
        ));
        customerAId = cust.id();

        // Salesperson profile in Business A linked to userSalespersonA
        SalespersonResponse sp = salespersonService.createSalesperson(ownerContextA, new SalespersonCreateRequest(
                "Tariq Mahmood", "rep-" + suffixA + "@example.test", "+923001234567", userSalespersonA, "Senior Rep"
        ));
        salespersonAId = sp.id();

        // Assign Customer to Salesperson
        salespersonService.assignCustomer(ownerContextA, salespersonAId, new SalesAssignmentCreateRequest(
                customerAId, today.minusDays(30), null, "Primary client"
        ));

        // Bank Account for Business A
        FinancialAccount bank = new FinancialAccount(
                businessA,
                "Bank Alfalah " + suffixA,
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("500000.00"),
                today.minusDays(60),
                AccountStatus.ACTIVE,
                null,
                null,
                "Settlement Bank"
        );
        bankAccountId = accountRepository.saveAndFlush(bank).getId();
    }

    @Test
    @DisplayName("IT-COMM-B-01: Rule precedence resolves salesperson-specific rule over business-default rule")
    void testRuleResolutionPrecedence() {
        // Business default: 3%
        ruleService.createRule(ownerContextA, new CommissionRuleCreateRequest(
                null, "Default 3%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("3.0000"), null, "PKR", today.minusDays(60), null
        ));

        // Salesperson specific: 5%
        CommissionRuleResponse specificRule = ruleService.createRule(ownerContextA, new CommissionRuleCreateRequest(
                salespersonAId, "Tariq 5%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.0000"), null, "PKR", today.minusDays(30), null
        ));

        var resolved = ruleService.findApplicableRule(businessA, salespersonAId, today);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo(specificRule.id());
        assertThat(resolved.get().getRatePercentage()).isEqualByComparingTo("5.0000");

        // Another salesperson without specific rule gets default 3%
        UUID otherRepId = UUID.randomUUID();
        var defaultResolved = ruleService.findApplicableRule(businessA, otherRepId, today);
        assertThat(defaultResolved).isPresent();
        assertThat(defaultResolved.get().getRatePercentage()).isEqualByComparingTo("3.0000");
    }

    @Test
    @DisplayName("IT-COMM-B-02: Discover eligible collections and calculate estimated commission")
    void testDiscoverEligibleCollections() {
        // 1. Create rule: 5% collection-based
        ruleService.createRule(ownerContextA, new CommissionRuleCreateRequest(
                salespersonAId, "Standard 5%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.0000"), null, "PKR", today.minusDays(30), null
        ));

        // 2. Create Receivable obligation: 100,000 PKR
        ObligationDetailResponse ob = obligationService.createObligation(ownerContextA, new ObligationCreateRequest(
                customerAId, "RECEIVABLE", "100000.00", today.minusDays(10).toString(), today.plusDays(20).toString(),
                "INV-TEST-001", "Order 1", null, null
        ));

        // 3. Allocate Cash payment: 100,000 PKR
        PaymentAllocateRequest payReq = new PaymentAllocateRequest(
                customerAId,
                "RECEIVABLE",
                bankAccountId,
                today.toString(),
                "100000.00",
                List.of(new AllocationItemRequest(ob.id(), "100000.00")),
                "RCP-TEST-001",
                "Cash collection",
                false
        );
        PaymentAllocateResponse allocResp = paymentAllocationService.allocatePayment(ownerContextA, "idemp-comm-1", payReq);
        assertThat(allocResp.allocations()).hasSize(1);
        UUID allocationId = allocResp.allocations().get(0).allocationId();

        // 4. Discover eligible collections
        List<EligibleCollectionResponse> eligible = earningService.discoverEligibleCollections(ownerContextA);
        assertThat(eligible).hasSize(1);

        EligibleCollectionResponse item = eligible.get(0);
        assertThat(item.allocationId()).isEqualTo(allocationId);
        assertThat(item.salespersonId()).isEqualTo(salespersonAId);
        assertThat(item.salespersonName()).isEqualTo("Tariq Mahmood");
        assertThat(item.counterpartyName()).contains("Acme Fabrics");
        assertThat(item.collectionAmount()).isEqualByComparingTo("100000.00");
        assertThat(item.estimatedCommission()).isEqualByComparingTo("5000.00");
        assertThat(item.alreadyEarned()).isFalse();
    }

    @Test
    @DisplayName("IT-COMM-B-03: Record earning, enforce uniqueness, and verify immutable audit record")
    void testRecordEarningAndPreventDuplicate() {
        // 1. Setup rule and payment
        ruleService.createRule(ownerContextA, new CommissionRuleCreateRequest(
                salespersonAId, "Standard 5%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.0000"), null, "PKR", today.minusDays(30), null
        ));

        ObligationDetailResponse ob = obligationService.createObligation(ownerContextA, new ObligationCreateRequest(
                customerAId, "RECEIVABLE", "80000.00", today.minusDays(5).toString(), today.plusDays(25).toString(),
                "INV-TEST-002", "Order 2", null, null
        ));

        PaymentAllocateRequest payReq = new PaymentAllocateRequest(
                customerAId,
                "RECEIVABLE",
                bankAccountId,
                today.toString(),
                "80000.00",
                List.of(new AllocationItemRequest(ob.id(), "80000.00")),
                "RCP-TEST-002",
                "Cash collection",
                false
        );
        PaymentAllocateResponse allocResp = paymentAllocationService.allocatePayment(ownerContextA, "idemp-comm-2", payReq);
        UUID allocationId = allocResp.allocations().get(0).allocationId();

        List<EligibleCollectionResponse> eligible = earningService.discoverEligibleCollections(ownerContextA);
        assertThat(eligible).hasSize(1);
        EligibleCollectionResponse col = eligible.get(0);

        // 2. Record Earning
        CommissionEarningCreateRequest earnReq = new CommissionEarningCreateRequest(
                CommissionSourceType.OBLIGATION_ALLOCATION,
                col.allocationId().toString(),
                col.salespersonId(),
                col.applicableRuleId(),
                "Commission on cash payment"
        );
        CommissionEarningResponse earned = earningService.recordEarning(ownerContextA, earnReq);

        assertThat(earned.id()).isNotNull();
        assertThat(earned.commissionAmount()).isEqualByComparingTo("4000.00");
        assertThat(earned.saleAmount()).isEqualByComparingTo("80000.00");
        assertThat(earned.sourceFingerprint()).isEqualTo("ALLOCATION:" + allocationId);
        assertThat(earned.status()).isEqualTo(CommissionEarningStatus.EARNED);

        // 3. Verify eligible collections now reflects alreadyEarned = true
        List<EligibleCollectionResponse> afterRecord = earningService.discoverEligibleCollections(ownerContextA);
        assertThat(afterRecord).hasSize(1);
        assertThat(afterRecord.get(0).alreadyEarned()).isTrue();
        assertThat(afterRecord.get(0).existingEarningId()).isEqualTo(earned.id());

        // 4. Duplicate earning attempt is blocked with conflict
        assertThatThrownBy(() -> earningService.recordEarning(ownerContextA, earnReq))
                .isInstanceOf(CommissionEarningConflictException.class);
    }

    @Test
    @DisplayName("IT-COMM-B-04: Tenant and role isolation on earnings and summary")
    void testTenantAndRoleScoping() {
        // Create earning in Business A
        ruleService.createRule(ownerContextA, new CommissionRuleCreateRequest(
                salespersonAId, "Standard 5%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.0000"), null, "PKR", today.minusDays(30), null
        ));

        ObligationDetailResponse ob = obligationService.createObligation(ownerContextA, new ObligationCreateRequest(
                customerAId, "RECEIVABLE", "50000.00", today.minusDays(5).toString(), today.plusDays(25).toString(),
                "INV-TEST-003", "Order 3", null, null
        ));

        PaymentAllocateRequest payReq = new PaymentAllocateRequest(
                customerAId, "RECEIVABLE", bankAccountId, today.toString(), "50000.00",
                List.of(new AllocationItemRequest(ob.id(), "50000.00")),
                "RCP-TEST-003", "Cash", false
        );
        paymentAllocationService.allocatePayment(ownerContextA, "idemp-comm-3", payReq);

        EligibleCollectionResponse col = earningService.discoverEligibleCollections(ownerContextA).get(0);
        CommissionEarningCreateRequest earnReq = new CommissionEarningCreateRequest(
                CommissionSourceType.OBLIGATION_ALLOCATION,
                col.allocationId().toString(),
                col.salespersonId(),
                col.applicableRuleId(),
                null
        );
        earningService.recordEarning(ownerContextA, earnReq);

        // Business B sees 0 earnings and 0 summary
        List<CommissionEarningResponse> bizBEarnings = earningService.listEarnings(ownerContextB, null, null);
        assertThat(bizBEarnings).isEmpty();

        CommissionSummaryResponse bizBSummary = earningService.getSummary(ownerContextB, null);
        assertThat(bizBSummary.totalEarnedAmount()).isEqualByComparingTo("0.00");

        // Salesperson A can view their own earnings
        List<CommissionEarningResponse> repEarnings = earningService.listEarnings(salespersonContextA, null, null);
        assertThat(repEarnings).hasSize(1);
        assertThat(repEarnings.get(0).commissionAmount()).isEqualByComparingTo("2500.00");

        // Salesperson A attempting to pass someone else's ID is rejected
        UUID fakeId = UUID.randomUUID();
        assertThatThrownBy(() -> earningService.listEarnings(salespersonContextA, fakeId, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("IT-COMM-B-05: Commission operations cause zero mutations to financial accounts or cash ledger")
    void testZeroFinancialLedgerMutations() {
        BigDecimal initialBalance = accountRepository.findById(bankAccountId).orElseThrow().getOpeningBalance();
        Integer initialMovementCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_movements WHERE business_id = ?",
                Integer.class,
                businessA
        );

        // Create rule
        ruleService.createRule(ownerContextA, new CommissionRuleCreateRequest(
                salespersonAId, "Standard 10%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("10.0000"), null, "PKR", today.minusDays(30), null
        ));

        // Create preview
        earningService.previewCalculation(ownerContextA, new CommissionCalculationRequest(
                CommissionSourceType.OBLIGATION_ALLOCATION,
                "preview-source",
                customerAId,
                salespersonAId,
                new BigDecimal("50000.00"),
                today,
                "PKR",
                null
        ));

        // Check movements count and bank account balance
        Integer afterMovementCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_movements WHERE business_id = ?",
                Integer.class,
                businessA
        );
        BigDecimal currentBalance = accountRepository.findById(bankAccountId).orElseThrow().getOpeningBalance();

        assertThat(afterMovementCount).isEqualTo(initialMovementCount);
        assertThat(currentBalance).isEqualByComparingTo(initialBalance);
    }
}
