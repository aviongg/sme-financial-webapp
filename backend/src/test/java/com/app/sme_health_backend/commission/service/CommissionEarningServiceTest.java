package com.app.sme_health_backend.commission.service;

import com.app.sme_health_backend.commission.dto.*;
import com.app.sme_health_backend.commission.entity.CommissionEarning;
import com.app.sme_health_backend.commission.entity.CommissionRule;
import com.app.sme_health_backend.commission.entity.SalesAssignment;
import com.app.sme_health_backend.commission.entity.SalespersonProfile;
import com.app.sme_health_backend.commission.exception.CommissionEarningConflictException;
import com.app.sme_health_backend.commission.model.*;
import com.app.sme_health_backend.commission.repository.CommissionEarningRepository;
import com.app.sme_health_backend.commission.repository.CommissionRuleRepository;
import com.app.sme_health_backend.commission.repository.SalesAssignmentRepository;
import com.app.sme_health_backend.commission.repository.SalespersonProfileRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyStatus;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.obligation.AllocationStatus;
import com.app.sme_health_backend.obligation.AllocationType;
import com.app.sme_health_backend.obligation.FinancialObligation;
import com.app.sme_health_backend.obligation.FinancialObligationRepository;
import com.app.sme_health_backend.obligation.ObligationPaymentAllocation;
import com.app.sme_health_backend.obligation.ObligationPaymentAllocationRepository;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommissionEarningServiceTest {

    @Mock
    private CommissionEarningRepository earningRepository;

    @Mock
    private CommissionRuleRepository ruleRepository;

    @Mock
    private CommissionRuleService ruleService;

    @Mock
    private CommissionCalculationService calculationService;

    @Mock
    private SalespersonProfileRepository salespersonRepository;

    @Mock
    private SalesAssignmentRepository assignmentRepository;

    @Mock
    private CounterpartyRepository counterpartyRepository;

    @Mock
    private ObligationPaymentAllocationRepository allocationRepository;

    @Mock
    private FinancialObligationRepository obligationRepository;

    @InjectMocks
    private CommissionEarningService service;

    private UUID businessId;
    private UUID userId;
    private BusinessAccessContext ownerContext;
    private BusinessAccessContext salespersonContext;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        ownerContext = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        salespersonContext = new BusinessAccessContext(userId, businessId, MembershipRole.SALESPERSON);
    }

    @Test
    @DisplayName("Discovers eligible cash collection for customer with active salesperson assignment")
    void discoverEligibleCollections_Success() {
        UUID allocId = UUID.randomUUID();
        UUID obligationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID salespersonId = UUID.randomUUID();
        LocalDate colDate = LocalDate.of(2026, 2, 10);

        ObligationPaymentAllocation alloc = new ObligationPaymentAllocation();
        alloc.setId(allocId);
        alloc.setBusinessId(businessId);
        alloc.setObligationId(obligationId);
        alloc.setAllocationType(AllocationType.CASH);
        alloc.setStatus(AllocationStatus.EFFECTIVE);
        alloc.setAmount(new BigDecimal("75000.00"));
        alloc.setEffectiveDate(colDate);

        FinancialObligation obligation = new FinancialObligation();
        obligation.setId(obligationId);
        obligation.setBusinessId(businessId);
        obligation.setCounterpartyId(customerId);
        obligation.setDirection(ObligationDirection.RECEIVABLE);
        obligation.setCurrency("PKR");

        Counterparty customer = new Counterparty();
        customer.setId(customerId);
        customer.setBusinessId(businessId);
        customer.setDisplayName("Pak Pharma Ltd");
        customer.setType(CounterpartyType.CUSTOMER);

        SalesAssignment assignment = new SalesAssignment(
                businessId, salespersonId, customerId, LocalDate.of(2026, 1, 1), null, userId, null
        );

        SalespersonProfile salesperson = new SalespersonProfile(
                businessId, null, "Tariq Mahmood", "tariq@example.com", null, null
        );
        salesperson.setId(salespersonId);
        salesperson.setStatus(SalespersonStatus.ACTIVE);

        CommissionRule rule = new CommissionRule(
                businessId, null, "General 5%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.0000"), null, "PKR", LocalDate.of(2026, 1, 1), null
        );
        rule.setId(UUID.randomUUID());

        when(allocationRepository.findByBusinessId(businessId)).thenReturn(List.of(alloc));
        when(obligationRepository.findByBusinessId(businessId)).thenReturn(List.of(obligation));
        when(counterpartyRepository.findAllByBusinessIdAndStatusOrderByDisplayNameAsc(businessId, CounterpartyStatus.ACTIVE))
                .thenReturn(List.of(customer));
        when(assignmentRepository.findByBusinessId(businessId)).thenReturn(List.of(assignment));
        when(salespersonRepository.findByBusinessId(businessId)).thenReturn(List.of(salesperson));
        when(ruleService.findApplicableRule(businessId, salespersonId, colDate)).thenReturn(Optional.of(rule));
        when(calculationService.calculateCommission(rule, new BigDecimal("75000.00"))).thenReturn(new BigDecimal("3750.00"));
        when(earningRepository.findByBusinessId(businessId)).thenReturn(List.of());

        List<EligibleCollectionResponse> results = service.discoverEligibleCollections(ownerContext);

        assertThat(results).hasSize(1);
        EligibleCollectionResponse item = results.get(0);
        assertThat(item.allocationId()).isEqualTo(allocId);
        assertThat(item.counterpartyName()).isEqualTo("Pak Pharma Ltd");
        assertThat(item.salespersonName()).isEqualTo("Tariq Mahmood");
        assertThat(item.collectionAmount()).isEqualByComparingTo("75000.00");
        assertThat(item.estimatedCommission()).isEqualByComparingTo("3750.00");
        assertThat(item.alreadyEarned()).isFalse();
    }

    @Test
    @DisplayName("Records earned commission from verified collection allocation")
    void recordEarning_Success() {
        UUID allocId = UUID.randomUUID();
        UUID obligationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID salespersonId = UUID.randomUUID();
        LocalDate colDate = LocalDate.of(2026, 2, 10);

        ObligationPaymentAllocation alloc = new ObligationPaymentAllocation();
        alloc.setId(allocId);
        alloc.setBusinessId(businessId);
        alloc.setObligationId(obligationId);
        alloc.setAllocationType(AllocationType.CASH);
        alloc.setStatus(AllocationStatus.EFFECTIVE);
        alloc.setAmount(new BigDecimal("50000.00"));
        alloc.setEffectiveDate(colDate);

        FinancialObligation obligation = new FinancialObligation();
        obligation.setId(obligationId);
        obligation.setBusinessId(businessId);
        obligation.setCounterpartyId(customerId);
        obligation.setDirection(ObligationDirection.RECEIVABLE);
        obligation.setCurrency("PKR");

        Counterparty customer = new Counterparty();
        customer.setId(customerId);
        customer.setBusinessId(businessId);
        customer.setDisplayName("Pak Pharma Ltd");

        SalesAssignment assignment = new SalesAssignment(
                businessId, salespersonId, customerId, LocalDate.of(2026, 1, 1), null, userId, null
        );

        SalespersonProfile salesperson = new SalespersonProfile(
                businessId, null, "Tariq Mahmood", "tariq@example.com", null, null
        );
        salesperson.setId(salespersonId);
        salesperson.setStatus(SalespersonStatus.ACTIVE);

        CommissionRule rule = new CommissionRule(
                businessId, null, "General 5%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.0000"), null, "PKR", LocalDate.of(2026, 1, 1), null
        );
        rule.setId(UUID.randomUUID());

        when(allocationRepository.findByIdAndBusinessId(allocId, businessId)).thenReturn(Optional.of(alloc));
        when(obligationRepository.findByIdAndBusinessId(obligationId, businessId)).thenReturn(Optional.of(obligation));
        when(earningRepository.existsByBusinessIdAndSourceFingerprint(businessId, "ALLOCATION:" + allocId)).thenReturn(false);
        when(assignmentRepository.findByBusinessIdAndCounterpartyId(businessId, customerId)).thenReturn(List.of(assignment));
        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.of(salesperson));
        when(counterpartyRepository.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(customer));
        when(ruleService.findApplicableRule(businessId, salespersonId, colDate)).thenReturn(Optional.of(rule));
        when(calculationService.calculateCommission(rule, new BigDecimal("50000.00"))).thenReturn(new BigDecimal("2500.00"));
        when(earningRepository.save(any(CommissionEarning.class))).thenAnswer(i -> {
            CommissionEarning e = i.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        CommissionEarningCreateRequest request = new CommissionEarningCreateRequest(
                CommissionSourceType.OBLIGATION_ALLOCATION, allocId.toString(), null, null, "Q1 Commission"
        );

        CommissionEarningResponse res = service.recordEarning(ownerContext, request);

        assertThat(res).isNotNull();
        assertThat(res.salespersonName()).isEqualTo("Tariq Mahmood");
        assertThat(res.counterpartyName()).isEqualTo("Pak Pharma Ltd");
        assertThat(res.commissionAmount()).isEqualByComparingTo("2500.00");
        assertThat(res.status()).isEqualTo(CommissionEarningStatus.EARNED);
    }

    @Test
    @DisplayName("Duplicate earning recognition for same allocation throws 409 Conflict")
    void recordEarning_DuplicateConflict() {
        UUID allocId = UUID.randomUUID();
        UUID obligationId = UUID.randomUUID();

        ObligationPaymentAllocation alloc = new ObligationPaymentAllocation();
        alloc.setId(allocId);
        alloc.setBusinessId(businessId);
        alloc.setObligationId(obligationId);
        alloc.setAllocationType(AllocationType.CASH);
        alloc.setStatus(AllocationStatus.EFFECTIVE);

        FinancialObligation obligation = new FinancialObligation();
        obligation.setId(obligationId);
        obligation.setDirection(ObligationDirection.RECEIVABLE);

        when(allocationRepository.findByIdAndBusinessId(allocId, businessId)).thenReturn(Optional.of(alloc));
        when(obligationRepository.findByIdAndBusinessId(obligationId, businessId)).thenReturn(Optional.of(obligation));
        when(earningRepository.existsByBusinessIdAndSourceFingerprint(businessId, "ALLOCATION:" + allocId)).thenReturn(true);

        CommissionEarningCreateRequest request = new CommissionEarningCreateRequest(
                CommissionSourceType.OBLIGATION_ALLOCATION, allocId.toString(), null, null, null
        );

        assertThatThrownBy(() -> service.recordEarning(ownerContext, request))
                .isInstanceOf(CommissionEarningConflictException.class)
                .hasMessageContaining("already been earned");
    }

    @Test
    @DisplayName("Restricted salesperson role cannot view other salespersons' earnings")
    void listEarnings_SalespersonRowLevelScoping() {
        UUID salesProfileId = UUID.randomUUID();
        SalespersonProfile profile = new SalespersonProfile(businessId, userId, "Rep", "rep@example.com", null, null);
        profile.setId(salesProfileId);
        profile.setStatus(SalespersonStatus.ACTIVE);

        when(salespersonRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(profile));
        when(earningRepository.findByBusinessIdAndSalespersonId(businessId, salesProfileId)).thenReturn(List.of());

        // Salesperson requests own earnings (null filter or own profile ID)
        List<CommissionEarningResponse> list = service.listEarnings(salespersonContext, null, null);
        assertThat(list).isEmpty();
        verify(earningRepository).findByBusinessIdAndSalespersonId(businessId, salesProfileId);

        // Salesperson supplies another salesperson's ID in query filter -> AccessDeniedException!
        UUID anotherSalespersonId = UUID.randomUUID();
        assertThatThrownBy(() -> service.listEarnings(salespersonContext, anotherSalespersonId, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Suspended salesperson user is denied access to earnings")
    void listEarnings_SuspendedSalespersonDenied() {
        SalespersonProfile profile = new SalespersonProfile(businessId, userId, "Rep", "rep@example.com", null, null);
        profile.setId(UUID.randomUUID());
        profile.setStatus(SalespersonStatus.SUSPENDED);

        when(salespersonRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.listEarnings(salespersonContext, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("suspended");
    }
}
