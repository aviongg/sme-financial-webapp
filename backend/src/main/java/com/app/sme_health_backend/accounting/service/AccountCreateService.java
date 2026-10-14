package com.app.sme_health_backend.accounting.service;

import com.app.sme_health_backend.accounting.dto.AccountCreateRequest;
import com.app.sme_health_backend.accounting.dto.AccountDetailResponse;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.exception.AccountValidationException;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.CurrencyRegistry;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AccountCreateService {

    public static final String OPERATION_ACCOUNT_CREATE = "ACCOUNT_CREATE";
    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");
    private static final DateTimeFormatter ISO_INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final FinancialAccountRepository accountRepository;
    private final FinancialIdempotencyRepository idempotencyRepository;
    private final AccountReadService accountReadService;
    private final ObjectMapper objectMapper;

    public AccountCreateService(
            FinancialAccountRepository accountRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            AccountReadService accountReadService,
            ObjectMapper objectMapper
    ) {
        this.accountRepository = accountRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.accountReadService = accountReadService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AccountDetailResponse createAccount(
            BusinessAccessContext context,
            String idempotencyKey,
            AccountCreateRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new AccountValidationException("Idempotency-Key", "Missing required header: Idempotency-Key");
        }
        String trimmedKey = idempotencyKey.trim();
        if (trimmedKey.length() > 100) {
            throw new AccountValidationException("Idempotency-Key", "Idempotency-Key must not exceed 100 characters");
        }

        if (request == null) {
            throw new AccountValidationException("body", "Request body is required");
        }

        Map<String, String> errors = new LinkedHashMap<>();

        // 1. Account Name
        String trimmedName = null;
        if (request.accountName() == null || request.accountName().trim().isEmpty()) {
            errors.put("accountName", "Account name is required");
        } else if (request.accountName().trim().length() > 100) {
            errors.put("accountName", "Account name must not exceed 100 characters");
        } else {
            trimmedName = request.accountName().trim();
        }

        // 2. Account Type
        AccountType accountType = null;
        if (request.accountType() == null || request.accountType().trim().isEmpty()) {
            errors.put("accountType", "Account type is required");
        } else {
            try {
                accountType = AccountType.valueOf(request.accountType().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.put("accountType", "Invalid accountType: " + request.accountType() + ". Must be CASH, BANK, or WALLET");
            }
        }

        // 3. Currency
        String normalizedCurrency = null;
        Integer scale = null;
        if (request.currency() == null || request.currency().trim().isEmpty()) {
            errors.put("currency", "Currency is required");
        } else {
            normalizedCurrency = request.currency().trim().toUpperCase(Locale.ROOT);
            if (!CurrencyRegistry.isSupported(normalizedCurrency)) {
                errors.put("currency", "Unsupported currency: " + request.currency() + ". Supported currencies: " + String.join(", ", CurrencyRegistry.supportedCurrencies()));
            } else {
                scale = CurrencyRegistry.getScale(normalizedCurrency);
            }
        }

        // 4. Opening Balance
        BigDecimal openingBalance = null;
        if (request.openingBalance() == null || request.openingBalance().trim().isEmpty()) {
            errors.put("openingBalance", "Opening balance is required");
        } else {
            try {
                openingBalance = new BigDecimal(request.openingBalance().trim());
                if (openingBalance.abs().compareTo(CurrencyRegistry.MAX_CAPACITY) >= 0) {
                    errors.put("openingBalance", "Opening balance exceeds maximum capacity of 999,999,999,999");
                } else if (scale != null && openingBalance.scale() > scale) {
                    errors.put("openingBalance", "Opening balance exceeds currency decimal scale of " + scale);
                } else if (accountType == AccountType.CASH && openingBalance.signum() < 0) {
                    errors.put("openingBalance", "Cash account opening balance cannot be negative");
                }
            } catch (NumberFormatException e) {
                errors.put("openingBalance", "Invalid decimal format for opening balance");
            }
        }

        // 5. Opening Date
        LocalDate openingDate = null;
        if (request.openingDate() == null || request.openingDate().trim().isEmpty()) {
            openingDate = LocalDate.now(KARACHI_ZONE);
        } else {
            try {
                openingDate = LocalDate.parse(request.openingDate().trim());
            } catch (DateTimeParseException e) {
                errors.put("openingDate", "Invalid date format. Expected YYYY-MM-DD");
            }
        }

        // 6. Institution Name
        String trimmedInstitution = (request.institutionName() != null && !request.institutionName().trim().isEmpty())
                ? request.institutionName().trim()
                : null;
        if (accountType == AccountType.CASH && trimmedInstitution != null) {
            errors.put("institutionName", "Institution name is not permitted for CASH accounts");
        } else if (trimmedInstitution != null && trimmedInstitution.length() > 150) {
            errors.put("institutionName", "Institution name must not exceed 150 characters");
        }

        // 7. Notes
        String trimmedNotes = (request.notes() != null && !request.notes().trim().isEmpty())
                ? request.notes().trim()
                : null;
        if (trimmedNotes != null && trimmedNotes.length() > 1000) {
            errors.put("notes", "Notes must not exceed 1000 characters");
        }

        if (!errors.isEmpty()) {
            throw new AccountValidationException(errors);
        }

        // Compute normalized payload digest
        String payloadHash = computePayloadHash(
                trimmedName,
                accountType,
                normalizedCurrency,
                openingBalance,
                scale,
                openingDate,
                trimmedInstitution,
                trimmedNotes
        );

        // Check durable idempotency
        Optional<FinancialIdempotencyRecord> existingOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                context.businessId(),
                OPERATION_ACCOUNT_CREATE,
                trimmedKey
        );

        if (existingOpt.isPresent()) {
            FinancialIdempotencyRecord existing = existingOpt.get();
            if (existing.getRequestHash().equals(payloadHash)) {
                return deserializeResponse(existing.getResponseBody());
            } else {
                throw new DuplicateResourceException(
                        "Idempotency key '" + trimmedKey + "' has already been used with a different request payload"
                );
            }
        }

        // Check duplicate name constraint
        if (accountRepository.existsByBusinessIdAndAccountName(context.businessId(), trimmedName)) {
            throw new DuplicateResourceException(
                    "An account with name '" + trimmedName + "' already exists for this business"
            );
        }

        try {
            FinancialAccount account = new FinancialAccount();
            account.setBusinessId(context.businessId());
            account.setAccountName(trimmedName);
            account.setAccountType(accountType);
            account.setCurrency(normalizedCurrency);
            account.setCurrencyDecimals(scale);
            account.setOpeningBalance(openingBalance.setScale(scale, RoundingMode.UNNECESSARY));
            account.setOpeningDate(openingDate);
            account.setStatus(AccountStatus.ACTIVE);
            account.setInstitutionName(trimmedInstitution);
            account.setAccountIdentifier(null); // Explicitly omitted in V1
            account.setNotes(trimmedNotes);

            account = accountRepository.saveAndFlush(account);

            LocalDate effectiveCutoffDate = LocalDate.now(KARACHI_ZONE);
            String observationAt = ISO_INSTANT_FORMATTER.format(Instant.now().truncatedTo(ChronoUnit.SECONDS));
            AccountDetailResponse response = accountReadService.toDetailResponse(account, observationAt, effectiveCutoffDate);

            String responseJson = serializeResponse(response);
            FinancialIdempotencyRecord idempotencyRecord = new FinancialIdempotencyRecord(
                    context.businessId(),
                    OPERATION_ACCOUNT_CREATE,
                    trimmedKey,
                    payloadHash,
                    account.getId(),
                    201,
                    responseJson
            );
            idempotencyRepository.saveAndFlush(idempotencyRecord);

            return response;
        } catch (DataIntegrityViolationException e) {
            // Concurrent race handling
            Optional<FinancialIdempotencyRecord> racedIdempotency = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                    context.businessId(),
                    OPERATION_ACCOUNT_CREATE,
                    trimmedKey
            );
            if (racedIdempotency.isPresent()) {
                if (racedIdempotency.get().getRequestHash().equals(payloadHash)) {
                    return deserializeResponse(racedIdempotency.get().getResponseBody());
                } else {
                    throw new DuplicateResourceException(
                            "Idempotency key '" + trimmedKey + "' has already been used with a different request payload"
                    );
                }
            }

            if (accountRepository.existsByBusinessIdAndAccountName(context.businessId(), trimmedName)) {
                throw new DuplicateResourceException(
                        "An account with name '" + trimmedName + "' already exists for this business"
                );
            }

            throw e;
        }
    }

    public static String computePayloadHash(
            String accountName,
            AccountType accountType,
            String currency,
            BigDecimal openingBalance,
            int scale,
            LocalDate openingDate,
            String institutionName,
            String notes
    ) {
        String canonicalBalance = openingBalance.setScale(scale, RoundingMode.UNNECESSARY).toPlainString();
        String canonicalPayload = "accountName=" + accountName
                + "&accountType=" + accountType.name()
                + "&currency=" + currency
                + "&openingBalance=" + canonicalBalance
                + "&openingDate=" + openingDate
                + "&institutionName=" + (institutionName != null ? institutionName : "")
                + "&notes=" + (notes != null ? notes : "");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String serializeResponse(AccountDetailResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize account detail response", e);
        }
    }

    private AccountDetailResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, AccountDetailResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize account detail response", e);
        }
    }
}
