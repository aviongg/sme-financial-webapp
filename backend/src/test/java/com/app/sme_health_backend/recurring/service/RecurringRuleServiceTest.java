package com.app.sme_health_backend.recurring.service;

import com.app.sme_health_backend.accounting.draft.DraftStatus;
import com.app.sme_health_backend.accounting.draft.RecordDraft;
import com.app.sme_health_backend.accounting.draft.RecordDraftRepository;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyStatus;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.recurring.dto.*;
import com.app.sme_health_backend.recurring.entity.RecurringRule;
import com.app.sme_health_backend.recurring.entity.RecurringRuleOccurrence;
import com.app.sme_health_backend.recurring.exception.RecurringConflictException;
import com.app.sme_health_backend.recurring.exception.RecurringValidationException;
import com.app.sme_health_backend.recurring.model.RecurringFrequency;
import com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus;
import com.app.sme_health_backend.recurring.model.RecurringRuleStatus;
import com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository;
import com.app.sme_health_backend.recurring.repository.RecurringRuleRepository;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecurringRuleService Unit Tests")
class RecurringRuleServiceTest {

    @Mock
    private RecurringRuleRepository ruleRepository;

    @Mock
    private RecurringRuleOccurrenceRepository occurrenceRepository;

    @Mock
    private FinancialAccountRepository accountRepository;

    @Mock
    private CounterpartyRepository counterpartyRepository;

    @Mock
    private RecordDraftRepository draftRepository;

    @Mock
    private SecurityAuditRepository auditRepository;

    @InjectMocks
    private RecurringRuleService ruleService;

