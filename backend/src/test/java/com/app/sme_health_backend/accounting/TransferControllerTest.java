package com.app.sme_health_backend.accounting;

import com.app.sme_health_backend.accounting.controller.TransferController;
import com.app.sme_health_backend.accounting.dto.MovementDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransferPostRequest;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
import com.app.sme_health_backend.accounting.service.TransferService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransferController.class)
@Import(GlobalExceptionHandler.class)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private UUID businessId;
    private UUID userId;
    private UUID sourceAccountId;
    private UUID destAccountId;
    private UUID txId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        sourceAccountId = UUID.randomUUID();
        destAccountId = UUID.randomUUID();
        txId = UUID.randomUUID();
    }

    @Test
    @DisplayName("POST /api/finance/transfers returns 201 Created for OWNER with valid Idempotency-Key")
    void postTransfer_ownerSuccess() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        TransactionDetailResponse response = new TransactionDetailResponse(
                txId,
                businessId,
                sourceAccountId,
                "Meezan Bank",
                "BANK",
                "INTERNAL_TRANSFER",
                "DECREASE",
                "5000.00",
                "PKR",
                "5000.00",
                LocalDate.of(2026, 10, 16),
                "Transfer to petty cash",
                "45000.00",
                List.of(
                        new MovementDetailResponse(UUID.randomUUID(), sourceAccountId, "DECREASE", "5000.00", "PKR", LocalDate.of(2026, 10, 16), "2026-10-16T12:00:00Z"),
                        new MovementDetailResponse(UUID.randomUUID(), destAccountId, "INCREASE", "5000.00", "PKR", LocalDate.of(2026, 10, 16), "2026-10-16T12:00:00Z")
                ),
                userId,
                "2026-10-16T12:00:00Z",
                sourceAccountId,
                "Meezan Bank",
                destAccountId,
                "Petty Cash"
        );

        when(transferService.postTransfer(eq(context), eq("idem-key-trans-1"), any(TransferPostRequest.class)))
                .thenReturn(response);

        String json = """
                {
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amount": "5000.00",
                  "effectiveDate": "2026-10-16",
                  "description": "Transfer to petty cash"
                }
                """.formatted(sourceAccountId, destAccountId);

        mockMvc.perform(post("/api/finance/transfers")
                        .header("Idempotency-Key", "idem-key-trans-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(txId.toString()))
                .andExpect(jsonPath("$.eventType").value("INTERNAL_TRANSFER"))
                .andExpect(jsonPath("$.movementDirection").value("DECREASE"))
                .andExpect(jsonPath("$.amount").value("5000.00"))
                .andExpect(jsonPath("$.resultingBalance").value("45000.00"))
                .andExpect(jsonPath("$.sourceAccountId").value(sourceAccountId.toString()))
                .andExpect(jsonPath("$.sourceAccountName").value("Meezan Bank"))
                .andExpect(jsonPath("$.destinationAccountId").value(destAccountId.toString()))
                .andExpect(jsonPath("$.destinationAccountName").value("Petty Cash"))
                .andExpect(jsonPath("$.movements.length()").value(2));
    }

    @Test
    @DisplayName("POST /api/finance/transfers returns 201 Created for ACCOUNTANT")
    void postTransfer_accountantSuccess() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        TransactionDetailResponse response = new TransactionDetailResponse(
                txId,
                businessId,
                sourceAccountId,
                "Meezan Bank",
                "BANK",
                "INTERNAL_TRANSFER",
                "DECREASE",
                "5000.00",
                "PKR",
                "5000.00",
                LocalDate.of(2026, 10, 16),
                "Transfer to petty cash",
                "45000.00",
                List.of(
                        new MovementDetailResponse(UUID.randomUUID(), sourceAccountId, "DECREASE", "5000.00", "PKR", LocalDate.of(2026, 10, 16), "2026-10-16T12:00:00Z"),
                        new MovementDetailResponse(UUID.randomUUID(), destAccountId, "INCREASE", "5000.00", "PKR", LocalDate.of(2026, 10, 16), "2026-10-16T12:00:00Z")
                ),
                userId,
                "2026-10-16T12:00:00Z"
        );

        when(transferService.postTransfer(eq(context), eq("idem-key-trans-2"), any(TransferPostRequest.class)))
                .thenReturn(response);

        String json = """
                {
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amount": "5000.00",
                  "effectiveDate": "2026-10-16"
                }
                """.formatted(sourceAccountId, destAccountId);

        mockMvc.perform(post("/api/finance/transfers")
                        .header("Idempotency-Key", "idem-key-trans-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(txId.toString()));
    }

    @Test
    @DisplayName("POST /api/finance/transfers returns 403 Forbidden for MANAGER")
    void postTransfer_forbiddenForManager() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenThrow(new AccessDeniedException("Role MANAGER does not have permission: FINANCIAL_TRANSACTION_POST"));

        String json = """
                {
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amount": "5000.00",
                  "effectiveDate": "2026-10-16"
                }
                """.formatted(sourceAccountId, destAccountId);

        mockMvc.perform(post("/api/finance/transfers")
                        .header("Idempotency-Key", "idem-key-trans-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/finance/transfers returns 403 Forbidden for VIEWER")
    void postTransfer_forbiddenForViewer() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenThrow(new AccessDeniedException("Role VIEWER does not have permission: FINANCIAL_TRANSACTION_POST"));

        String json = """
                {
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amount": "5000.00",
                  "effectiveDate": "2026-10-16"
                }
                """.formatted(sourceAccountId, destAccountId);

        mockMvc.perform(post("/api/finance/transfers")
                        .header("Idempotency-Key", "idem-key-trans-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/finance/transfers maps TransactionValidationException to 400 Bad Request")
    void postTransfer_validationException_returns400() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        when(transferService.postTransfer(eq(context), eq("idem-bad-amt"), any(TransferPostRequest.class)))
                .thenThrow(new TransactionValidationException("amount", "Amount must be greater than zero"));

        String json = """
                {
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amount": "0.00",
                  "effectiveDate": "2026-10-16"
                }
                """.formatted(sourceAccountId, destAccountId);

        mockMvc.perform(post("/api/finance/transfers")
                        .header("Idempotency-Key", "idem-bad-amt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("Amount must be greater than zero"));
    }

    @Test
    @DisplayName("POST /api/finance/transfers maps DuplicateResourceException to 409 Conflict")
    void postTransfer_duplicateException_returns409() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        when(transferService.postTransfer(eq(context), eq("idem-dup"), any(TransferPostRequest.class)))
                .thenThrow(new DuplicateResourceException("Idempotency key 'idem-dup' has already been used with a different request payload"));

        String json = """
                {
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amount": "5000.00",
                  "effectiveDate": "2026-10-16"
                }
                """.formatted(sourceAccountId, destAccountId);

        mockMvc.perform(post("/api/finance/transfers")
                        .header("Idempotency-Key", "idem-dup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/finance/transfers maps ResourceNotFoundException to 404 Not Found")
    void postTransfer_notFound_returns404() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        when(transferService.postTransfer(eq(context), eq("idem-missing-acc"), any(TransferPostRequest.class)))
                .thenThrow(new ResourceNotFoundException("Financial account not found"));

        String json = """
                {
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amount": "5000.00",
                  "effectiveDate": "2026-10-16"
                }
                """.formatted(sourceAccountId, destAccountId);

        mockMvc.perform(post("/api/finance/transfers")
                        .header("Idempotency-Key", "idem-missing-acc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound());
    }
}
