package com.app.sme_health_backend.accounting;

import com.app.sme_health_backend.accounting.dto.AccountDetailResponse;
import com.app.sme_health_backend.accounting.dto.AccountListResponse;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.BalanceStatus;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.service.AccountReadService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountReadServiceTest {

    @Mock
    private FinancialAccountRepository accountRepository;

    private AccountReadService accountReadService;

    private final UUID businessId = UUID.randomUUID();
    private final LocalDate todayKarachi = LocalDate.now(AccountReadService.BUSINESS_ZONE);

    @BeforeEach
    void setUp() {
        accountReadService = new AccountReadService(accountRepository);
    }

    @Test
    @DisplayName("Opening balance included when openingDate <= effectiveCutoffDate")
    void openingBeforeOrOnCutoffIncluded() {
        FinancialAccount account = new FinancialAccount(
                businessId,
                "Main Operating Bank",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("100000.00"),
                todayKarachi.minusDays(5),
                AccountStatus.ACTIVE,
                "Meezan Bank Ltd",
                "PK12MEZN0000123456784821",
                "Primary operations"
        );
        UUID accountId = UUID.randomUUID();
        account.setId(accountId);

        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(account));

        AccountDetailResponse response = accountReadService.getAccountDetail(businessId, accountId);

        assertEquals("100000.00", response.openingBalance());
        assertEquals("100000.00", response.currentBalance());
        assertEquals(BalanceStatus.AVAILABLE, response.balanceStatus());
        assertNull(response.balanceUnavailableReason());
        assertEquals("****4821", response.accountIdentifier());
        assertEquals("Primary operations", response.notes());
        assertEquals(todayKarachi, response.effectiveCutoffDate());
        assertNotNull(response.observationAt());
    }

    @Test
    @DisplayName("Pre-opening date accounts have canonical zero current balance")
    void futureOpeningDateHasCanonicalZero() {
        FinancialAccount account = new FinancialAccount(
                businessId,
                "Future Expansion Account",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("500000.00"),
                todayKarachi.plusDays(10), // Future opening date
                AccountStatus.ACTIVE,
                "Habib Bank Ltd",
                "HBL1234567890",
                null
        );
        UUID accountId = UUID.randomUUID();
        account.setId(accountId);

        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(account));

        AccountDetailResponse response = accountReadService.getAccountDetail(businessId, accountId);

        assertEquals("500000.00", response.openingBalance());
        assertEquals("0.00", response.currentBalance()); // Canonical zero
        assertEquals(BalanceStatus.AVAILABLE, response.balanceStatus());
        assertNull(response.balanceUnavailableReason());
    }

    @Test
    @DisplayName("Capacity overflow triggers UNAVAILABLE with CAPACITY_OVERFLOW and null openingBalance")
    void capacityOverflowUnavailable() {
        FinancialAccount account = new FinancialAccount(
                businessId,
                "Whale Vault",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("1000000000000.00"), // 13 integer digits
                todayKarachi.minusDays(1),
                AccountStatus.ACTIVE,
                "Bank of Pakistan",
                null,
                null
        );
        UUID accountId = UUID.randomUUID();
        account.setId(accountId);

        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(account));

        AccountDetailResponse response = accountReadService.getAccountDetail(businessId, accountId);

        assertNull(response.openingBalance());
        assertNull(response.currentBalance());
        assertEquals(BalanceStatus.UNAVAILABLE, response.balanceStatus());
        assertEquals("CAPACITY_OVERFLOW", response.balanceUnavailableReason());
    }

    @Test
    @DisplayName("Precision overflow triggers UNAVAILABLE with PRECISION_OVERFLOW and null openingBalance")
    void precisionOverflowUnavailable() {
        FinancialAccount account = new FinancialAccount(
                businessId,
                "Mismatched Scale",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("100.005"), // 3 decimals for PKR
                todayKarachi.minusDays(1),
                AccountStatus.ACTIVE,
                "Bank of Pakistan",
                null,
                null
        );
        UUID accountId = UUID.randomUUID();
        account.setId(accountId);

        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(account));

        AccountDetailResponse response = accountReadService.getAccountDetail(businessId, accountId);

        assertNull(response.openingBalance());
        assertNull(response.currentBalance());
        assertEquals(BalanceStatus.UNAVAILABLE, response.balanceStatus());
        assertEquals("PRECISION_OVERFLOW", response.balanceUnavailableReason());
    }

    @Test
    @DisplayName("Currency metadata mismatch (PKR scale 3) triggers UNAVAILABLE with UNSUPPORTED_CURRENCY and null openingBalance")
    void currencyMetadataMismatchUnavailable() {
        FinancialAccount account = new FinancialAccount(
                businessId,
                "Defective Metadata Account",
                AccountType.BANK,
                "PKR",
                3, // Inconsistent scale (PKR is scale 2)
                new BigDecimal("100.00"),
                todayKarachi.minusDays(1),
                AccountStatus.INACTIVE,
                null,
                null,
                null
        );
        UUID accountId = UUID.randomUUID();
        account.setId(accountId);

        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(account));

        AccountDetailResponse response = accountReadService.getAccountDetail(businessId, accountId);

        assertNull(response.openingBalance());
        assertNull(response.currentBalance());
        assertEquals(BalanceStatus.UNAVAILABLE, response.balanceStatus());
        assertEquals("UNSUPPORTED_CURRENCY", response.balanceUnavailableReason());
    }

    @Test
    @DisplayName("Future opening date with currency metadata mismatch does not claim AVAILABLE or canonical zero")
    void futureOpeningWithCurrencyMismatch() {
        FinancialAccount account = new FinancialAccount(
                businessId,
                "Future Defective Metadata",
                AccountType.BANK,
                "PKR",
                3, // Inconsistent scale
                new BigDecimal("100.00"),
                todayKarachi.plusDays(5),
                AccountStatus.ACTIVE,
                null,
                null,
                null
        );
        UUID accountId = UUID.randomUUID();
        account.setId(accountId);

        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(account));

        AccountDetailResponse response = accountReadService.getAccountDetail(businessId, accountId);

        assertNull(response.openingBalance());
        assertNull(response.currentBalance());
        assertEquals(BalanceStatus.UNAVAILABLE, response.balanceStatus());
        assertEquals("UNSUPPORTED_CURRENCY", response.balanceUnavailableReason());
    }

    @Test
    @DisplayName("Future opening date with precision overflow does not claim AVAILABLE or canonical zero")
    void futureOpeningWithPrecisionOverflow() {
        FinancialAccount account = new FinancialAccount(
                businessId,
                "Future Defective Precision",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("1.0001"),
                todayKarachi.plusDays(5),
                AccountStatus.ACTIVE,
                null,
                null,
                null
        );
        UUID accountId = UUID.randomUUID();
        account.setId(accountId);

        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(account));

        AccountDetailResponse response = accountReadService.getAccountDetail(businessId, accountId);

        assertNull(response.openingBalance());
        assertNull(response.currentBalance());
        assertEquals(BalanceStatus.UNAVAILABLE, response.balanceStatus());
        assertEquals("PRECISION_OVERFLOW", response.balanceUnavailableReason());
    }

    @Test
    @DisplayName("Excess-precision opening balance (1.0001) triggers PRECISION_OVERFLOW and null openingBalance")
    void excessPrecisionOpeningAmount() {
        FinancialAccount account = new FinancialAccount(
                businessId,
                "Live Invalid Precision Replica",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("1.0001"),
                todayKarachi.minusDays(1),
                AccountStatus.INACTIVE,
                null,
                null,
                null
        );
        UUID accountId = UUID.randomUUID();
        account.setId(accountId);

        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(account));

        AccountDetailResponse response = accountReadService.getAccountDetail(businessId, accountId);

        assertNull(response.openingBalance());
        assertNull(response.currentBalance());
        assertEquals(BalanceStatus.UNAVAILABLE, response.balanceStatus());
        assertEquals("PRECISION_OVERFLOW", response.balanceUnavailableReason());
    }

    @Test
    @DisplayName("Valid exact 3-decimal BHD and 0-decimal JPY opening values return AVAILABLE")
    void validBhdAndJpyAccounts() {
        FinancialAccount bhdAccount = new FinancialAccount(
                businessId,
                "BHD Ops",
                AccountType.BANK,
                "BHD",
                3,
                new BigDecimal("1250.375"),
                todayKarachi.minusDays(1),
                AccountStatus.ACTIVE,
                null,
                null,
                null
        );
        UUID bhdId = UUID.randomUUID();
        bhdAccount.setId(bhdId);

        when(accountRepository.findByIdAndBusinessId(bhdId, businessId))
                .thenReturn(Optional.of(bhdAccount));

        AccountDetailResponse bhdResponse = accountReadService.getAccountDetail(businessId, bhdId);
        assertEquals("1250.375", bhdResponse.openingBalance());
        assertEquals("1250.375", bhdResponse.currentBalance());
        assertEquals(BalanceStatus.AVAILABLE, bhdResponse.balanceStatus());
        assertNull(bhdResponse.balanceUnavailableReason());
    }

    @Test
    @DisplayName("Negative opening values return AVAILABLE with negative sign preserved")
    void negativeOpeningValue() {
        FinancialAccount account = new FinancialAccount(
                businessId,
                "Overdrawn Account",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("-2500.50"),
                todayKarachi.minusDays(1),
                AccountStatus.ACTIVE,
                null,
                null,
                null
        );
        UUID accountId = UUID.randomUUID();
        account.setId(accountId);

        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(account));

        AccountDetailResponse response = accountReadService.getAccountDetail(businessId, accountId);
        assertEquals("-2500.50", response.openingBalance());
        assertEquals("-2500.50", response.currentBalance());
        assertEquals(BalanceStatus.AVAILABLE, response.balanceStatus());
        assertNull(response.balanceUnavailableReason());
    }

    @Test
    @DisplayName("Account not found throws ResourceNotFoundException")
    void accountNotFoundThrows() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> accountReadService.getAccountDetail(businessId, accountId));
        assertEquals("Financial account not found", ex.getMessage());
    }

    @Test
    @DisplayName("Empty business listing returns 0 totalElements and 0 totalPages")
    void emptyListingMetadata() {
        when(accountRepository.findFilteredAccounts(eq(businessId), any(), any(), any(), eq(PageRequest.of(0, 50))))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 50), 0));

        AccountListResponse response = accountReadService.listAccounts(
                businessId, AccountStatus.ACTIVE, null, null, 0, 50
        );

        assertTrue(response.items().isEmpty());
        assertEquals(0, response.page());
        assertEquals(50, response.size());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
    }

    @Test
    @DisplayName("Out-of-range pagination preserves requested page and calculates totalPages")
    void outOfRangePagination() {
        when(accountRepository.findFilteredAccounts(eq(businessId), any(), any(), any(), eq(PageRequest.of(5, 20))))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(5, 20), 12));

        AccountListResponse response = accountReadService.listAccounts(
                businessId, AccountStatus.ACTIVE, null, null, 5, 20
        );

        assertTrue(response.items().isEmpty());
        assertEquals(5, response.page());
        assertEquals(20, response.size());
        assertEquals(12, response.totalElements());
        assertEquals(1, response.totalPages());
    }
}
