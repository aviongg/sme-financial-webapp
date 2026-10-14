package com.app.sme_health_backend.accounting.service;

import com.app.sme_health_backend.accounting.dto.AccountDetailResponse;
import com.app.sme_health_backend.accounting.dto.AccountListItemResponse;
import com.app.sme_health_backend.accounting.dto.AccountListResponse;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.BalanceStatus;
import com.app.sme_health_backend.accounting.model.CurrencyRegistry;
import com.app.sme_health_backend.accounting.model.CurrencyRegistry.BalanceEvaluation;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AccountReadService {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Karachi");
    private static final DateTimeFormatter ISO_INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final FinancialAccountRepository accountRepository;

    public AccountReadService(FinancialAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AccountListResponse listAccounts(
            UUID businessId,
            AccountStatus status,
            AccountType accountType,
            String currency,
            int page,
            int size
    ) {
        Instant snapshotInstant = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String observationAt = ISO_INSTANT_FORMATTER.format(snapshotInstant);
        LocalDate effectiveCutoffDate = LocalDate.now(BUSINESS_ZONE);

        String normalizedCurrency = (currency != null && !currency.isBlank())
                ? currency.trim().toUpperCase(java.util.Locale.ROOT)
                : null;

        Pageable pageable = PageRequest.of(page, size);
        Page<FinancialAccount> accountsPage = accountRepository.findFilteredAccounts(
                businessId, status, accountType, normalizedCurrency, pageable
        );

        List<AccountListItemResponse> items = accountsPage.getContent().stream()
                .map(acc -> toListItemResponse(acc, observationAt, effectiveCutoffDate))
                .toList();

        long totalElements = accountsPage.getTotalElements();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        return new AccountListResponse(items, page, size, totalElements, totalPages);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AccountDetailResponse getAccountDetail(UUID businessId, UUID accountId) {
        Instant snapshotInstant = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String observationAt = ISO_INSTANT_FORMATTER.format(snapshotInstant);
        LocalDate effectiveCutoffDate = LocalDate.now(BUSINESS_ZONE);

        FinancialAccount account = accountRepository.findByIdAndBusinessId(accountId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

        return toDetailResponse(account, observationAt, effectiveCutoffDate);
    }

    private AccountListItemResponse toListItemResponse(
            FinancialAccount account,
            String observationAt,
            LocalDate effectiveCutoffDate
    ) {
        DerivedBalance derived = computeDerivedBalance(account, effectiveCutoffDate);

        return new AccountListItemResponse(
                account.getId(),
                account.getAccountName(),
                account.getAccountType(),
                account.getCurrency(),
                account.getCurrencyDecimals(),
                derived.openingBalanceStr(),
                account.getOpeningDate(),
                derived.currentBalance(),
                derived.balanceStatus(),
                derived.balanceUnavailableReason(),
                account.getStatus(),
                observationAt,
                effectiveCutoffDate,
                account.getInstitutionName(),
                CurrencyRegistry.maskIdentifier(account.getAccountIdentifier()),
                ISO_INSTANT_FORMATTER.format(account.getCreatedAt().truncatedTo(ChronoUnit.SECONDS)),
                ISO_INSTANT_FORMATTER.format(account.getUpdatedAt().truncatedTo(ChronoUnit.SECONDS))
        );
    }

    AccountDetailResponse toDetailResponse(
            FinancialAccount account,
            String observationAt,
            LocalDate effectiveCutoffDate
    ) {
        DerivedBalance derived = computeDerivedBalance(account, effectiveCutoffDate);

        return new AccountDetailResponse(
                account.getId(),
                account.getAccountName(),
                account.getAccountType(),
                account.getCurrency(),
                account.getCurrencyDecimals(),
                derived.openingBalanceStr(),
                account.getOpeningDate(),
                derived.currentBalance(),
                derived.balanceStatus(),
                derived.balanceUnavailableReason(),
                account.getStatus(),
                observationAt,
                effectiveCutoffDate,
                account.getInstitutionName(),
                CurrencyRegistry.maskIdentifier(account.getAccountIdentifier()),
                account.getNotes(),
                ISO_INSTANT_FORMATTER.format(account.getCreatedAt().truncatedTo(ChronoUnit.SECONDS)),
                ISO_INSTANT_FORMATTER.format(account.getUpdatedAt().truncatedTo(ChronoUnit.SECONDS))
        );
    }

    private record DerivedBalance(
            String openingBalanceStr,
            String currentBalance,
            BalanceStatus balanceStatus,
            String balanceUnavailableReason
    ) {}

    private DerivedBalance computeDerivedBalance(FinancialAccount account, LocalDate effectiveCutoffDate) {
        String currency = account.getCurrency();
        Integer currencyDecimals = account.getCurrencyDecimals();
        BigDecimal openingBalance = account.getOpeningBalance();

        // 1. Currency metadata validation: must match registry supported currency AND declared scale
        // Rule 2: Invalid currency/scale metadata -> openingBalance=null, currentBalance=null, UNSUPPORTED_CURRENCY
        if (!CurrencyRegistry.isSupported(currency, currencyDecimals)) {
            return new DerivedBalance(
                    null,
                    null,
                    BalanceStatus.UNAVAILABLE,
                    "UNSUPPORTED_CURRENCY"
            );
        }

        // 2. Evaluate opening balance with verified currency metadata
        BalanceEvaluation evalOpening = CurrencyRegistry.evaluateBalance(
                openingBalance, currency, currencyDecimals
        );

        // Rule 2: Invalid opening precision/capacity or integrity -> openingBalance=null, currentBalance=null
        // Never expose the malformed amount, round it, fabricate zero or repair stored metadata silently.
        if (evalOpening.status() != BalanceStatus.AVAILABLE) {
            return new DerivedBalance(
                    null,
                    null,
                    BalanceStatus.UNAVAILABLE,
                    evalOpening.unavailableReason()
            );
        }

        String openingBalanceStr = evalOpening.formattedBalance();

        // 3. Pre-Opening Date Invariant: If openingDate > effectiveCutoffDate, balance is canonical zero
        // Rule 4: Valid future-opening accounts retain their valid openingBalance, with AVAILABLE currentBalance at canonical zero
        if (account.getOpeningDate().isAfter(effectiveCutoffDate)) {
            return new DerivedBalance(
                    openingBalanceStr,
                    CurrencyRegistry.formatCanonicalZero(currency),
                    BalanceStatus.AVAILABLE,
                    null
            );
        }

        // 4. Opening Date <= effectiveCutoffDate: opening balance is included as initial balance
        // Rule 1: AVAILABLE: valid metadata, openingBalance and currentBalance are non-null exact currency amounts, reason=null
        // Rule 3 Note: In future slices with transaction ledgers, if current balance alone fails integrity check,
        // valid openingBalance will be preserved while currentBalance is set to null with INTEGRITY_CHECK_FAILED.
        return new DerivedBalance(
                openingBalanceStr,
                evalOpening.formattedBalance(),
                BalanceStatus.AVAILABLE,
                null
        );
    }
}
