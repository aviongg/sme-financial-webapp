package com.app.sme_health_backend.reconciliation.service;

import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.reconciliation.dto.StatementEntryItemRequest;
import com.app.sme_health_backend.reconciliation.dto.StatementImportRequest;
import com.app.sme_health_backend.reconciliation.dto.StatementImportResponse;
import com.app.sme_health_backend.reconciliation.entity.BankStatementImport;
import com.app.sme_health_backend.reconciliation.exception.ReconciliationConflictException;
import com.app.sme_health_backend.reconciliation.exception.ReconciliationValidationException;
import com.app.sme_health_backend.reconciliation.repository.BankStatementEntryRepository;
import com.app.sme_health_backend.reconciliation.repository.BankStatementImportRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankStatementImportServiceTest {

    @Mock
    private BankStatementImportRepository importRepository;

    @Mock
    private BankStatementEntryRepository entryRepository;

    @Mock
    private FinancialAccountRepository accountRepository;

    @Mock
    private FinancialIdempotencyRepository idempotencyRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private BankStatementImportService importService;

    private BusinessAccessContext context;
    private UUID businessId;
    private UUID accountId;
    private FinancialAccount bankAccount;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        context = new BusinessAccessContext(UUID.randomUUID(), businessId, MembershipRole.OWNER);

        bankAccount = new FinancialAccount(
                businessId,
                "HBL Main Account",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("500000.00"),
                LocalDate.of(2026, 1, 1),
                AccountStatus.ACTIVE,
                null,
                null,
                "Operating bank account"
        );
        bankAccount.setId(accountId);
    }

    @Test
    @DisplayName("Import statement persists import and entries and computes duplicate warnings")
    void testImportStatementSuccess() {
        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(bankAccount));
        when(importRepository.save(any(BankStatementImport.class))).thenAnswer(i -> {
            BankStatementImport imp = i.getArgument(0);
            imp.setId(UUID.randomUUID());
            return imp;
        });

        List<StatementEntryItemRequest> items = List.of(
                new StatementEntryItemRequest(
                        1,
                        LocalDate.of(2026, 10, 1),
                        null,
                        MovementDirection.DECREASE,
                        new BigDecimal("5000.00"),
                        "Office Rent",
                        "REF-101",
                        null,
                        new BigDecimal("495000.00")
                ),
                new StatementEntryItemRequest(
                        2,
                        LocalDate.of(2026, 10, 2),
                        null,
                        MovementDirection.INCREASE,
                        new BigDecimal("25000.00"),
                        "Customer Deposit",
                        "DEP-202",
                        null,
                        new BigDecimal("520000.00")
                )
        );

        StatementImportRequest request = new StatementImportRequest(
                accountId,
                "october_hbl.csv",
                null,
                "{\"dateCol\":0,\"descCol\":1}",
                new BigDecimal("500000.00"),
                new BigDecimal("520000.00"),
                items
        );

        StatementImportResponse response = importService.importStatement(context, "idemp-001", request);

        assertNotNull(response);
        assertEquals(accountId, response.accountId());
        assertEquals("october_hbl.csv", response.sourceFilename());
        assertEquals(2, response.totalEntries());
        assertEquals(new BigDecimal("500000.00"), response.openingBalance());
        assertEquals(new BigDecimal("520000.00"), response.closingBalance());

        verify(importRepository).save(any(BankStatementImport.class));
        verify(entryRepository).saveAll(any());
        verify(idempotencyRepository).save(any());
    }

    @Test
    @DisplayName("Import statement rejects non-bank account")
    void testImportRejectsNonBankAccount() {
        FinancialAccount cashAccount = new FinancialAccount(
                businessId, "Cash Till", AccountType.CASH, "PKR", 2,
                BigDecimal.ZERO, LocalDate.of(2026, 1, 1), AccountStatus.ACTIVE, null, null, null
        );
        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(cashAccount));

        StatementImportRequest request = new StatementImportRequest(
                accountId, "test.csv", null, null, null, null,
                List.of(new StatementEntryItemRequest(1, LocalDate.now(), null, MovementDirection.INCREASE, new BigDecimal("100.00"), "test", null, null, null))
        );

        assertThrows(ReconciliationValidationException.class, () ->
                importService.importStatement(context, null, request)
        );
    }

    @Test
    @DisplayName("Import statement rejects inactive account")
    void testImportRejectsInactiveAccount() {
        bankAccount.setStatus(AccountStatus.ARCHIVED);
        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(bankAccount));

        StatementImportRequest request = new StatementImportRequest(
                accountId, "test.csv", null, null, null, null,
                List.of(new StatementEntryItemRequest(1, LocalDate.now(), null, MovementDirection.INCREASE, new BigDecimal("100.00"), "test", null, null, null))
        );

        assertThrows(ReconciliationValidationException.class, () ->
                importService.importStatement(context, null, request)
        );
    }

    @Test
    @DisplayName("Import statement rejects empty entries")
    void testImportRejectsEmptyEntries() {
        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(bankAccount));

        StatementImportRequest request = new StatementImportRequest(
                accountId, "test.csv", null, null, null, null, Collections.emptyList()
        );

        assertThrows(ReconciliationValidationException.class, () ->
                importService.importStatement(context, null, request)
        );
    }

    @Test
    @DisplayName("Import statement replay with same idempotency key returns cached response")
    void testImportReplayIdempotency() throws Exception {
        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(bankAccount));

        List<StatementEntryItemRequest> items = List.of(
                new StatementEntryItemRequest(1, LocalDate.now(), null, MovementDirection.INCREASE, new BigDecimal("1000.00"), "test", null, null, null)
        );
        StatementImportRequest request = new StatementImportRequest(
                accountId, "test.csv", null, null, null, null, items
        );

        // Compute expected request hash
        String requestHash = importService.computeRequestHash(request);
        StatementImportResponse cachedResponse = new StatementImportResponse(
                UUID.randomUUID(), accountId, "test.csv", 1, null, null, LocalDate.now(), LocalDate.now(),
                com.app.sme_health_backend.reconciliation.model.StatementImportStatus.ACTIVE, 0, java.time.Instant.now()
        );
        String cachedJson = objectMapper.writeValueAsString(cachedResponse);

        FinancialIdempotencyRecord record = new FinancialIdempotencyRecord(
                businessId, "BANK_STATEMENT_IMPORT", "key-123", requestHash, cachedResponse.id(), 201, cachedJson
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                businessId, "BANK_STATEMENT_IMPORT", "key-123"
        )).thenReturn(Optional.of(record));

        StatementImportResponse result = importService.importStatement(context, "key-123", request);

        assertNotNull(result);
        assertEquals(cachedResponse.id(), result.id());
        verify(importRepository, never()).save(any());
    }

    @Test
    @DisplayName("Import statement replay with changed payload throws ReconciliationConflictException")
    void testImportReplayPayloadConflict() {
        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(bankAccount));

        List<StatementEntryItemRequest> items = List.of(
                new StatementEntryItemRequest(1, LocalDate.now(), null, MovementDirection.INCREASE, new BigDecimal("1000.00"), "test", null, null, null)
        );
        StatementImportRequest request = new StatementImportRequest(
                accountId, "test.csv", null, null, null, null, items
        );

        FinancialIdempotencyRecord record = new FinancialIdempotencyRecord(
                businessId, "BANK_STATEMENT_IMPORT", "key-123", "DIFFERENT_HASH", UUID.randomUUID(), 201, "{}"
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                businessId, "BANK_STATEMENT_IMPORT", "key-123"
        )).thenReturn(Optional.of(record));

        assertThrows(ReconciliationConflictException.class, () ->
                importService.importStatement(context, "key-123", request)
        );
    }
}
