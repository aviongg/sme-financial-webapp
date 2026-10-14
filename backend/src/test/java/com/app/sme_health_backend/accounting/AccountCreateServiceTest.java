package com.app.sme_health_backend.accounting;

import com.app.sme_health_backend.accounting.dto.AccountCreateRequest;
import com.app.sme_health_backend.accounting.dto.AccountDetailResponse;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.exception.AccountValidationException;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.BalanceStatus;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.accounting.service.AccountCreateService;
import com.app.sme_health_backend.accounting.service.AccountReadService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountCreateServiceTest {

    @Mock
    private FinancialAccountRepository accountRepository;

    @Mock
    private FinancialIdempotencyRepository idempotencyRepository;

    private AccountReadService accountReadService;
    private ObjectMapper objectMapper;
    private AccountCreateService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID businessId = UUID.randomUUID();
    private BusinessAccessContext context;

    @BeforeEach
    void setUp() {
        accountReadService = new AccountReadService(accountRepository);
        objectMapper = new ObjectMapper();
        service = new AccountCreateService(accountRepository, idempotencyRepository, accountReadService, objectMapper);
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
    }

    @Test
    @DisplayName("Create valid BANK account with exact decimals and default Karachi date")
    void createValidBankAccount_defaultsDate() {
        AccountCreateRequest request = new AccountCreateRequest(
                "Meezan Operating",
                "BANK",
                "PKR",
                "150000.50",
                null,
                "Meezan Bank Ltd",
                "Main payroll account"
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), eq("key-1")))
                .thenReturn(Optional.empty());
        when(accountRepository.existsByBusinessIdAndAccountName(businessId, "Meezan Operating"))
                .thenReturn(false);
        when(accountRepository.saveAndFlush(any())).thenAnswer(inv -> {
            FinancialAccount fa = inv.getArgument(0);
            fa.setId(UUID.randomUUID());
            return fa;
        });

        AccountDetailResponse response = service.createAccount(context, "key-1", request);

        assertThat(response).isNotNull();
        assertThat(response.accountName()).isEqualTo("Meezan Operating");
        assertThat(response.accountType()).isEqualTo(AccountType.BANK);
        assertThat(response.currency()).isEqualTo("PKR");
        assertThat(response.currencyDecimals()).isEqualTo(2);
        assertThat(response.openingBalance()).isEqualTo("150000.50");
        assertThat(response.currentBalance()).isEqualTo("150000.50");
        assertThat(response.balanceStatus()).isEqualTo(BalanceStatus.AVAILABLE);
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.openingDate()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Karachi")));
        assertThat(response.institutionName()).isEqualTo("Meezan Bank Ltd");
        assertThat(response.accountIdentifier()).isNull();
        assertThat(response.notes()).isEqualTo("Main payroll account");

        // Verify account persisted with correct scale
        ArgumentCaptor<FinancialAccount> accountCaptor = ArgumentCaptor.forClass(FinancialAccount.class);
        verify(accountRepository).saveAndFlush(accountCaptor.capture());
        FinancialAccount saved = accountCaptor.getValue();
        assertThat(saved.getOpeningBalance()).isEqualByComparingTo("150000.50");
        assertThat(saved.getCurrencyDecimals()).isEqualTo(2);

        // Verify idempotency record saved
        ArgumentCaptor<FinancialIdempotencyRecord> idempCaptor = ArgumentCaptor.forClass(FinancialIdempotencyRecord.class);
        verify(idempotencyRepository).saveAndFlush(idempCaptor.capture());
        FinancialIdempotencyRecord savedIdemp = idempCaptor.getValue();
        assertThat(savedIdemp.getBusinessId()).isEqualTo(businessId);
        assertThat(savedIdemp.getOperation()).isEqualTo("ACCOUNT_CREATE");
        assertThat(savedIdemp.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(savedIdemp.getResponseStatus()).isEqualTo(201);
        assertThat(savedIdemp.getResponseBody()).contains("Meezan Operating");
    }

    @Test
    @DisplayName("Create valid CASH account with zero balance and no institution")
    void createValidCashAccount() {
        AccountCreateRequest request = new AccountCreateRequest(
                "Front Desk Cash Drawer",
                "CASH",
                "PKR",
                "0.00",
                "2026-10-01",
                null,
                null
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), eq("cash-key")))
                .thenReturn(Optional.empty());
        when(accountRepository.existsByBusinessIdAndAccountName(businessId, "Front Desk Cash Drawer"))
                .thenReturn(false);
        when(accountRepository.saveAndFlush(any())).thenAnswer(inv -> {
            FinancialAccount fa = inv.getArgument(0);
            fa.setId(UUID.randomUUID());
            return fa;
        });

        AccountDetailResponse response = service.createAccount(context, "cash-key", request);

        assertThat(response.accountType()).isEqualTo(AccountType.CASH);
        assertThat(response.openingBalance()).isEqualTo("0.00");
        assertThat(response.currentBalance()).isEqualTo("0.00");
        assertThat(response.institutionName()).isNull();
    }

    @Test
    @DisplayName("Create signed negative opening balance for BANK overdraft")
    void createSignedNegativeBalanceForBank() {
        AccountCreateRequest request = new AccountCreateRequest(
                "Credit Line Overdraft",
                "BANK",
                "USD",
                "-25000.00",
                "2026-10-10",
                "Citibank",
                "Authorized revolving credit facility"
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), eq("od-key")))
                .thenReturn(Optional.empty());
        when(accountRepository.existsByBusinessIdAndAccountName(businessId, "Credit Line Overdraft"))
                .thenReturn(false);
        when(accountRepository.saveAndFlush(any())).thenAnswer(inv -> {
            FinancialAccount fa = inv.getArgument(0);
            fa.setId(UUID.randomUUID());
            return fa;
        });

        AccountDetailResponse response = service.createAccount(context, "od-key", request);

        assertThat(response.openingBalance()).isEqualTo("-25000.00");
        assertThat(response.currentBalance()).isEqualTo("-25000.00");
        assertThat(response.balanceStatus()).isEqualTo(BalanceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Future-dated account has planned openingBalance but canonical zero currentBalance")
    void futureDatedAccountHasZeroCurrentBalance() {
        LocalDate futureDate = LocalDate.now(ZoneId.of("Asia/Karachi")).plusDays(10);
        AccountCreateRequest request = new AccountCreateRequest(
                "Future Expansion Account",
                "BANK",
                "PKR",
                "500000.00",
                futureDate.toString(),
                "Standard Chartered",
                null
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), eq("future-key")))
                .thenReturn(Optional.empty());
        when(accountRepository.existsByBusinessIdAndAccountName(businessId, "Future Expansion Account"))
                .thenReturn(false);
        when(accountRepository.saveAndFlush(any())).thenAnswer(inv -> {
            FinancialAccount fa = inv.getArgument(0);
            fa.setId(UUID.randomUUID());
            return fa;
        });

        AccountDetailResponse response = service.createAccount(context, "future-key", request);

        assertThat(response.openingBalance()).isEqualTo("500000.00");
        assertThat(response.currentBalance()).isEqualTo("0.00");
        assertThat(response.balanceStatus()).isEqualTo(BalanceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("CASH account with negative balance throws AccountValidationException")
    void cashAccountNegativeBalanceThrows() {
        AccountCreateRequest request = new AccountCreateRequest(
                "Petty Cash",
                "CASH",
                "PKR",
                "-500.00",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.createAccount(context, "key-cash-neg", request))
                .isInstanceOf(AccountValidationException.class)
                .satisfies(ex -> {
                    AccountValidationException ave = (AccountValidationException) ex;
                    assertThat(ave.getErrors()).containsKey("openingBalance");
                    assertThat(ave.getErrors().get("openingBalance")).contains("Cash account opening balance cannot be negative");
                });
    }

    @Test
    @DisplayName("CASH account with institutionName throws AccountValidationException")
    void cashAccountWithInstitutionThrows() {
        AccountCreateRequest request = new AccountCreateRequest(
                "Petty Cash",
                "CASH",
                "PKR",
                "1000.00",
                null,
                "Bank of Punjab",
                null
        );

        assertThatThrownBy(() -> service.createAccount(context, "key-cash-inst", request))
                .isInstanceOf(AccountValidationException.class)
                .satisfies(ex -> {
                    AccountValidationException ave = (AccountValidationException) ex;
                    assertThat(ave.getErrors()).containsKey("institutionName");
                    assertThat(ave.getErrors().get("institutionName")).contains("Institution name is not permitted for CASH accounts");
                });
    }

    @Test
    @DisplayName("Precision overflow in openingBalance throws AccountValidationException")
    void precisionOverflowThrows() {
        AccountCreateRequest request = new AccountCreateRequest(
                "PKR Account",
                "BANK",
                "PKR",
                "100.123", // PKR only allows 2 decimals
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.createAccount(context, "key-prec", request))
                .isInstanceOf(AccountValidationException.class)
                .satisfies(ex -> {
                    AccountValidationException ave = (AccountValidationException) ex;
                    assertThat(ave.getErrors()).containsKey("openingBalance");
                    assertThat(ave.getErrors().get("openingBalance")).contains("Opening balance exceeds currency decimal scale of 2");
                });
    }

    @Test
    @DisplayName("Capacity overflow throws AccountValidationException")
    void capacityOverflowThrows() {
        AccountCreateRequest request = new AccountCreateRequest(
                "Huge Account",
                "BANK",
                "PKR",
                "1000000000000.00", // 10^12
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.createAccount(context, "key-cap", request))
                .isInstanceOf(AccountValidationException.class)
                .satisfies(ex -> {
                    AccountValidationException ave = (AccountValidationException) ex;
                    assertThat(ave.getErrors()).containsKey("openingBalance");
                    assertThat(ave.getErrors().get("openingBalance")).contains("Opening balance exceeds maximum capacity");
                });
    }

    @Test
    @DisplayName("Unsupported currency throws AccountValidationException")
    void unsupportedCurrencyThrows() {
        AccountCreateRequest request = new AccountCreateRequest(
                "Crypto Account",
                "WALLET",
                "BTC",
                "1.00",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.createAccount(context, "key-curr", request))
                .isInstanceOf(AccountValidationException.class)
                .satisfies(ex -> {
                    AccountValidationException ave = (AccountValidationException) ex;
                    assertThat(ave.getErrors()).containsKey("currency");
                    assertThat(ave.getErrors().get("currency")).contains("Unsupported currency: BTC");
                });
    }

    @Test
    @DisplayName("Missing or blank Idempotency-Key throws AccountValidationException")
    void missingIdempotencyKeyThrows() {
        AccountCreateRequest request = new AccountCreateRequest(
                "Test Account",
                "BANK",
                "PKR",
                "1000.00",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.createAccount(context, null, request))
                .isInstanceOf(AccountValidationException.class)
                .satisfies(ex -> assertThat(((AccountValidationException) ex).getErrors()).containsKey("Idempotency-Key"));

        assertThatThrownBy(() -> service.createAccount(context, "   ", request))
                .isInstanceOf(AccountValidationException.class)
                .satisfies(ex -> assertThat(((AccountValidationException) ex).getErrors()).containsKey("Idempotency-Key"));
    }

    @Test
    @DisplayName("Duplicate account name in business throws DuplicateResourceException (409)")
    void duplicateAccountNameThrows() {
        AccountCreateRequest request = new AccountCreateRequest(
                "Existing Operations Account",
                "BANK",
                "PKR",
                "1000.00",
                null,
                null,
                null
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), eq("new-key")))
                .thenReturn(Optional.empty());
        when(accountRepository.existsByBusinessIdAndAccountName(businessId, "Existing Operations Account"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createAccount(context, "new-key", request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("An account with name 'Existing Operations Account' already exists for this business");

        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Identical idempotent replay returns stored response without creating duplicate")
    void identicalIdempotentReplayReturnsStoredResponse() throws Exception {
        AccountCreateRequest request = new AccountCreateRequest(
                "Meezan Operating",
                "BANK",
                "PKR",
                "150000.50",
                "2026-10-14",
                "Meezan Bank Ltd",
                "Main payroll account"
        );

        String payloadHash = AccountCreateService.computePayloadHash(
                "Meezan Operating",
                AccountType.BANK,
                "PKR",
                new BigDecimal("150000.50"),
                2,
                LocalDate.of(2026, 10, 14),
                "Meezan Bank Ltd",
                "Main payroll account"
        );

        UUID accountId = UUID.randomUUID();
        AccountDetailResponse originalResponse = new AccountDetailResponse(
                accountId,
                "Meezan Operating",
                AccountType.BANK,
                "PKR",
                2,
                "150000.50",
                LocalDate.of(2026, 10, 14),
                "150000.50",
                BalanceStatus.AVAILABLE,
                null,
                AccountStatus.ACTIVE,
                "2026-10-14T10:00:00Z",
                LocalDate.of(2026, 10, 14),
                "Meezan Bank Ltd",
                null,
                "Main payroll account",
                "2026-10-14T10:00:00Z",
                "2026-10-14T10:00:00Z"
        );
        String responseJson = objectMapper.writeValueAsString(originalResponse);

        FinancialIdempotencyRecord record = new FinancialIdempotencyRecord(
                businessId,
                "ACCOUNT_CREATE",
                "idemp-same",
                payloadHash,
                accountId,
                201,
                responseJson
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, "ACCOUNT_CREATE", "idemp-same"))
                .thenReturn(Optional.of(record));

        AccountDetailResponse result = service.createAccount(context, "idemp-same", request);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(accountId);
        assertThat(result.accountName()).isEqualTo("Meezan Operating");

        // Verify NO new account or idempotency record was created
        verify(accountRepository, never()).saveAndFlush(any());
        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Same Idempotency-Key with conflicting payload throws DuplicateResourceException (409)")
    void sameIdempotencyKeyDifferentPayloadThrowsConflict() {
        AccountCreateRequest request = new AccountCreateRequest(
                "Different Account Name",
                "BANK",
                "PKR",
                "200000.00",
                "2026-10-14",
                null,
                null
        );

        FinancialIdempotencyRecord record = new FinancialIdempotencyRecord(
                businessId,
                "ACCOUNT_CREATE",
                "idemp-conflict",
                "some-different-hash-1234567890",
                UUID.randomUUID(),
                201,
                "{}"
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, "ACCOUNT_CREATE", "idemp-conflict"))
                .thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.createAccount(context, "idemp-conflict", request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Idempotency key 'idemp-conflict' has already been used with a different request payload");

        verify(accountRepository, never()).saveAndFlush(any());
    }
}
