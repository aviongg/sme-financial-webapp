package com.app.sme_health_backend.reconciliation.controller;

import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.reconciliation.dto.*;
import com.app.sme_health_backend.reconciliation.model.ReconciliationMatchMethod;
import com.app.sme_health_backend.reconciliation.model.StatementEntryStatus;
import com.app.sme_health_backend.reconciliation.model.StatementImportStatus;
import com.app.sme_health_backend.reconciliation.service.BankReconciliationService;
import com.app.sme_health_backend.reconciliation.service.BankStatementImportService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BankReconciliationController.class)
@Import(GlobalExceptionHandler.class)
class BankReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BankStatementImportService importService;

    @MockitoBean
    private BankReconciliationService reconciliationService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private BusinessAccessContext context;
    private UUID businessId;
    private UUID accountId;
    private UUID entryId;
    private UUID movementId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        entryId = UUID.randomUUID();
        movementId = UUID.randomUUID();
        context = new BusinessAccessContext(UUID.randomUUID(), businessId, MembershipRole.OWNER);
    }

    @Test
    @DisplayName("POST /api/finance/reconciliation/imports returns 201 Created")
    void testImportStatementReturns201() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenReturn(context);

        StatementImportResponse response = new StatementImportResponse(
                UUID.randomUUID(), accountId, "test.csv", 1, null, null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1),
                StatementImportStatus.ACTIVE, 0, Instant.now()
        );
        when(importService.importStatement(eq(context), any(), any())).thenReturn(response);

        String json = """
                {
                    "accountId": "%s",
                    "sourceFilename": "test.csv",
                    "entries": [
                        {
                            "sourceRowNumber": 1,
                            "postedDate": "2026-10-01",
                            "direction": "INCREASE",
                            "amount": 5000.00,
                            "description": "Deposit"
                        }
                    ]
                }
                """.formatted(accountId);

        mockMvc.perform(post("/api/finance/reconciliation/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.totalEntries").value(1));
    }

    @Test
    @DisplayName("GET /api/finance/reconciliation/entries/{id}/candidates returns candidate list")
    void testGetCandidatesReturns200() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ)))
                .thenReturn(context);

        ReconciliationMatchCandidateResponse candidate = new ReconciliationMatchCandidateResponse(
                movementId, UUID.randomUUID(),
                com.app.sme_health_backend.accounting.model.TransactionEventType.DIRECT_SALE_RECEIPT,
                MovementDirection.INCREASE, new BigDecimal("50000.00"), "PKR",
                LocalDate.of(2026, 10, 15), "Customer receipt", "REF-001", "Client A",
                95, List.of("Exact amount and direction match")
        );
        when(reconciliationService.findCandidates(context, entryId)).thenReturn(List.of(candidate));

        mockMvc.perform(get("/api/finance/reconciliation/entries/{id}/candidates", entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountMovementId").value(movementId.toString()))
                .andExpect(jsonPath("$[0].confidenceScore").value(95));
    }

    @Test
    @DisplayName("POST /api/finance/reconciliation/entries/{id}/match with unauthorized role returns 403 Forbidden")
    void testMatchWithUnauthorizedRoleReturns403() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenThrow(new AccessDeniedException("Insufficient business permissions"));

        String json = """
                {
                    "accountMovementId": "%s"
                }
                """.formatted(movementId);

        mockMvc.perform(post("/api/finance/reconciliation/entries/{id}/match", entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/finance/reconciliation/accounts/{accountId}/summary returns 200 with summary KPIs")
    void testGetAccountSummaryReturns200() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ)))
                .thenReturn(context);

        AccountReconciliationSummaryResponse summary = new AccountReconciliationSummaryResponse(
                accountId, "HBL Business", "BANK", "PKR", new BigDecimal("500000.00"),
                100, 80, 15, 5, 10,
                new BigDecimal("200000.00"), new BigDecimal("350000.00"),
                new BigDecimal("450000.00"), new BigDecimal("100000.00"),
                LocalDate.of(2026, 1, 1), Instant.now()
        );
        when(reconciliationService.getAccountSummary(context, accountId)).thenReturn(summary);

        mockMvc.perform(get("/api/finance/reconciliation/accounts/{accountId}/summary", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountName").value("HBL Business"))
                .andExpect(jsonPath("$.totalStatementEntries").value(100))
                .andExpect(jsonPath("$.matchedEntries").value(80));
    }
}
