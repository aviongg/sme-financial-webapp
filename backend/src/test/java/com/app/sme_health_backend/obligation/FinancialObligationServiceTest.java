package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyStatus;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.obligation.aging.AgingBucket;
import com.app.sme_health_backend.obligation.aging.ObligationAgingResult;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.obligation.dto.*;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FinancialObligationServiceTest {

    private FinancialObligationRepository obligationRepository;
    private ObligationPaymentAllocationRepository allocationRepository;
    private CounterpartyRepository counterpartyRepository;
    private FinancialAccountRepository accountRepository;
    private FinancialTransactionRepository transactionRepository;
    private AccountMovementRepository movementRepository;
    private FinancialIdempotencyRepository idempotencyRepository;
    private SecurityAuditRepository securityAuditRepository;
    private ObjectMapper objectMapper;
    private FinancialObligationService service;

    private UUID businessId;
    private UUID userId;
    private BusinessAccessContext ownerContext;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        obligationRepository = mock(FinancialObligationRepository.class);
        allocationRepository = mock(ObligationPaymentAllocationRepository.class);
        counterpartyRepository = mock(CounterpartyRepository.class);
        accountRepository = mock(FinancialAccountRepository.class);
        transactionRepository = mock(FinancialTransactionRepository.class);
        movementRepository = mock(AccountMovementRepository.class);
        idempotencyRepository = mock(FinancialIdempotencyRepository.class);
        securityAuditRepository = mock(SecurityAuditRepository.class);
        objectMapper = new ObjectMapper();

        service = new FinancialObligationService(
                obligationRepository,
                allocationRepository,
                counterpartyRepository,
                accountRepository,
                transactionRepository,
                movementRepository,
                idempotencyRepository,
                securityAuditRepository,
                objectMapper
        );

        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        ownerContext = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        today = LocalDate.now(ZoneId.of("Asia/Karachi"));
    }

    @Test
    @DisplayName("Create receivable: successfully creates receivable for customer counterparty")
    void testCreateReceivable_Success() {
        UUID cpId = UUID.randomUUID();
        Counterparty customer = new Counterparty(businessId, "Customer A", CounterpartyType.CUSTOMER, null, null, null, null, null);
        customer.setId(cpId);
        when(counterpartyRepository.findByIdAndBusinessId(cpId, businessId)).thenReturn(Optional.of(customer));

        when(obligationRepository.saveAndFlush(any(FinancialObligation.class))).thenAnswer(inv -> {
            FinancialObligation o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        ObligationCreateRequest request = new ObligationCreateRequest(
                cpId,
                "RECEIVABLE",
                "100000.00",
                today.toString(),
                today.plusDays(15).toString(),
                "INV-001",
                "Sale of goods",
                "MANUAL_DECLARATION",
                null
        );

        ObligationDetailResponse response = service.createObligation(ownerContext, request);

        assertNotNull(response);
        assertEquals("RECEIVABLE", response.direction());
        assertEquals("100000.00", response.originalAmount());
        assertEquals("0.00", response.paidAmount());
        assertEquals("100000.00", response.outstandingAmount());
        assertEquals("OPEN", response.status());
        assertEquals("UNPAID", response.settlementStatus());
        assertEquals("Customer A", response.counterpartyName());
        verify(securityAuditRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Create payable: successfully creates payable for supplier counterparty")
    void testCreatePayable_Success() {
        UUID cpId = UUID.randomUUID();
        Counterparty supplier = new Counterparty(businessId, "Supplier B", CounterpartyType.SUPPLIER, null, null, null, null, null);
        supplier.setId(cpId);
        when(counterpartyRepository.findByIdAndBusinessId(cpId, businessId)).thenReturn(Optional.of(supplier));

        when(obligationRepository.saveAndFlush(any(FinancialObligation.class))).thenAnswer(inv -> {
            FinancialObligation o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        ObligationCreateRequest request = new ObligationCreateRequest(
                cpId,
                "PAYABLE",
                "50000.00",
                today.toString(),
                today.plusDays(30).toString(),
                "BILL-100",
                "Raw materials",
                "MANUAL_DECLARATION",
                null
        );

        ObligationDetailResponse response = service.createObligation(ownerContext, request);

        assertNotNull(response);
        assertEquals("PAYABLE", response.direction());
        assertEquals("50000.00", response.originalAmount());
        assertEquals("0.00", response.paidAmount());
        assertEquals("50000.00", response.outstandingAmount());
        assertEquals("OPEN", response.status());
        assertEquals("UNPAID", response.settlementStatus());
    }

    @Test
    @DisplayName("Create obligation: counterparty with type BOTH is compatible with both directions")
    void testCreateObligation_BothCompatibleCounterparty() {
        UUID cpId = UUID.randomUUID();
        Counterparty bothCp = new Counterparty(businessId, "Trading Partner", CounterpartyType.BOTH, null, null, null, null, null);
        bothCp.setId(cpId);
        when(counterpartyRepository.findByIdAndBusinessId(cpId, businessId)).thenReturn(Optional.of(bothCp));

        when(obligationRepository.saveAndFlush(any(FinancialObligation.class))).thenAnswer(inv -> {
            FinancialObligation o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        ObligationCreateRequest recReq = new ObligationCreateRequest(
                cpId, "RECEIVABLE", "1000.00", today.toString(), null, null, null, null, null
        );
        ObligationDetailResponse recResp = service.createObligation(ownerContext, recReq);
        assertEquals("RECEIVABLE", recResp.direction());

        ObligationCreateRequest payReq = new ObligationCreateRequest(
                cpId, "PAYABLE", "2000.00", today.toString(), null, null, null, null, null
        );
        ObligationDetailResponse payResp = service.createObligation(ownerContext, payReq);
        assertEquals("PAYABLE", payResp.direction());
    }

    @Test
    @DisplayName("Create receivable: rejects supplier-only counterparty")
    void testCreateReceivable_SupplierIncompatible() {
        UUID cpId = UUID.randomUUID();
        Counterparty supplier = new Counterparty(businessId, "Supplier Only", CounterpartyType.SUPPLIER, null, null, null, null, null);
        supplier.setId(cpId);
        when(counterpartyRepository.findByIdAndBusinessId(cpId, businessId)).thenReturn(Optional.of(supplier));

        ObligationCreateRequest request = new ObligationCreateRequest(
                cpId, "RECEIVABLE", "1000.00", today.toString(), null, null, null, null, null
        );

        ObligationValidationException ex = assertThrows(ObligationValidationException.class,
                () -> service.createObligation(ownerContext, request));
        assertTrue(ex.getErrors().containsKey("counterpartyId"));
    }

    @Test
    @DisplayName("Create payable: rejects customer-only counterparty")
    void testCreatePayable_CustomerIncompatible() {
        UUID cpId = UUID.randomUUID();
        Counterparty customer = new Counterparty(businessId, "Customer Only", CounterpartyType.CUSTOMER, null, null, null, null, null);
        customer.setId(cpId);
        when(counterpartyRepository.findByIdAndBusinessId(cpId, businessId)).thenReturn(Optional.of(customer));

        ObligationCreateRequest request = new ObligationCreateRequest(
                cpId, "PAYABLE", "1000.00", today.toString(), null, null, null, null, null
        );

        ObligationValidationException ex = assertThrows(ObligationValidationException.class,
                () -> service.createObligation(ownerContext, request));
        assertTrue(ex.getErrors().containsKey("counterpartyId"));
    }

    @Test
    @DisplayName("Create obligation: rejects archived counterparty")
    void testCreateObligation_ArchivedCounterparty() {
        UUID cpId = UUID.randomUUID();
        Counterparty archived = new Counterparty(businessId, "Archived CP", CounterpartyType.CUSTOMER, null, null, null, null, null);
        archived.setId(cpId);
        archived.setStatus(CounterpartyStatus.ARCHIVED);
        when(counterpartyRepository.findByIdAndBusinessId(cpId, businessId)).thenReturn(Optional.of(archived));

        ObligationCreateRequest request = new ObligationCreateRequest(
                cpId, "RECEIVABLE", "1000.00", today.toString(), null, null, null, null, null
        );

        ObligationValidationException ex = assertThrows(ObligationValidationException.class,
                () -> service.createObligation(ownerContext, request));
        assertTrue(ex.getErrors().containsKey("counterpartyId"));
    }

    @Test
    @DisplayName("Create obligation: rejects invalid amount formats")
    void testCreateObligation_InvalidAmounts() {
        UUID cpId = UUID.randomUUID();
        Counterparty cp = new Counterparty(businessId, "CP", CounterpartyType.CUSTOMER, null, null, null, null, null);
        cp.setId(cpId);
        when(counterpartyRepository.findByIdAndBusinessId(cpId, businessId)).thenReturn(Optional.of(cp));

        assertThrows(ObligationValidationException.class, () -> service.createObligation(ownerContext,
                new ObligationCreateRequest(cpId, "RECEIVABLE", "0.00", today.toString(), null, null, null, null, null)));

        assertThrows(ObligationValidationException.class, () -> service.createObligation(ownerContext,
                new ObligationCreateRequest(cpId, "RECEIVABLE", "-50.00", today.toString(), null, null, null, null, null)));

        assertThrows(ObligationValidationException.class, () -> service.createObligation(ownerContext,
                new ObligationCreateRequest(cpId, "RECEIVABLE", "100.555", today.toString(), null, null, null, null, null)));

        assertThrows(ObligationValidationException.class, () -> service.createObligation(ownerContext,
                new ObligationCreateRequest(cpId, "RECEIVABLE", "abc", today.toString(), null, null, null, null, null)));
    }

    @Test
    @DisplayName("Create obligation: rejects future issue date beyond today")
    void testCreateObligation_FutureIssueDate() {
        UUID cpId = UUID.randomUUID();
        Counterparty cp = new Counterparty(businessId, "CP", CounterpartyType.CUSTOMER, null, null, null, null, null);
        cp.setId(cpId);
        when(counterpartyRepository.findByIdAndBusinessId(cpId, businessId)).thenReturn(Optional.of(cp));

        ObligationCreateRequest request = new ObligationCreateRequest(
                cpId, "RECEIVABLE", "100.00", today.plusDays(1).toString(), null, null, null, null, null
        );

        ObligationValidationException ex = assertThrows(ObligationValidationException.class,
                () -> service.createObligation(ownerContext, request));
        assertTrue(ex.getErrors().containsKey("issueDate"));
    }

    @Test
    @DisplayName("Create obligation: rejects due date earlier than issue date")
    void testCreateObligation_DueDateBeforeIssueDate() {
        UUID cpId = UUID.randomUUID();
        Counterparty cp = new Counterparty(businessId, "CP", CounterpartyType.CUSTOMER, null, null, null, null, null);
        cp.setId(cpId);
        when(counterpartyRepository.findByIdAndBusinessId(cpId, businessId)).thenReturn(Optional.of(cp));

        ObligationCreateRequest request = new ObligationCreateRequest(
                cpId, "RECEIVABLE", "100.00", today.toString(), today.minusDays(5).toString(), null, null, null, null
        );

        ObligationValidationException ex = assertThrows(ObligationValidationException.class,
                () -> service.createObligation(ownerContext, request));
        assertTrue(ex.getErrors().containsKey("dueDate"));
    }

    @Test
    @DisplayName("Create obligation: rejects duplicate sourceId for same sourceType")
    void testCreateObligation_DuplicateSourceId() {
        UUID cpId = UUID.randomUUID();
        Counterparty cp = new Counterparty(businessId, "CP", CounterpartyType.CUSTOMER, null, null, null, null, null);
        cp.setId(cpId);
        when(counterpartyRepository.findByIdAndBusinessId(cpId, businessId)).thenReturn(Optional.of(cp));
        when(obligationRepository.existsByBusinessIdAndSourceTypeAndSourceId(businessId, ObligationSourceType.CUSTOMER_INVOICE, "INV-999"))
                .thenReturn(true);

        ObligationCreateRequest request = new ObligationCreateRequest(
                cpId, "RECEIVABLE", "100.00", today.toString(), null, null, null, "CUSTOMER_INVOICE", "INV-999"
        );

        ObligationValidationException ex = assertThrows(ObligationValidationException.class,
                () -> service.createObligation(ownerContext, request));
        assertTrue(ex.getErrors().containsKey("sourceId"));
    }

    @Test
    @DisplayName("Void obligation: succeeds when no effective settlements exist")
    void testVoidObligation_Success() {
        UUID obId = UUID.randomUUID();
        UUID cpId = UUID.randomUUID();
        FinancialObligation obligation = new FinancialObligation(
                businessId, cpId, ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                null, "REF-1", today, null, new BigDecimal("1000.00"), "PKR", null, userId
        );
        obligation.setId(obId);

        when(obligationRepository.findByIdAndBusinessId(obId, businessId)).thenReturn(Optional.of(obligation));
        when(allocationRepository.sumEffectiveAllocations(businessId, obId)).thenReturn(BigDecimal.ZERO);
        when(obligationRepository.saveAndFlush(any(FinancialObligation.class))).thenAnswer(inv -> inv.getArgument(0));

        ObligationDetailResponse response = service.voidObligation(ownerContext, obId, new ObligationVoidRequest("Entered by mistake"));

        assertEquals("VOIDED", response.status());
        assertEquals("VOIDED", response.settlementStatus());
        assertEquals("Entered by mistake", response.voidReason());
        assertNotNull(response.voidDate());
        verify(securityAuditRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Void obligation: rejected when effective settlements exist")
    void testVoidObligation_EffectiveSettlementsExist() {
        UUID obId = UUID.randomUUID();
        FinancialObligation obligation = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                null, "REF-1", today, null, new BigDecimal("1000.00"), "PKR", null, userId
        );
        obligation.setId(obId);

        when(obligationRepository.findByIdAndBusinessId(obId, businessId)).thenReturn(Optional.of(obligation));
        when(allocationRepository.sumEffectiveAllocations(businessId, obId)).thenReturn(new BigDecimal("400.00"));

        ObligationConflictException ex = assertThrows(ObligationConflictException.class,
                () -> service.voidObligation(ownerContext, obId, new ObligationVoidRequest("Wrong amount")));
        assertTrue(ex.getMessage().contains("Reverse settlements first"));
    }

    private FinancialAccount createAccount(UUID businessId, String accountName, AccountType accountType, String currency, BigDecimal openingBalance, LocalDate openingDate) {
        return new FinancialAccount(businessId, accountName, accountType, currency, 2, openingBalance, openingDate, AccountStatus.ACTIVE, null, null, null);
    }

    @Test
    @DisplayName("Settlement: partial customer payment on receivable creates transaction, movement, and allocation")
    void testRecordSettlement_PartialCustomerPayment() {
        UUID obId = UUID.randomUUID();
        UUID cpId = UUID.randomUUID();
        UUID accId = UUID.randomUUID();

        FinancialObligation obligation = new FinancialObligation(
                businessId, cpId, ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                null, "INV-01", today.minusDays(5), today.plusDays(10), new BigDecimal("100000.00"), "PKR", "Sales", userId
        );
        obligation.setId(obId);

        FinancialAccount account = createAccount(businessId, "Main Bank", AccountType.BANK, "PKR", new BigDecimal("50000.00"), today.minusDays(30));
        account.setId(accId);

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obId, businessId)).thenReturn(Optional.of(obligation));
        when(allocationRepository.sumEffectiveAllocations(businessId, obId)).thenReturn(BigDecimal.ZERO);
        when(accountRepository.findForUpdateByIdAndBusinessId(accId, businessId)).thenReturn(Optional.of(account));
        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), any())).thenReturn(Optional.empty());

        when(transactionRepository.saveAndFlush(any(FinancialTransaction.class))).thenAnswer(inv -> {
            FinancialTransaction tx = inv.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        when(allocationRepository.saveAndFlush(any(ObligationPaymentAllocation.class))).thenAnswer(inv -> {
            ObligationPaymentAllocation a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        ObligationSettlementRequest req = new ObligationSettlementRequest(
                accId, "40000.00", today.toString(), "TRX-444", "Customer check payment"
        );

        ObligationSettlementResponse resp = service.recordSettlement(ownerContext, obId, "idemp-key-1", req);

        assertNotNull(resp);
        assertEquals("40000.00", resp.amount());
        assertEquals("60000.00", resp.remainingOutstandingAmount());
        assertEquals("PARTIALLY_PAID", resp.settlementStatus());

        // Verify transaction posted as RECEIVABLE_COLLECTION, direction INCREASE
        verify(transactionRepository, times(1)).saveAndFlush(argThat(tx ->
                tx.getEventType() == TransactionEventType.RECEIVABLE_COLLECTION &&
                        tx.getMovementDirection() == MovementDirection.INCREASE &&
                        tx.getAmount().compareTo(new BigDecimal("40000.00")) == 0 &&
                        tx.getCounterpartyId().equals(cpId)
        ));

        // Verify account movement
        verify(movementRepository, times(1)).saveAndFlush(argThat(m ->
                m.getMovementDirection() == MovementDirection.INCREASE &&
                        m.getAmount().compareTo(new BigDecimal("40000.00")) == 0
        ));

        // Verify allocation
        verify(allocationRepository, times(1)).saveAndFlush(argThat(a ->
                a.getObligationId().equals(obId) &&
                        a.getAmount().compareTo(new BigDecimal("40000.00")) == 0 &&
                        a.getStatus() == AllocationStatus.EFFECTIVE
        ));
    }

    @Test
    @DisplayName("Settlement: remaining payment marks obligation FULLY_PAID with zero outstanding")
    void testRecordSettlement_RemainingPayment_FullyPaid() {
        UUID obId = UUID.randomUUID();
        UUID cpId = UUID.randomUUID();
        UUID accId = UUID.randomUUID();

        FinancialObligation obligation = new FinancialObligation(
                businessId, cpId, ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                null, "INV-01", today.minusDays(5), today.plusDays(10), new BigDecimal("100000.00"), "PKR", "Sales", userId
        );
        obligation.setId(obId);

        FinancialAccount account = createAccount(businessId, "Main Bank", AccountType.BANK, "PKR", new BigDecimal("50000.00"), today.minusDays(30));
        account.setId(accId);

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obId, businessId)).thenReturn(Optional.of(obligation));
        // Already paid 40000; remaining outstanding is 60000
        when(allocationRepository.sumEffectiveAllocations(businessId, obId)).thenReturn(new BigDecimal("40000.00"));
        when(accountRepository.findForUpdateByIdAndBusinessId(accId, businessId)).thenReturn(Optional.of(account));

        when(transactionRepository.saveAndFlush(any(FinancialTransaction.class))).thenAnswer(inv -> {
            FinancialTransaction tx = inv.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        when(allocationRepository.saveAndFlush(any(ObligationPaymentAllocation.class))).thenAnswer(inv -> {
            ObligationPaymentAllocation a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        ObligationSettlementRequest req = new ObligationSettlementRequest(
                accId, "60000.00", today.toString(), null, null
        );

        ObligationSettlementResponse resp = service.recordSettlement(ownerContext, obId, "idemp-key-2", req);

        assertEquals("60000.00", resp.amount());
        assertEquals("0.00", resp.remainingOutstandingAmount());
        assertEquals("FULLY_PAID", resp.settlementStatus());
    }

    @Test
    @DisplayName("Settlement: overpayment rejected with conflict exception")
    void testRecordSettlement_OverpaymentRejected() {
        UUID obId = UUID.randomUUID();
        FinancialObligation obligation = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                null, "INV-01", today, null, new BigDecimal("100000.00"), "PKR", null, userId
        );
        obligation.setId(obId);

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obId, businessId)).thenReturn(Optional.of(obligation));
        // Outstanding is 100000.00
        when(allocationRepository.sumEffectiveAllocations(businessId, obId)).thenReturn(BigDecimal.ZERO);

        ObligationSettlementRequest req = new ObligationSettlementRequest(
                UUID.randomUUID(), "100000.01", today.toString(), null, null
        );

        ObligationConflictException ex = assertThrows(ObligationConflictException.class,
                () -> service.recordSettlement(ownerContext, obId, "idemp-overpay", req));
        assertTrue(ex.getMessage().contains("exceeds outstanding balance"));
    }

    @Test
    @DisplayName("Settlement: supplier payment on payable posts PAYABLE_SETTLEMENT with DECREASE movement")
    void testRecordSettlement_SupplierPayablePayment() {
        UUID obId = UUID.randomUUID();
        UUID cpId = UUID.randomUUID();
        UUID accId = UUID.randomUUID();

        FinancialObligation obligation = new FinancialObligation(
                businessId, cpId, ObligationDirection.PAYABLE, ObligationSourceType.MANUAL_DECLARATION,
                null, "BILL-01", today.minusDays(5), today.plusDays(10), new BigDecimal("80000.00"), "PKR", "Materials", userId
        );
        obligation.setId(obId);

        FinancialAccount account = createAccount(businessId, "Main Bank", AccountType.BANK, "PKR", new BigDecimal("150000.00"), today.minusDays(30));
        account.setId(accId);

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obId, businessId)).thenReturn(Optional.of(obligation));
        when(allocationRepository.sumEffectiveAllocations(businessId, obId)).thenReturn(BigDecimal.ZERO);
        when(accountRepository.findForUpdateByIdAndBusinessId(accId, businessId)).thenReturn(Optional.of(account));
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accId)).thenReturn(List.of());

        when(transactionRepository.saveAndFlush(any(FinancialTransaction.class))).thenAnswer(inv -> {
            FinancialTransaction tx = inv.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        when(allocationRepository.saveAndFlush(any(ObligationPaymentAllocation.class))).thenAnswer(inv -> {
            ObligationPaymentAllocation a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        ObligationSettlementRequest req = new ObligationSettlementRequest(
                accId, "30000.00", today.toString(), "PAY-01", "Supplier payment"
        );

        ObligationSettlementResponse resp = service.recordSettlement(ownerContext, obId, "idemp-pay-1", req);

        assertEquals("30000.00", resp.amount());
        assertEquals("50000.00", resp.remainingOutstandingAmount());
        assertEquals("PARTIALLY_PAID", resp.settlementStatus());

        // Verify transaction is PAYABLE_SETTLEMENT, direction DECREASE
        verify(transactionRepository, times(1)).saveAndFlush(argThat(tx ->
                tx.getEventType() == TransactionEventType.PAYABLE_SETTLEMENT &&
                        tx.getMovementDirection() == MovementDirection.DECREASE &&
                        tx.getAmount().compareTo(new BigDecimal("30000.00")) == 0 &&
                        tx.getCounterpartyId().equals(cpId)
        ));

        // Verify account movement is DECREASE
        verify(movementRepository, times(1)).saveAndFlush(argThat(m ->
                m.getMovementDirection() == MovementDirection.DECREASE &&
                        m.getAmount().compareTo(new BigDecimal("30000.00")) == 0
        ));
    }

    @Test
    @DisplayName("Settlement: Cash account chronological deficit check triggers validation exception")
    void testRecordSettlement_CashAccountNegativeCheck() {
        UUID obId = UUID.randomUUID();
        UUID accId = UUID.randomUUID();

        FinancialObligation obligation = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.PAYABLE, ObligationSourceType.MANUAL_DECLARATION,
                null, "BILL-01", today.minusDays(5), today.plusDays(10), new BigDecimal("80000.00"), "PKR", "Materials", userId
        );
        obligation.setId(obId);

        // Cash account with only 10,000 opening balance
        FinancialAccount cashAccount = createAccount(businessId, "Office Cash", AccountType.CASH, "PKR", new BigDecimal("10000.00"), today.minusDays(30));
        cashAccount.setId(accId);

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obId, businessId)).thenReturn(Optional.of(obligation));
        when(allocationRepository.sumEffectiveAllocations(businessId, obId)).thenReturn(BigDecimal.ZERO);
        when(accountRepository.findForUpdateByIdAndBusinessId(accId, businessId)).thenReturn(Optional.of(cashAccount));
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accId)).thenReturn(List.of());

        // Paying 30,000 would cause negative cash (-20,000)
        ObligationSettlementRequest req = new ObligationSettlementRequest(
                accId, "30000.00", today.toString(), null, null
        );

        TransactionValidationException ex = assertThrows(TransactionValidationException.class,
                () -> service.recordSettlement(ownerContext, obId, "idemp-cash-neg", req));
        assertTrue(ex.getErrors().containsKey("cashBalance"));
    }

    @Test
    @DisplayName("Settlement idempotency: same key with same payload returns cached response")
    void testRecordSettlement_IdempotencyReplay() {
        UUID obId = UUID.randomUUID();
        UUID accId = UUID.randomUUID();

        FinancialObligation obligation = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                null, "INV-01", today, null, new BigDecimal("100000.00"), "PKR", null, userId
        );
        obligation.setId(obId);

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obId, businessId)).thenReturn(Optional.of(obligation));
        when(allocationRepository.sumEffectiveAllocations(businessId, obId)).thenReturn(BigDecimal.ZERO);

        String payloadHash = FinancialObligationService.computeSettlementPayloadHash(
                obId, accId, new BigDecimal("40000.00"), today, null, null
        );

        ObligationSettlementResponse cachedResponse = new ObligationSettlementResponse(
                UUID.randomUUID(), obId, UUID.randomUUID(), "40000.00", today, "90000.00", "60000.00", "PARTIALLY_PAID", Instant.now().toString()
        );
        String cachedJson = "";
        try {
            cachedJson = objectMapper.writeValueAsString(cachedResponse);
        } catch (Exception ignored) {}

        FinancialIdempotencyRecord existingRecord = new FinancialIdempotencyRecord(
                businessId, FinancialObligationService.OPERATION_SETTLEMENT, "idemp-replay", payloadHash, UUID.randomUUID(), 201, cachedJson
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, FinancialObligationService.OPERATION_SETTLEMENT, "idemp-replay"))
                .thenReturn(Optional.of(existingRecord));

        ObligationSettlementRequest req = new ObligationSettlementRequest(
                accId, "40000.00", today.toString(), null, null
        );

        ObligationSettlementResponse result = service.recordSettlement(ownerContext, obId, "idemp-replay", req);

        assertNotNull(result);
        assertEquals("40000.00", result.amount());
        assertEquals("60000.00", result.remainingOutstandingAmount());
        // Verify no additional transactions were saved
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Settlement idempotency: same key with different payload throws DuplicateResourceException conflict")
    void testRecordSettlement_IdempotencyConflict() {
        UUID obId = UUID.randomUUID();
        UUID accId = UUID.randomUUID();

        FinancialObligation obligation = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                null, "INV-01", today, null, new BigDecimal("100000.00"), "PKR", null, userId
        );
        obligation.setId(obId);

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obId, businessId)).thenReturn(Optional.of(obligation));
        when(allocationRepository.sumEffectiveAllocations(businessId, obId)).thenReturn(BigDecimal.ZERO);

        // Record with different hash
        FinancialIdempotencyRecord existingRecord = new FinancialIdempotencyRecord(
                businessId, FinancialObligationService.OPERATION_SETTLEMENT, "idemp-conflict", "different-hash", UUID.randomUUID(), 201, "{}"
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, FinancialObligationService.OPERATION_SETTLEMENT, "idemp-conflict"))
                .thenReturn(Optional.of(existingRecord));

        ObligationSettlementRequest req = new ObligationSettlementRequest(
                accId, "40000.00", today.toString(), null, null
        );

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> service.recordSettlement(ownerContext, obId, "idemp-conflict", req));
        assertTrue(ex.getMessage().contains("already been used with a different request payload"));
    }

    @Test
    @DisplayName("Get totals summary: calculates recorded, outstanding, overdue, and entity counts")
    void testGetTotalsSummary() {
        UUID cp1 = UUID.randomUUID();
        UUID cp2 = UUID.randomUUID();

        FinancialObligation o1 = new FinancialObligation(businessId, cp1, ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION, null, null, today.minusDays(10), today.minusDays(2), new BigDecimal("100000.00"), "PKR", null, userId);
        o1.setId(UUID.randomUUID());

        FinancialObligation o2 = new FinancialObligation(businessId, cp2, ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION, null, null, today.minusDays(5), today.plusDays(10), new BigDecimal("50000.00"), "PKR", null, userId);
        o2.setId(UUID.randomUUID());

        when(obligationRepository.findByBusinessId(businessId)).thenReturn(List.of(o1, o2));

        // o1 paid 25,000; outstanding 75,000 (overdue)
        ObligationPaymentAllocation a1 = new ObligationPaymentAllocation(businessId, o1.getId(), UUID.randomUUID(), new BigDecimal("25000.00"), today.minusDays(3), userId);
        when(allocationRepository.findByBusinessId(businessId)).thenReturn(List.of(a1));

        ObligationSummaryTotalsResponse summary = service.getTotalsSummary(ownerContext, today.toString());

        assertEquals("150000.00", summary.totalRecordedReceivables());
        assertEquals("125000.00", summary.outstandingReceivables());
        assertEquals("75000.00", summary.overdueReceivables()); // o1 only
        assertEquals("50000.00", summary.dueSoonReceivables()); // o2 only
        assertEquals(2, summary.customerCountWithBalance());
    }

    @Test
    @DisplayName("Get aging: integration with ObligationAgingCalculator returns all 7 standard buckets")
    void testGetAging_SevenBucketsReconciliation() {
        UUID cpId = UUID.randomUUID();

        FinancialObligation o1 = new FinancialObligation(businessId, cpId, ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION, null, null, today.minusDays(20), today.minusDays(10), new BigDecimal("10000.00"), "PKR", null, userId);
        o1.setId(UUID.randomUUID());

        when(obligationRepository.findByBusinessId(businessId)).thenReturn(List.of(o1));
        when(allocationRepository.findByBusinessId(businessId)).thenReturn(List.of());

        ObligationAgingResult agingResult = service.getAging(ownerContext, today.toString());

        assertNotNull(agingResult);
        assertEquals("PKR", agingResult.currency());
        assertEquals(new BigDecimal("10000.00"), agingResult.receivableTotalOutstanding());

        // Check 7 buckets presence
        assertEquals(7, agingResult.receivables().buckets().size());
        assertTrue(agingResult.receivables().buckets().containsKey(AgingBucket.UNKNOWN_DUE_DATE));
        assertTrue(agingResult.receivables().buckets().containsKey(AgingBucket.NOT_YET_DUE));
        assertTrue(agingResult.receivables().buckets().containsKey(AgingBucket.DUE_TODAY));
        assertTrue(agingResult.receivables().buckets().containsKey(AgingBucket.OVERDUE_1_30));
        assertTrue(agingResult.receivables().buckets().containsKey(AgingBucket.OVERDUE_31_60));
        assertTrue(agingResult.receivables().buckets().containsKey(AgingBucket.OVERDUE_61_90));
        assertTrue(agingResult.receivables().buckets().containsKey(AgingBucket.OVERDUE_91_PLUS));

        // 10 days overdue -> OVERDUE_1_30
        assertEquals(new BigDecimal("10000.00"), agingResult.receivables().buckets().get(AgingBucket.OVERDUE_1_30).totalOutstanding());
    }
}
