package com.app.sme_health_backend.accounting;

import com.app.sme_health_backend.accounting.controller.AccountController;
import com.app.sme_health_backend.accounting.dto.AccountDetailResponse;
import com.app.sme_health_backend.accounting.dto.AccountListItemResponse;
import com.app.sme_health_backend.accounting.dto.AccountListResponse;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.BalanceStatus;
import com.app.sme_health_backend.accounting.service.AccountReadService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.shared.exception.ActiveBusinessRequiredException;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Import(GlobalExceptionHandler.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountReadService accountReadService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private final UUID userId = UUID.randomUUID();
    private final UUID businessId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_ACCOUNT_READ))).thenReturn(context);
    }

    @ParameterizedTest
    @EnumSource(value = MembershipRole.class, names = {"OWNER", "ACCOUNTANT", "MANAGER", "VIEWER"})
    @DisplayName("Authorized roles matrix: OWNER, ACCOUNTANT, MANAGER, and VIEWER can list accounts")
    void authorizedRolesCanListAccounts(MembershipRole role) throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, role);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_ACCOUNT_READ))).thenReturn(context);

        when(accountReadService.listAccounts(eq(businessId), eq(AccountStatus.ACTIVE), any(), any(), eq(0), eq(50)))
                .thenReturn(new AccountListResponse(List.of(), 0, 50, 0, 0));

        mockMvc.perform(get("/api/finance/accounts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    @DisplayName("List accounts returns 200 with formatted list item")
    void listAccountsSuccess() throws Exception {
        UUID accountId = UUID.randomUUID();
        AccountListItemResponse item = new AccountListItemResponse(
                accountId,
                "Meezan Bank - Main Operations",
                AccountType.BANK,
                "PKR",
                2,
                "100000.00",
                LocalDate.of(2026, 10, 1),
                "115000.00",
                BalanceStatus.AVAILABLE,
                null,
                AccountStatus.ACTIVE,
                "2026-10-14T13:30:00Z",
                LocalDate.of(2026, 10, 14),
                "Meezan Bank Ltd",
                "****4821",
                "2026-10-14T10:00:00Z",
                "2026-10-14T12:30:00Z"
        );

        when(accountReadService.listAccounts(eq(businessId), eq(AccountStatus.ACTIVE), any(), any(), eq(0), eq(50)))
                .thenReturn(new AccountListResponse(List.of(item), 0, 50, 1, 1));

        mockMvc.perform(get("/api/finance/accounts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(accountId.toString()))
                .andExpect(jsonPath("$.items[0].accountName").value("Meezan Bank - Main Operations"))
                .andExpect(jsonPath("$.items[0].accountType").value("BANK"))
                .andExpect(jsonPath("$.items[0].currency").value("PKR"))
                .andExpect(jsonPath("$.items[0].currencyDecimals").value(2))
                .andExpect(jsonPath("$.items[0].openingBalance").value("100000.00"))
                .andExpect(jsonPath("$.items[0].currentBalance").value("115000.00"))
                .andExpect(jsonPath("$.items[0].balanceStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.items[0].balanceUnavailableReason").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].institutionName").value("Meezan Bank Ltd"))
                .andExpect(jsonPath("$.items[0].accountIdentifier").value("****4821"))
                .andExpect(jsonPath("$.items[0].notes").doesNotExist()) // notes omitted from list item
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("Get account detail returns 200 with notes")
    void getAccountDetailSuccess() throws Exception {
        UUID accountId = UUID.randomUUID();
        AccountDetailResponse detail = new AccountDetailResponse(
                accountId,
                "Meezan Bank - Main Operations",
                AccountType.BANK,
                "PKR",
                2,
                "100000.00",
                LocalDate.of(2026, 10, 1),
                "115000.00",
                BalanceStatus.AVAILABLE,
                null,
                AccountStatus.ACTIVE,
                "2026-10-14T13:30:00Z",
                LocalDate.of(2026, 10, 14),
                "Meezan Bank Ltd",
                "****4821",
                "Primary operational disbursement account",
                "2026-10-14T10:00:00Z",
                "2026-10-14T12:30:00Z"
        );

        when(accountReadService.getAccountDetail(businessId, accountId)).thenReturn(detail);

        mockMvc.perform(get("/api/finance/accounts/{id}", accountId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.accountName").value("Meezan Bank - Main Operations"))
                .andExpect(jsonPath("$.notes").value("Primary operational disbursement account"))
                .andExpect(jsonPath("$.accountIdentifier").value("****4821"));
    }

    @Test
    @DisplayName("Get account detail with invalid opening data returns null openingBalance and null currentBalance")
    void getAccountDetailUnavailableOpeningAndCurrent() throws Exception {
        UUID accountId = UUID.randomUUID();
        AccountDetailResponse detail = new AccountDetailResponse(
                accountId,
                "Tokyo Branch Settlement",
                AccountType.BANK,
                "JPY",
                0,
                null, // Option A: openingBalance is null when opening metadata/precision overflows
                LocalDate.of(2026, 10, 1),
                null,
                BalanceStatus.UNAVAILABLE,
                "UNSUPPORTED_CURRENCY",
                AccountStatus.ACTIVE,
                "2026-10-14T13:30:00Z",
                LocalDate.of(2026, 10, 14),
                "Mitsubishi UFJ",
                "****9012",
                "Metadata investigation",
                "2026-10-14T10:10:00Z",
                "2026-10-14T12:45:00Z"
        );

        when(accountReadService.getAccountDetail(businessId, accountId)).thenReturn(detail);

        mockMvc.perform(get("/api/finance/accounts/{id}", accountId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.openingBalance").doesNotExist())
                .andExpect(jsonPath("$.currentBalance").doesNotExist())
                .andExpect(jsonPath("$.balanceStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.balanceUnavailableReason").value("UNSUPPORTED_CURRENCY"));
    }

    @Test
    @DisplayName("Get account detail with valid opening data but integrity failure preserves openingBalance")
    void getAccountDetailUnavailableCurrentOnly() throws Exception {
        UUID accountId = UUID.randomUUID();
        AccountDetailResponse detail = new AccountDetailResponse(
                accountId,
                "Tokyo Branch Settlement",
                AccountType.BANK,
                "JPY",
                0,
                "500000", // Rule 3: opening balance preserved when valid
                LocalDate.of(2026, 10, 1),
                null,
                BalanceStatus.UNAVAILABLE,
                "INTEGRITY_CHECK_FAILED",
                AccountStatus.ACTIVE,
                "2026-10-14T13:30:00Z",
                LocalDate.of(2026, 10, 14),
                "Mitsubishi UFJ",
                "****9012",
                "Ledger integrity investigation",
                "2026-10-14T10:10:00Z",
                "2026-10-14T12:45:00Z"
        );

        when(accountReadService.getAccountDetail(businessId, accountId)).thenReturn(detail);

        mockMvc.perform(get("/api/finance/accounts/{id}", accountId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.openingBalance").value("500000"))
                .andExpect(jsonPath("$.currentBalance").doesNotExist())
                .andExpect(jsonPath("$.balanceStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.balanceUnavailableReason").value("INTEGRITY_CHECK_FAILED"));
    }

    @Test
    @DisplayName("Non-existent or cross-business account returns 404 with exact message")
    void accountNotFoundReturns404() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(accountReadService.getAccountDetail(businessId, accountId))
                .thenThrow(new ResourceNotFoundException("Financial account not found"));

        mockMvc.perform(get("/api/finance/accounts/{id}", accountId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Financial account not found"));
    }

    @Test
    @DisplayName("Invalid UUID path variable returns 400 Validation Failed")
    void invalidUuidReturns400() throws Exception {
        mockMvc.perform(get("/api/finance/accounts/abc-123")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message").value("One or more request parameters are invalid"))
                .andExpect(jsonPath("$.errors.id").value("Invalid UUID format: abc-123"));
    }

    @Test
    @DisplayName("Invalid pagination parameters return 400 Validation Failed")
    void invalidPaginationReturns400() throws Exception {
        // Negative page
        mockMvc.perform(get("/api/finance/accounts?page=-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.page").value("Page must be greater than or equal to 0"));

        // Non-integer page
        mockMvc.perform(get("/api/finance/accounts?page=abc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.page").value("Invalid page number: abc"));

        // Size < 1
        mockMvc.perform(get("/api/finance/accounts?size=0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").value("Size must be between 1 and 100"));

        // Size > 100
        mockMvc.perform(get("/api/finance/accounts?size=101")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").value("Size must be between 1 and 100"));
    }

    @Test
    @DisplayName("Invalid filters return 400 Validation Failed")
    void invalidFiltersReturn400() throws Exception {
        mockMvc.perform(get("/api/finance/accounts?status=BOGUS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").value("Invalid status: BOGUS"));

        mockMvc.perform(get("/api/finance/accounts?accountType=CRYPTO")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.accountType").value("Invalid accountType: CRYPTO"));
    }

    @Test
    @DisplayName("Missing permission returns 403 Forbidden")
    void missingPermissionReturns403() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_ACCOUNT_READ)))
                .thenThrow(new AccessDeniedException("Caller does not have permission: FINANCIAL_ACCOUNT_READ"));

        mockMvc.perform(get("/api/finance/accounts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Caller does not have permission: FINANCIAL_ACCOUNT_READ"));
    }

    @Test
    @DisplayName("Active business required returns 409 active_business_required")
    void activeBusinessRequiredReturns409() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_ACCOUNT_READ)))
                .thenThrow(new ActiveBusinessRequiredException("Select or create a business before using this feature."));

        mockMvc.perform(get("/api/finance/accounts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("active_business_required"))
                .andExpect(jsonPath("$.message").value("Select or create a business before using this feature."));
    }
}