    private BusinessAccessContext context;
    private UUID businessId;
    private UUID userId;
    private UUID accountId;
    private UUID counterpartyId;
    private FinancialAccount activePkrAccount;
    private Counterparty activeSupplier;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        counterpartyId = UUID.randomUUID();
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);

        activePkrAccount = new FinancialAccount(
                businessId,
                "HBL Business",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("100000.00"),
                LocalDate.of(2025, 1, 1),
                AccountStatus.ACTIVE,
                "HBL",
                "123456",
                "Notes"
        );

        activeSupplier = new Counterparty(
                businessId,
                "Office Landlord",
                CounterpartyType.SUPPLIER,
                "landlord@example.com",
                "03001234567",
                "Commercial Rent",
                null,
                null
        );
    }

    @Test
    @DisplayName("Create rule succeeds with valid OPERATING_EXPENSE_PAYMENT and supplier")
    void testCreateRuleSuccess() {
        when(accountRepository.findByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(activePkrAccount));
        when(counterpartyRepository.findByIdAndBusinessId(counterpartyId, businessId)).thenReturn(Optional.of(activeSupplier));
        when(ruleRepository.saveAndFlush(any(RecurringRule.class))).thenAnswer(invocation -> {
            RecurringRule r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        RecurringRuleCreateRequest request = new RecurringRuleCreateRequest(
                "Office Rent",
                "Monthly office rental",
                "OPERATING_EXPENSE_PAYMENT",
                accountId,
                counterpartyId,
                "50000.00",
                "MONTHLY",
                "2026-11-01",
                null
        );

        RecurringRuleResponse response = ruleService.createRule(context, request);

        assertNotNull(response);
        assertEquals("Office Rent", response.name());
        assertEquals(RecurringRuleStatus.ACTIVE.name(), response.status());
        assertEquals("50000.00", response.amount());
        assertEquals("2026-11-01", response.nextDueDate());
        verify(ruleRepository).saveAndFlush(any(RecurringRule.class));
        verify(auditRepository).save(any());
    }

    @Test
    @DisplayName("Create rule rejects non-PKR account")
    void testCreateRuleRejectsNonPkrAccount() {
        FinancialAccount usdAccount = new FinancialAccount(
                businessId,
                "USD Account",
                AccountType.BANK,
                "USD",
                2,
                BigDecimal.ZERO,
                LocalDate.of(2025, 1, 1),
                AccountStatus.ACTIVE,
                "Bank",
                "987",
                null
        );
        when(accountRepository.findByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(usdAccount));

        RecurringRuleCreateRequest request = new RecurringRuleCreateRequest(
                "Rent",
                null,
                "OPERATING_EXPENSE_PAYMENT",
                accountId,
                null,
                "1000.00",
                "MONTHLY",
                "2026-11-01",
                null
        );

        RecurringValidationException ex = assertThrows(
                RecurringValidationException.class,
                () -> ruleService.createRule(context, request)
        );
        assertTrue(ex.getErrors().containsKey("accountId"));
        assertTrue(ex.getErrors().get("accountId").contains("PKR"));
    }

    @Test
    @DisplayName("Create rule rejects customer counterparty for OPERATING_EXPENSE_PAYMENT")
    void testCreateRuleRejectsCustomerForExpense() {
        Counterparty customer = new Counterparty(
                businessId,
                "Customer Ltd",
                CounterpartyType.CUSTOMER,
                null, null, null, null, null
        );
        when(accountRepository.findByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(activePkrAccount));
        when(counterpartyRepository.findByIdAndBusinessId(counterpartyId, businessId)).thenReturn(Optional.of(customer));

        RecurringRuleCreateRequest request = new RecurringRuleCreateRequest(
                "Rent",
                null,
                "OPERATING_EXPENSE_PAYMENT",
                accountId,
                counterpartyId,
                "50000.00",
                "MONTHLY",
                "2026-11-01",
                null
        );

        RecurringValidationException ex = assertThrows(
                RecurringValidationException.class,
                () -> ruleService.createRule(context, request)
        );
        assertTrue(ex.getErrors().containsKey("counterpartyId"));
    }

    @Test
    @DisplayName("Pause rule transitions ACTIVE to PAUSED")
    void testPauseRule() {
        UUID ruleId = UUID.randomUUID();
        RecurringRule rule = new RecurringRule(
                businessId,
                "Office Rent",
                null,
                TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                accountId,
                null,
                new BigDecimal("50000.00"),
                RecurringFrequency.MONTHLY,
                1,
                11,
                LocalDate.of(2026, 11, 1),
                null,
                LocalDate.of(2026, 11, 1),
                userId
        );
        rule.setId(ruleId);
        when(ruleRepository.findForUpdateByIdAndBusinessId(ruleId, businessId)).thenReturn(Optional.of(rule));
        when(ruleRepository.saveAndFlush(any(RecurringRule.class))).thenAnswer(i -> i.getArgument(0));

        RecurringRuleResponse response = ruleService.pauseRule(context, ruleId);

        assertEquals(RecurringRuleStatus.PAUSED.name(), response.status());
        verify(auditRepository).save(any());
    }

    @Test
    @DisplayName("Archive rule transitions to ARCHIVED and clears nextDueDate")
    void testArchiveRule() {
        UUID ruleId = UUID.randomUUID();
        RecurringRule rule = new RecurringRule(
                businessId,
                "Office Rent",
                null,
                TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                accountId,
                null,
                new BigDecimal("50000.00"),
                RecurringFrequency.MONTHLY,
                1,
                11,
                LocalDate.of(2026, 11, 1),
                null,
                LocalDate.of(2026, 11, 1),
                userId
        );
        rule.setId(ruleId);
        when(ruleRepository.findForUpdateByIdAndBusinessId(ruleId, businessId)).thenReturn(Optional.of(rule));
        when(ruleRepository.saveAndFlush(any(RecurringRule.class))).thenAnswer(i -> i.getArgument(0));

        RecurringRuleResponse response = ruleService.archiveRule(context, ruleId);

        assertEquals(RecurringRuleStatus.ARCHIVED.name(), response.status());
        assertNull(response.nextDueDate());
    }

    @Test
    @DisplayName("Update rule checks optimistic concurrency version mismatch")
    void testUpdateRuleVersionMismatch() {
        UUID ruleId = UUID.randomUUID();
        RecurringRule rule = new RecurringRule(
                businessId,
                "Office Rent",
                null,
                TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                accountId,
                null,
                new BigDecimal("50000.00"),
                RecurringFrequency.MONTHLY,
                1,
                11,
                LocalDate.of(2026, 11, 1),
                null,
                LocalDate.of(2026, 11, 1),
                userId
        );
        rule.setId(ruleId);
        rule.setVersion(1L);
        when(ruleRepository.findForUpdateByIdAndBusinessId(ruleId, businessId)).thenReturn(Optional.of(rule));

        RecurringRuleUpdateRequest request = new RecurringRuleUpdateRequest(
                "Office Rent Updated",
                null,
                "OPERATING_EXPENSE_PAYMENT",
                accountId,
                null,
                "55000.00",
                "MONTHLY",
                null,
                99L // Wrong version (expected 1L)
        );

        assertThrows(
                RecurringConflictException.class,
                () -> ruleService.updateRule(context, ruleId, request)
        );
    }

    @Test
    @DisplayName("Generate due occurrences creates RecordDraft and occurrence in DRAFT_READY status")
    void testGenerateDueOccurrencesCreatesDraft() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Karachi"));
        UUID ruleId = UUID.randomUUID();
        RecurringRule rule = new RecurringRule(
                businessId,
                "Internet Subscription",
                "PTCL Fiber",
                TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                accountId,
                null,
                new BigDecimal("5000.00"),
                RecurringFrequency.MONTHLY,
                today.minusDays(2).getDayOfMonth(),
                today.minusDays(2).getMonthValue(),
                today.minusDays(2),
                null,
                today.minusDays(2),
                userId
        );
        rule.setId(ruleId);
        when(ruleRepository.findByBusinessIdAndStatusAndNextDueDateLessThanEqual(eq(businessId), eq(RecurringRuleStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(rule));
        when(ruleRepository.findForUpdateByIdAndBusinessId(rule.getId(), businessId)).thenReturn(Optional.of(rule));
        when(occurrenceRepository.findByBusinessIdAndRecurringRuleIdAndScheduledDate(any(), any(), any()))
                .thenReturn(Optional.empty());

        when(draftRepository.saveAndFlush(any(RecordDraft.class))).thenAnswer(invocation -> {
            RecordDraft d = invocation.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(occurrenceRepository.saveAndFlush(any(RecurringRuleOccurrence.class))).thenAnswer(i -> {
            RecurringRuleOccurrence o = i.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });
        when(ruleRepository.saveAndFlush(any(RecurringRule.class))).thenAnswer(i -> i.getArgument(0));

        OccurrenceGenerateDueResponse response = ruleService.generateDueOccurrencesForBusiness(context);

        assertEquals(1, response.generatedCount());
        verify(draftRepository).saveAndFlush(any(RecordDraft.class));
        verify(occurrenceRepository).saveAndFlush(any(RecurringRuleOccurrence.class));
    }

    @Test
    @DisplayName("Generate due occurrences skips already existing scheduled date occurrences (idempotency)")
    void testGenerateDueOccurrencesIdempotentSkip() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Karachi"));
        LocalDate pastDue = today.minusDays(2);
        UUID ruleId = UUID.randomUUID();
        RecurringRule rule = new RecurringRule(
                businessId,
                "Internet Subscription",
                null,
                TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                accountId,
                null,
                new BigDecimal("5000.00"),
                RecurringFrequency.MONTHLY,
                pastDue.getDayOfMonth(),
                pastDue.getMonthValue(),
                pastDue,
                null,
                pastDue,
                userId
        );
        rule.setId(ruleId);
        when(ruleRepository.findByBusinessIdAndStatusAndNextDueDateLessThanEqual(eq(businessId), eq(RecurringRuleStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(rule));
        when(ruleRepository.findForUpdateByIdAndBusinessId(rule.getId(), businessId)).thenReturn(Optional.of(rule));
        when(ruleRepository.saveAndFlush(any(RecurringRule.class))).thenAnswer(i -> i.getArgument(0));

        RecurringRuleOccurrence existingOcc = new RecurringRuleOccurrence(
                businessId,
                rule.getId(),
                1L,
                pastDue,
                new BigDecimal("5000.00"),
                TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                accountId,
                null,
                null,
                RecurringOccurrenceStatus.DRAFT_READY,
                userId
        );
        existingOcc.setId(UUID.randomUUID());
        when(occurrenceRepository.findByBusinessIdAndRecurringRuleIdAndScheduledDate(businessId, rule.getId(), pastDue))
                .thenReturn(Optional.of(existingOcc));

        OccurrenceGenerateDueResponse response = ruleService.generateDueOccurrencesForBusiness(context);

        assertEquals(0, response.generatedCount());
        verify(draftRepository, never()).saveAndFlush(any(RecordDraft.class));
    }

    @Test
    @DisplayName("Skip occurrence discards unposted linked draft and transitions to SKIPPED")
    void testSkipOccurrenceDiscardsDraft() {
        UUID occId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();

        RecurringRuleOccurrence occ = new RecurringRuleOccurrence(
                businessId,
                UUID.randomUUID(),
                1L,
                LocalDate.of(2026, 11, 1),
                new BigDecimal("10000.00"),
                TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                accountId,
                null,
                "Maintenance",
                RecurringOccurrenceStatus.DRAFT_READY,
                userId
        );
        occ.setId(occId);
        occ.setDraftId(draftId);

        when(occurrenceRepository.findForUpdateByIdAndBusinessId(occId, businessId)).thenReturn(Optional.of(occ));
        when(occurrenceRepository.saveAndFlush(any(RecurringRuleOccurrence.class))).thenAnswer(i -> i.getArgument(0));

        RecordDraft draft = new RecordDraft(
                businessId,
                accountId,
                TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                LocalDate.of(2026, 11, 1),
                "10000.00",
                "Maintenance",
                userId
        );
        draft.setId(draftId);
        draft.setStatus(DraftStatus.DRAFT);
        when(draftRepository.findByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));

        RecurringOccurrenceResponse response = ruleService.skipOccurrence(
                context,
                occId,
                new OccurrenceSkipRequest("Tenant requested skip")
        );

        assertEquals(RecurringOccurrenceStatus.SKIPPED.name(), response.status());
        assertNotNull(response.skippedAt());
        assertEquals("Tenant requested skip", response.skippedReason());
        assertEquals(DraftStatus.DISCARDED, draft.getStatus());
        verify(draftRepository).saveAndFlush(draft);
    }

    @Test
    @DisplayName("VIEWER role cannot modify recurring rules")
    void testViewerRoleDeniedWrite() {
        BusinessAccessContext viewerContext = new BusinessAccessContext(userId, businessId, MembershipRole.VIEWER);

        RecurringRuleCreateRequest request = new RecurringRuleCreateRequest(
                "Rent",
                null,
                "OPERATING_EXPENSE_PAYMENT",
                accountId,
                null,
                "50000.00",
                "MONTHLY",
                "2026-11-01",
                null
        );

        assertThrows(
                AccessDeniedException.class,
                () -> ruleService.createRule(viewerContext, request)
        );
    }
}
