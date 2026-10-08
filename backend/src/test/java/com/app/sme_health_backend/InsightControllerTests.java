package com.app.sme_health_backend;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.insight.controller.InsightController;
import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.service.InsightService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InsightController.class)
@Import(GlobalExceptionHandler.class)
class InsightControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsightService insightService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private final UUID userId = UUID.randomUUID();
    private final UUID businessId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), any(BusinessPermission.class))).thenReturn(context);
    }

    @Test
    void shouldReturnInsightsForUser() throws Exception {
        Insight insight = new Insight();
        insight.setUserId(businessId);
        insight.setMonth("2026-09");
        insight.setText("Focus on liquidity.");
        insight.setCategory("liquidity");
        insight.setPriority("high");
        insight.setCreatedAt(LocalDateTime.now());

        when(insightService.getInsights(businessId))
                .thenReturn(List.of(insight));

        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].businessId").value(businessId.toString()))
                .andExpect(jsonPath("$[0].month").value("2026-09"))
                .andExpect(jsonPath("$[0].text").value("Focus on liquidity."))
                .andExpect(jsonPath("$[0].category").value("liquidity"))
                .andExpect(jsonPath("$[0].priority").value("high"));
    }

    @Test
    void shouldReturnEmptyListWhenServiceReturnsNoInsights() throws Exception {
        when(insightService.getInsights(businessId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturnInsightsForSpecificMonth() throws Exception {
        Insight insight = new Insight();
        insight.setUserId(businessId);
        insight.setMonth("2026-08");
        insight.setText("Focus on cash flow.");
        insight.setCategory("cashflow");
        insight.setPriority("high");
        insight.setCreatedAt(LocalDateTime.now());

        when(insightService.getInsights(businessId, "2026-08")).thenReturn(List.of(insight));

        mockMvc.perform(post("/api/insights/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-08\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].month").value("2026-08"))
                .andExpect(jsonPath("$[0].category").value("cashflow"));
    }

    @Test
    void shouldRejectLegacyInsightsWithUserIdUrl() throws Exception {
        mockMvc.perform(get("/api/insights/{userId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
