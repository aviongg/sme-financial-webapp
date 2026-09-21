package com.app.sme_health_backend;

import com.app.sme_health_backend.insight.controller.InsightController;
import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.service.InsightService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InsightController.class)
@Import(GlobalExceptionHandler.class)
class InsightControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsightService insightService;

    @Test
    void shouldReturnInsightsForUser() throws Exception {
        UUID userId = UUID.randomUUID();
        Insight insight = new Insight();

        insight.setUserId(userId);
        insight.setMonth("2026-09");
        insight.setText("Focus on liquidity.");
        insight.setCategory("liquidity");
        insight.setPriority("high");
        insight.setCreatedAt(LocalDateTime.now());
        insight.setSourceVersion("a".repeat(64));
        insight.setLanguage("ur");
        insight.setSourceComputedAt(LocalDateTime.of(2026, 9, 18, 12, 30));

        when(insightService.getInsights(userId))
                .thenReturn(List.of(insight));

        mockMvc.perform(get("/api/insights/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId")
                        .value(userId.toString()))
                .andExpect(jsonPath("$[0].month").value("2026-09"))
                .andExpect(jsonPath("$[0].text")
                        .value("Focus on liquidity."))
                .andExpect(jsonPath("$[0].category")
                        .value("liquidity"))
                .andExpect(jsonPath("$[0].priority").value("high"))
                .andExpect(jsonPath("$[0].sourceVersion").value("a".repeat(64)))
                .andExpect(jsonPath("$[0].language").value("ur"))
                .andExpect(jsonPath("$[0].sourceComputedAt").value("2026-09-18T12:30:00"));
    }

    @Test
    void shouldReturnEmptyListWhenServiceReturnsNoInsights() throws Exception {
        UUID userId = UUID.randomUUID();

        when(insightService.getInsights(userId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/insights/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldPassAnExplicitMonthToTheService() throws Exception {
        UUID userId = UUID.randomUUID();
        when(insightService.getInsights(userId, "2026-08")).thenReturn(List.of());

        mockMvc.perform(get("/api/insights/{userId}", userId).param("month", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(insightService).getInsights(userId, "2026-08");
    }

    @Test
    void shouldReturnBadRequestForAnInvalidMonth() throws Exception {
        UUID userId = UUID.randomUUID();
        when(insightService.getInsights(userId, "2026-13"))
                .thenThrow(new IllegalArgumentException("Month must be a valid YYYY-MM value"));

        mockMvc.perform(get("/api/insights/{userId}", userId).param("month", "2026-13"))
                .andExpect(status().isBadRequest());
    }
}
