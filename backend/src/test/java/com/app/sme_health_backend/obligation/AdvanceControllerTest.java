package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.dto.*;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdvanceController.class)
@Import(GlobalExceptionHandler.class)
class AdvanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdvanceService advanceService;

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
    @DisplayName("createAdvance returns 201 Created on valid advance")
    void testCreateAdvance_Success() throws Exception {
        UUID cpId = UUID.randomUUID();
        UUID accId = UUID.randomUUID();
        UUID advId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        AdvanceCreateRequest req = new AdvanceCreateRequest(
                cpId, "RECEIVABLE", accId, "2026-10-01", "30000.00", "ADV-01", "Advance"
        );

        AdvanceDetailResponse detail = new AdvanceDetailResponse(
                advId, businessId, cpId, "Customer A", "CUSTOMER", "RECEIVABLE",
                txId, "30000.00", "0.00", "30000.00", "PKR", LocalDate.of(2026, 10, 1),
                "ACTIVE", "ADV-01", "Advance", null, null, null,
                "2026-10-01T10:00:00Z", Collections.emptyList()
        );

        when(advanceService.createAdvance(eq(context), eq("idemp-adv-1"), any(AdvanceCreateRequest.class)))
                .thenReturn(detail);

        mockMvc.perform(post("/api/finance/advances")
                        .header("Idempotency-Key", "idemp-adv-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(advId.toString()))
                .andExpect(jsonPath("$.originalAmount").value("30000.00"))
                .andExpect(jsonPath("$.unappliedAmount").value("30000.00"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("applyAdvance returns 201 Created on valid application")
    void testApplyAdvance_Success() throws Exception {
        UUID advId = UUID.randomUUID();
        UUID obId = UUID.randomUUID();
        AdvanceApplyRequest req = new AdvanceApplyRequest(obId, "20000.00", "2026-10-05", "Apply");

        AdvanceDetailResponse detail = new AdvanceDetailResponse(
                advId, businessId, UUID.randomUUID(), "Customer A", "CUSTOMER", "RECEIVABLE",
                UUID.randomUUID(), "30000.00", "20000.00", "10000.00", "PKR", LocalDate.of(2026, 10, 1),
                "ACTIVE", "ADV-01", "Advance", null, null, null,
                "2026-10-01T10:00:00Z", Collections.emptyList()
        );

        when(advanceService.applyAdvance(eq(context), eq("idemp-apply-1"), eq(advId), any(AdvanceApplyRequest.class)))
                .thenReturn(detail);

        mockMvc.perform(post("/api/finance/advances/" + advId + "/applications")
                        .header("Idempotency-Key", "idemp-apply-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appliedAmount").value("20000.00"))
                .andExpect(jsonPath("$.unappliedAmount").value("10000.00"));
    }

    @Test
    @DisplayName("reverseApplication returns 200 OK on successful reversal")
    void testReverseApplication_Success() throws Exception {
        UUID advId = UUID.randomUUID();
        UUID allocId = UUID.randomUUID();
        AdvanceApplicationReverseRequest req = new AdvanceApplicationReverseRequest("Wrong invoice");

        AdvanceDetailResponse detail = new AdvanceDetailResponse(
                advId, businessId, UUID.randomUUID(), "Customer A", "CUSTOMER", "RECEIVABLE",
                UUID.randomUUID(), "30000.00", "0.00", "30000.00", "PKR", LocalDate.of(2026, 10, 1),
                "ACTIVE", "ADV-01", "Advance", null, null, null,
                "2026-10-01T10:00:00Z", Collections.emptyList()
        );

        when(advanceService.reverseApplication(eq(context), eq("idemp-rev-app-1"), eq(advId), eq(allocId), any(AdvanceApplicationReverseRequest.class)))
                .thenReturn(detail);

        mockMvc.perform(post("/api/finance/advances/" + advId + "/applications/" + allocId + "/reverse")
                        .header("Idempotency-Key", "idemp-rev-app-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unappliedAmount").value("30000.00"))
                .andExpect(jsonPath("$.appliedAmount").value("0.00"));
    }
}
