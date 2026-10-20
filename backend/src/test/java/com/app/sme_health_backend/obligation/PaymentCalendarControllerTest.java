package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.dto.PaymentCalendarResponse;
import com.app.sme_health_backend.obligation.dto.PaymentCalendarSummaryResponse;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentCalendarController.class)
@Import(GlobalExceptionHandler.class)
class PaymentCalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentPlanService paymentPlanService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private UUID businessId;
    private UUID userId;
    private BusinessAccessContext context;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);
    }

    @Test
    @DisplayName("getPaymentCalendar returns 200 OK with summary and items")
    void testGetPaymentCalendar_Success() throws Exception {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 11, 1);
        PaymentCalendarSummaryResponse summary = new PaymentCalendarSummaryResponse(
                "100000.00", "40000.00", "10000.00", "0.00", "90000.00", "40000.00"
        );
        PaymentCalendarResponse response = new PaymentCalendarResponse(
                start, end, summary, Collections.emptyList()
        );

        when(paymentPlanService.getPaymentCalendar(eq(context), eq("RECEIVABLE"), eq("2026-10-01"), eq("2026-11-01")))
                .thenReturn(response);

        mockMvc.perform(get("/api/finance/payment-calendar")
                        .param("direction", "RECEIVABLE")
                        .param("from", "2026-10-01")
                        .param("to", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-10-01"))
                .andExpect(jsonPath("$.summary.totalExpectedReceivable").value("100000.00"));
    }
}
