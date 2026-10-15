package com.app.sme_health_backend.accounting;

import com.app.sme_health_backend.accounting.controller.TransactionController;
import com.app.sme_health_backend.accounting.dto.MovementDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionListResponse;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.accounting.service.TransactionReadService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@Import(GlobalExceptionHandler.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionPostService transactionPostService;

    @MockitoBean
    private TransactionReadService transactionReadService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private UUID businessId;
    private UUID userId;
    private UUID accountId;
    private UUID txId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        txId = UUID.randomUUID();
    }

    @Test
    @DisplayName("POST /api/finance/transactions returns 201 Created for OWNER with valid Idempotency-Key")
    void postTransaction_ownerSuccess() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        TransactionDetailResponse response = new TransactionDetailResponse(
                txId,
                businessId,
                accountId,
                "Retail Cash Counter",
                "CASH",
                "DIRECT_SALE_RECEIPT",
                "INCREASE",
                "15000.00",
                "PKR",
                "15000.00",
                LocalDate.of(2026, 10, 14),
                "Counter retail sale",
                "65000.00",
                Collections.singletonList(new MovementDetailResponse(
                        UUID.randomUUID(),
                        accountId,
                        "INCREASE",
                        "15000.00",
                        "PKR",
                        LocalDate.of(2026, 10, 14),
                        "2026-10-14T12:00:00Z"
                )),
                userId,
                "2026-10-14T12:00:00Z"
        );

        when(transactionPostService.postTransaction(eq(context), eq("idem-key-123"), any(TransactionPostRequest.class)))
                .thenReturn(response);

        String json = """
                {
                  "accountId": "%s",
                  "eventType": "DIRECT_SALE_RECEIPT",
                  "amount": "15000.00",
                  "effectiveDate": "2026-10-14",
                  "description": "Counter retail sale"
                }
                """.formatted(accountId);

        mockMvc.perform(post("/api/finance/transactions")
                        .header("Idempotency-Key", "idem-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(txId.toString()))
                .andExpect(jsonPath("$.eventType").value("DIRECT_SALE_RECEIPT"))
                .andExpect(jsonPath("$.movementDirection").value("INCREASE"))
                .andExpect(jsonPath("$.amount").value("15000.00"))
                .andExpect(jsonPath("$.resultingBalance").value("65000.00"))
                .andExpect(jsonPath("$.movements[0].amount").value("15000.00"));
    }

    @Test
    @DisplayName("POST /api/finance/transactions returns 403 Forbidden for MANAGER")
    void postTransaction_forbiddenForManager() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenThrow(new AccessDeniedException("Role MANAGER does not have permission: FINANCIAL_TRANSACTION_POST"));

        String json = """
                {
                  "accountId": "%s",
                  "eventType": "DIRECT_SALE_RECEIPT",
                  "amount": "15000.00",
                  "effectiveDate": "2026-10-14"
                }
                """.formatted(accountId);

        mockMvc.perform(post("/api/finance/transactions")
                        .header("Idempotency-Key", "idem-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/finance/transactions returns 403 Forbidden for VIEWER")
    void postTransaction_forbiddenForViewer() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenThrow(new AccessDeniedException("Role VIEWER does not have permission: FINANCIAL_TRANSACTION_POST"));

        mockMvc.perform(post("/api/finance/transactions")
                        .header("Idempotency-Key", "idem-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/finance/transactions returns 400 Bad Request on validation failure")
    void postTransaction_validationFailure() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        when(transactionPostService.postTransaction(eq(context), eq("idem-key-123"), any()))
                .thenThrow(new TransactionValidationException("amount", "Amount must be greater than zero"));

        String json = """
                {
                  "accountId": "%s",
                  "eventType": "DIRECT_SALE_RECEIPT",
                  "amount": "0.00",
                  "effectiveDate": "2026-10-14"
                }
                """.formatted(accountId);

        mockMvc.perform(post("/api/finance/transactions")
                        .header("Idempotency-Key", "idem-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.amount").value("Amount must be greater than zero"));
    }

    @Test
    @DisplayName("POST /api/finance/transactions returns 409 Conflict when idempotency key reused with mismatched payload")
    void postTransaction_idempotencyConflict() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        when(transactionPostService.postTransaction(eq(context), eq("idem-key-123"), any()))
                .thenThrow(new DuplicateResourceException("Idempotency key 'idem-key-123' has already been used with a different request payload"));

        mockMvc.perform(post("/api/finance/transactions")
                        .header("Idempotency-Key", "idem-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("GET /api/finance/transactions/{id} returns 200 OK for authorized role")
    void getTransactionDetail_success() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.VIEWER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        TransactionDetailResponse response = new TransactionDetailResponse(
                txId,
                businessId,
                accountId,
                "Operating Bank Account",
                "BANK",
                "OPERATING_EXPENSE_PAYMENT",
                "DECREASE",
                "5000.00",
                "PKR",
                "5000.00",
                LocalDate.of(2026, 10, 14),
                "Office supplies",
                "45000.00",
                Collections.emptyList(),
                userId,
                "2026-10-14T12:00:00Z"
        );

        when(transactionReadService.getTransactionDetail(businessId, txId)).thenReturn(response);

        mockMvc.perform(get("/api/finance/transactions/" + txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(txId.toString()))
                .andExpect(jsonPath("$.eventType").value("OPERATING_EXPENSE_PAYMENT"))
                .andExpect(jsonPath("$.movementDirection").value("DECREASE"))
                .andExpect(jsonPath("$.amount").value("5000.00"))
                .andExpect(jsonPath("$.resultingBalance").value("45000.00"));
    }

    @Test
    @DisplayName("GET /api/finance/transactions/{id} returns 404 Not Found when transaction does not exist")
    void getTransactionDetail_notFound() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        when(transactionReadService.getTransactionDetail(businessId, txId))
                .thenThrow(new ResourceNotFoundException("Financial transaction not found"));

        mockMvc.perform(get("/api/finance/transactions/" + txId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/finance/transactions returns 200 OK with paginated list")
    void listTransactions_success() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.MANAGER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        TransactionListResponse response = new TransactionListResponse(
                Collections.emptyList(),
                0,
                20,
                0,
                0,
                LocalDate.of(2026, 10, 14),
                "2026-10-14T12:00:00Z"
        );

        when(transactionReadService.listTransactions(businessId, null, null, null, null, 0, 20))
                .thenReturn(response);

        mockMvc.perform(get("/api/finance/transactions")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.effectiveCutoffDate").value("2026-10-14"));
    }

    @Test
    @DisplayName("GET /api/finance/transactions/summary returns 200 OK with server-calculated summary")
    void getSummary_success() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.VIEWER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        com.app.sme_health_backend.accounting.dto.TransactionSummaryResponse response =
                new com.app.sme_health_backend.accounting.dto.TransactionSummaryResponse(
                        "PKR",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 31),
                        accountId,
                        "DIRECT_SALE_RECEIPT",
                        5,
                        "50000.00",
                        "0.00",
                        "50000.00",
                        "2026-10-14T20:30:00Z"
                );

        when(transactionReadService.getTransactionSummary(
                businessId, accountId, "DIRECT_SALE_RECEIPT", "2026-10-01", "2026-10-31"
        )).thenReturn(response);

        mockMvc.perform(get("/api/finance/transactions/summary")
                        .param("accountId", accountId.toString())
                        .param("eventType", "DIRECT_SALE_RECEIPT")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PKR"))
                .andExpect(jsonPath("$.transactionCount").value(5))
                .andExpect(jsonPath("$.moneyReceived").value("50000.00"))
                .andExpect(jsonPath("$.moneyPaid").value("0.00"))
                .andExpect(jsonPath("$.netCashMovement").value("50000.00"))
                .andExpect(jsonPath("$.observationAt").value("2026-10-14T20:30:00Z"));
    }

    @Test
    @DisplayName("GET /api/finance/transactions/summary returns 400 Bad Request when startDate > endDate")
    void getSummary_invalidRange() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        when(transactionReadService.getTransactionSummary(any(), any(), any(), eq("2026-10-31"), eq("2026-10-01")))
                .thenThrow(new TransactionValidationException("startDate", "Start date cannot be after end date"));

        mockMvc.perform(get("/api/finance/transactions/summary")
                        .param("startDate", "2026-10-31")
                        .param("endDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startDate").value("Start date cannot be after end date"));
    }

    @Test
    @DisplayName("GET /api/finance/transactions/summary does not collide with /{id}")
    void summaryDoesNotCollideWithIdEndpoint() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        com.app.sme_health_backend.accounting.dto.TransactionSummaryResponse response =
                new com.app.sme_health_backend.accounting.dto.TransactionSummaryResponse(
                        "PKR", null, null, null, null, 0, "0.00", "0.00", "0.00", "2026-10-14T20:30:00Z"
                );

        when(transactionReadService.getTransactionSummary(businessId, null, null, null, null))
                .thenReturn(response);

        mockMvc.perform(get("/api/finance/transactions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PKR"))
                .andExpect(jsonPath("$.transactionCount").value(0));
    }
}
