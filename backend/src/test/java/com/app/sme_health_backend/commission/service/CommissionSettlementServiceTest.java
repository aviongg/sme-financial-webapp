package com.app.sme_health_backend.commission.service;

import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.commission.dto.*;
import com.app.sme_health_backend.commission.entity.*;
import com.app.sme_health_backend.commission.exception.CommissionSettlementConflictException;
import com.app.sme_health_backend.commission.exception.CommissionSettlementValidationException;
import com.app.sme_health_backend.commission.model.*;
import com.app.sme_health_backend.commission.repository.*;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommissionSettlementServiceTest {

    @Mock private CommissionEarningRepository earningRepository;
    @Mock private CommissionAdvanceRepository advanceRepository;
    @Mock private CommissionPayoutRepository payoutRepository;
    @Mock private SalespersonProfileRepository salespersonRepository;
    @Mock private CommissionRuleRepository ruleRepository;
    @Mock private CounterpartyRepository counterpartyRepository;
    @Mock private FinancialAccountRepository accountRepository;
    @Mock private FinancialTransactionRepository transactionRepository;
    @Mock private AccountMovementRepository movementRepository;
    @Mock private BusinessAuthorizationService authService;

    @InjectMocks
    private CommissionSettlementService settlementService;

    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID salespersonId = UUID.randomUUID();
    private final UUID counterpartyId = UUID.randomUUID();
    private final UUID ruleId = UUID.randomUUID();
    private final UUID earningId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID advanceId = UUID.randomUUID();

    private BusinessAccessContext ownerContext;
    private BusinessAccessContext salespersonContext;
    private SalespersonProfile salesperson;
    private FinancialAccount bankAccount;
    private CommissionEarning earning;
    private CommissionAdvance advance;

    @BeforeEach
    void setUp() {
        ownerContext = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        salespersonContext = new BusinessAccessContext(UUID.randomUUID(), businessId, MembershipRole.SALESPERSON);

        salesperson = new SalespersonProfile(businessId, UUID.randomUUID(), "Tariq Mahmood", "tariq@example.com", "+923001234567", "North Region");
        salesperson.setId(salespersonId);

        bankAccount = new FinancialAccount(
                businessId, "Meezan Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("500000.00"), LocalDate.of(2026, 1, 1),
                AccountStatus.ACTIVE, null, null, "Operating Account"
        );
        bankAccount.setId(accountId);

        earning = new CommissionEarning(
                businessId, salespersonId, ruleId, counterpartyId,
                CommissionSourceType.OBLIGATION_ALLOCATION, "ALLOC-001", "FINGERPRINT-001",
                new BigDecimal("100000.00"), new BigDecimal("5.0000"), new BigDecimal("5000.00"),
                "PKR", CommissionEarningStatus.EARNED, "Q1 sale"
        );
        earning.setId(earningId);

        advance = new CommissionAdvance(
                businessId, salespersonId, new BigDecimal("3000.00"), BigDecimal.ZERO,
                "PKR", LocalDate.of(2026, 11, 1), accountId, UUID.randomUUID(),
                CommissionAdvanceStatus.ACTIVE, "Travel advance", "ADV-IDEMP-001", userId
        );
        advance.setId(advanceId);

        lenient().when(authService.hasRolePermission(eq(MembershipRole.OWNER), any())).thenReturn(true);
        lenient().when(authService.hasRolePermission(eq(MembershipRole.SALESPERSON), eq(BusinessPermission.COMMISSION_READ))).thenReturn(true);
        lenient().when(authService.hasRolePermission(eq(MembershipRole.SALESPERSON), eq(BusinessPermission.COMMISSION_MANAGE))).thenReturn(false);
    }

    @Test
    @DisplayName("Owner approves EARNED commission with default full amount")
    void approveEarning_FullAmount_Success() {
        when(earningRepository.findForUpdateByIdAndBusinessId(earningId, businessId)).thenReturn(Optional.of(earning));
        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.of(salesperson));
        when(earningRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommissionEarningResponse res = settlementService.approveEarning(ownerContext, earningId, null);

        assertThat(res.status()).isEqualTo(CommissionEarningStatus.APPROVED);
        assertThat(res.approvedAmount()).isEqualByComparingTo("5000.00");
        assertThat(res.remainingPayable()).isEqualByComparingTo("5000.00");
        assertThat(earning.getApprovedAt()).isNotNull();
        assertThat(earning.getApprovedByUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Owner approves commission with custom approved amount")
    void approveEarning_CustomAmount_Success() {
        when(earningRepository.findForUpdateByIdAndBusinessId(earningId, businessId)).thenReturn(Optional.of(earning));
        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.of(salesperson));
        when(earningRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommissionApprovalRequest req = new CommissionApprovalRequest(new BigDecimal("4000.00"), "Partial approval per policy");
        CommissionEarningResponse res = settlementService.approveEarning(ownerContext, earningId, req);

        assertThat(res.status()).isEqualTo(CommissionEarningStatus.APPROVED);
        assertThat(res.approvedAmount()).isEqualByComparingTo("4000.00");
        assertThat(res.remainingPayable()).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("Salesperson cannot approve their own commission (AccessDeniedException)")
    void approveEarning_SelfApproval_Blocked() {
        // Set salesperson profile user ID to match caller's user ID
        salesperson.setUserId(userId);
        when(earningRepository.findForUpdateByIdAndBusinessId(earningId, businessId)).thenReturn(Optional.of(earning));
        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.of(salesperson));

        assertThatThrownBy(() -> settlementService.approveEarning(ownerContext, earningId, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Salespeople cannot approve their own commissions");
    }

    @Test
    @DisplayName("Approval of already approved or void commission fails with Conflict")
    void approveEarning_NonEarned_Conflict() {
        earning.setStatus(CommissionEarningStatus.APPROVED);
        when(earningRepository.findForUpdateByIdAndBusinessId(earningId, businessId)).thenReturn(Optional.of(earning));

        assertThatThrownBy(() -> settlementService.approveEarning(ownerContext, earningId, null))
                .isInstanceOf(CommissionSettlementConflictException.class)
                .hasMessageContaining("Only EARNED commissions can be approved");
    }

    @Test
    @DisplayName("Create advance posts cash movement and records advance")
    void createAdvance_Success() {
        CommissionAdvanceCreateRequest req = new CommissionAdvanceCreateRequest(
                salespersonId, accountId, new BigDecimal("3000.00"), LocalDate.of(2026, 11, 5), "Sales advance"
        );

        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.of(salesperson));
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(bankAccount));
        when(transactionRepository.saveAndFlush(any())).thenAnswer(inv -> {
            FinancialTransaction t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });
        when(movementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(advanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommissionAdvanceResponse res = settlementService.createAdvance(ownerContext, "ADV-KEY-1", req);

        assertThat(res.amount()).isEqualByComparingTo("3000.00");
        assertThat(res.appliedAmount()).isEqualByComparingTo("0.00");
        assertThat(res.unappliedAmount()).isEqualByComparingTo("3000.00");
        assertThat(res.status()).isEqualTo(CommissionAdvanceStatus.ACTIVE);

        verify(transactionRepository, times(1)).saveAndFlush(any(FinancialTransaction.class));
        verify(movementRepository, times(1)).saveAndFlush(any(AccountMovement.class));
    }

    @Test
    @DisplayName("Applying advance reduces remaining payable with ZERO additional cash movement")
    void applyAdvance_Success() {
        earning.setStatus(CommissionEarningStatus.APPROVED);
        earning.setApprovedAmount(new BigDecimal("5000.00"));

        when(earningRepository.findForUpdateByIdAndBusinessId(earningId, businessId)).thenReturn(Optional.of(earning));
        when(advanceRepository.findForUpdateByIdAndBusinessId(advanceId, businessId)).thenReturn(Optional.of(advance));
        when(payoutRepository.sumEffectiveSettlementsByEarning(businessId, earningId)).thenReturn(BigDecimal.ZERO);
        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.of(salesperson));
        when(payoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommissionAdvanceApplyRequest req = new CommissionAdvanceApplyRequest(advanceId, new BigDecimal("2000.00"), "Apply advance");
        CommissionPayoutResponse res = settlementService.applyAdvance(ownerContext, "APPLY-KEY-1", earningId, req);

        assertThat(res.payoutType()).isEqualTo(CommissionPayoutType.ADVANCE_APPLICATION);
        assertThat(res.amount()).isEqualByComparingTo("2000.00");
        assertThat(res.financialTransactionId()).isNull(); // ZERO cash movement!
        assertThat(res.status()).isEqualTo(CommissionPayoutStatus.SETTLED);

        assertThat(advance.getAppliedAmount()).isEqualByComparingTo("2000.00");
        assertThat(earning.getStatus()).isEqualTo(CommissionEarningStatus.PARTIALLY_SETTLED);

        // Verify zero transactions or movements were posted
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Direct cash payout posts real cash movement and updates earning status")
    void recordPayout_Success() {
        earning.setStatus(CommissionEarningStatus.APPROVED);
        earning.setApprovedAmount(new BigDecimal("5000.00"));

        when(earningRepository.findForUpdateByIdAndBusinessId(earningId, businessId)).thenReturn(Optional.of(earning));
        when(payoutRepository.sumEffectiveSettlementsByEarning(businessId, earningId)).thenReturn(BigDecimal.ZERO);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(bankAccount));
        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.of(salesperson));
        when(transactionRepository.saveAndFlush(any())).thenAnswer(inv -> {
            FinancialTransaction t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });
        when(movementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(payoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommissionPayoutCreateRequest req = new CommissionPayoutCreateRequest(
                accountId, new BigDecimal("5000.00"), LocalDate.of(2026, 11, 8), "Final payout"
        );

        CommissionPayoutResponse res = settlementService.recordPayout(ownerContext, "PAY-KEY-1", earningId, req);

        assertThat(res.payoutType()).isEqualTo(CommissionPayoutType.PAYOUT);
        assertThat(res.amount()).isEqualByComparingTo("5000.00");
        assertThat(res.financialTransactionId()).isNotNull(); // Cash movement recorded!
        assertThat(res.status()).isEqualTo(CommissionPayoutStatus.SETTLED);

        assertThat(earning.getStatus()).isEqualTo(CommissionEarningStatus.SETTLED);
        assertThat(earning.getPaidAt()).isNotNull();

        verify(transactionRepository, times(1)).saveAndFlush(any());
        verify(movementRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Overpayment exceeding remaining payable is rejected with Conflict")
    void recordPayout_Overpayment_Rejected() {
        earning.setStatus(CommissionEarningStatus.APPROVED);
        earning.setApprovedAmount(new BigDecimal("5000.00"));

        // Already settled 4,000.00 -> remaining payable is 1,000.00
        when(earningRepository.findForUpdateByIdAndBusinessId(earningId, businessId)).thenReturn(Optional.of(earning));
        when(payoutRepository.sumEffectiveSettlementsByEarning(businessId, earningId)).thenReturn(new BigDecimal("4000.00"));

        CommissionPayoutCreateRequest req = new CommissionPayoutCreateRequest(
                accountId, new BigDecimal("2000.00"), LocalDate.of(2026, 11, 8), "Excess payout"
        );

        assertThatThrownBy(() -> settlementService.recordPayout(ownerContext, "OVERPAY-KEY", earningId, req))
                .isInstanceOf(CommissionSettlementConflictException.class)
                .hasMessageContaining("exceeds remaining payable balance");
    }
}
