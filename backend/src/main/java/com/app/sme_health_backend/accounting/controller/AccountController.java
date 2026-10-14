package com.app.sme_health_backend.accounting.controller;

import com.app.sme_health_backend.accounting.dto.AccountDetailResponse;
import com.app.sme_health_backend.accounting.dto.AccountListResponse;
import com.app.sme_health_backend.accounting.exception.AccountValidationException;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.service.AccountReadService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/accounts")
public class AccountController {

    private final AccountReadService accountReadService;
    private final BusinessAuthorizationService authService;

    public AccountController(
            AccountReadService accountReadService,
            BusinessAuthorizationService authService
    ) {
        this.accountReadService = accountReadService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<AccountListResponse> listAccounts(
            @RequestParam(name = "status", required = false, defaultValue = "ACTIVE") String statusStr,
            @RequestParam(name = "accountType", required = false) String accountTypeStr,
            @RequestParam(name = "currency", required = false) String currency,
            @RequestParam(name = "page", required = false, defaultValue = "0") String pageStr,
            @RequestParam(name = "size", required = false, defaultValue = "50") String sizeStr,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_ACCOUNT_READ);

        Map<String, String> errors = new LinkedHashMap<>();

        AccountStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = AccountStatus.valueOf(statusStr.trim());
            } catch (IllegalArgumentException e) {
                errors.put("status", "Invalid status: " + statusStr);
            }
        }

        AccountType accountType = null;
        if (accountTypeStr != null && !accountTypeStr.isBlank()) {
            try {
                accountType = AccountType.valueOf(accountTypeStr.trim());
            } catch (IllegalArgumentException e) {
                errors.put("accountType", "Invalid accountType: " + accountTypeStr);
            }
        }

        int page = 0;
        try {
            page = Integer.parseInt(pageStr);
            if (page < 0) {
                errors.put("page", "Page must be greater than or equal to 0");
            }
        } catch (NumberFormatException e) {
            errors.put("page", "Invalid page number: " + pageStr);
        }

        int size = 50;
        try {
            size = Integer.parseInt(sizeStr);
            if (size < 1 || size > 100) {
                errors.put("size", "Size must be between 1 and 100");
            }
        } catch (NumberFormatException e) {
            errors.put("size", "Invalid page size: " + sizeStr);
        }

        if (!errors.isEmpty()) {
            throw new AccountValidationException(errors);
        }

        String normalizedCurrency = (currency != null && !currency.isBlank())
                ? currency.trim().toUpperCase(Locale.ROOT)
                : null;

        AccountListResponse response = accountReadService.listAccounts(
                context.businessId(), status, accountType, normalizedCurrency, page, size
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountDetailResponse> getAccount(
            @PathVariable("id") String idStr,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_ACCOUNT_READ);

        UUID accountId;
        try {
            accountId = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            throw new AccountValidationException("id", "Invalid UUID format: " + idStr);
        }

        AccountDetailResponse response = accountReadService.getAccountDetail(context.businessId(), accountId);
        return ResponseEntity.ok(response);
    }
}
