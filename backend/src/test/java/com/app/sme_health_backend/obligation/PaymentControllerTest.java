package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.dto.AllocationItemRequest;
import com.app.sme_health_backend.obligation.dto.PaymentAllocateRequest;
import com.app.sme_health_backend.obligation.dto.PaymentAllocateResponse;
import com.app.sme_health_backend.obligation.dto.PaymentDetailResponse;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentAllocationService paymentAllocationService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID businessId;
    private UUID userId;
    private BusinessAccessContext context;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);
    }

    @Test
    @DisplayName("allocatePayment returns 201 Created on valid allocation")
    void testAllocatePayment_Success() throws Exception {
        UUID cpId = UUID.randomUUID();
        UUID accId = UUID.randomUUID();
        UUID obId = UUID.randomUUID();
        UUID payId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        PaymentAllocateRequest req = new PaymentAllocateRequest(
                cpId, "RECEIVABLE", accId, "2026-10-02", "50000.00",
                List.of(new AllocationItemRequest(obId, "50000.00")),
                "REF-01", "Settlement", false
        );

        PaymentAllocateResponse resp = new PaymentAllocateResponse(
                payId, txId, cpId, "Customer A", accId, "Bank Account",
                LocalDate.of(2026, 10, 2), "50000.00", "50000.00", "0.00", null,
                "1050000.00", Collections.emptyList(), "COMPLETED"
        );

        when(paymentAllocationService.allocatePayment(eq(context), eq("idemp-key-1"), any(PaymentAllocateRequest.class)))
                .thenReturn(resp);

        mockMvc.perform(post("/api/finance/payments/allocate")
                        .header("Idempotency-Key", "idemp-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(payId.toString()))
                .andExpect(jsonPath("$.totalCashAmount").value("50000.00"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("getPayment returns 200 OK on existing payment")
    void testGetPayment_Success() throws Exception {
        UUID payId = UUID.randomUUID();
        PaymentDetailResponse detail = new PaymentDetailResponse(
                payId, businessId, UUID.randomUUID(), "Bank Account", UUID.randomUUID(), "Customer A",
                "RECEIVABLE_COLLECTION", "RECEIVABLE", "50000.00", LocalDate.of(2026, 10, 2), "Batch note",
                false, null, false, null, "2026-10-02T10:00:00Z", Collections.emptyList(), null
        );

        when(paymentAllocationService.getPayment(eq(context), eq(payId))).thenReturn(detail);

        mockMvc.perform(get("/api/finance/payments/" + payId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(payId.toString()))
                .andExpect(jsonPath("$.amount").value("50000.00"))
                .andExpect(jsonPath("$.counterpartyName").value("Customer A"));
    }
}
