package com.app.sme_health_backend.records;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class MonthlyRecordIntegrationTests extends com.app.sme_health_backend.testsupport.DisposablePostgres {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MonthlyRecordRepository monthlyRecordRepository;

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    @Autowired
    private com.app.sme_health_backend.identity.repository.BusinessRepository businessRepository;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private UUID currentBusinessId;

    @BeforeEach
    void setUp() {
        currentBusinessId = UUID.randomUUID();
        when(authService.requirePermission(any(), any())).thenAnswer(inv ->
                new BusinessAccessContext(currentBusinessId, currentBusinessId, MembershipRole.OWNER)
        );
    }

    private UUID createTestUserWithProfile() {
        UUID userId = UUID.randomUUID();
        currentBusinessId = userId;
        businessRepository.save(new com.app.sme_health_backend.identity.entity.Business(userId, "ACTIVE"));
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setBusinessType("retail");
        profile.setLanguagePreference("en");
        profile.setWhatsappOptIn(false);
        profile.setCreatedAt(LocalDateTime.now());
        businessProfileRepository.save(profile);
        return userId;
    }

    @Test
    void manualMonthlyFiguresAreRefusedBecauseTheMonthIsDerived() throws Exception {
        UUID userId = createTestUserWithProfile();

        String payload = """
                {
                  "month": "2026-07",
                  "cashInflow": 500000,
                  "cashOutflow": 200000,
                  "revenue": 500000,
                  "cogs": null,
                  "operatingExpenses": 50000,
                  "cashBalanceEom": 300000,
                  "financingType": "none"
                }
                """;

        mockMvc.perform(post("/api/records/monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("monthly_figures_derived"));

        assertTrue(monthlyRecordRepository.findByUserIdAndMonth(userId, "2026-07").isEmpty(),
                "A refused manual month must not create a second source of truth");
    }

    @Test
    void shouldReturn404ForNonexistentRecordId() throws Exception {
        UUID nonexistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/records/monthly/id/{id}", nonexistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Monthly record not found with id: " + nonexistentId));
    }

    @Test
    void shouldReturn400ForMalformedRecordId() throws Exception {
        mockMvc.perform(get("/api/records/monthly/id/invalid-id-format"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid record ID format: invalid-id-format"));
    }
}
