package com.app.sme_health_backend.recurring.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.recurring.dto.*;
import com.app.sme_health_backend.recurring.service.RecurringRuleService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecurringRuleController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("RecurringRuleController WebMvc Tests")
class RecurringRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecurringRuleService ruleService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private BusinessAccessContext context;
    private UUID businessId;
    private UUID ruleId;
    private UUID accountId;
    private UUID occurrenceId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        ruleId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        occurrenceId = UUID.randomUUID();
        context = new BusinessAccessContext(UUID.randomUUID(), businessId, MembershipRole.OWNER);
    }

    @Test
    @DisplayName("POST /api/finance/recurring/rules returns 201 Created")
    void testCreateRuleReturns201() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenReturn(context);

        RecurringRuleResponse response = new RecurringRuleResponse(
                ruleId, "Office Rent", "Monthly rent", "OPERATING_EXPENSE_PAYMENT",
                accountId, "HBL Bank", "BANK", null, null,
                "50000.00", "PKR", "MONTHLY", "2026-11-01", null, "2026-11-01",
                "ACTIVE", 1L, Instant.now(), Instant.now()
        );
        when(ruleService.createRule(eq(context), any())).thenReturn(response);

        String json = """
                {
                    "name": "Office Rent",
                    "description": "Monthly rent",
                    "eventType": "OPERATING_EXPENSE_PAYMENT",
                    "accountId": "%s",
                    "amount": "50000.00",
                    "frequency": "MONTHLY",
                    "startDate": "2026-11-01"
                }
                """.formatted(accountId);

        mockMvc.perform(post("/api/finance/recurring/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ruleId.toString()))
                .andExpect(jsonPath("$.name").value("Office Rent"))
                .andExpect(jsonPath("$.amount").value("50000.00"));
    }

    @Test
    @DisplayName("GET /api/finance/recurring/rules returns 200 OK")
    void testListRulesReturns200() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ)))
                .thenReturn(context);

        RecurringRuleResponse response = new RecurringRuleResponse(
                ruleId, "Office Rent", "Monthly rent", "OPERATING_EXPENSE_PAYMENT",
                accountId, "HBL Bank", "BANK", null, null,
                "50000.00", "PKR", "MONTHLY", "2026-11-01", null, "2026-11-01",
                "ACTIVE", 1L, Instant.now(), Instant.now()
        );
        when(ruleService.listRules(eq(context), any(), any())).thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/finance/recurring/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ruleId.toString()))
                .andExpect(jsonPath("$.content[0].name").value("Office Rent"));
    }

    @Test
    @DisplayName("POST /api/finance/recurring/rules/{id}/pause returns 200 OK")
    void testPauseRuleReturns200() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenReturn(context);

        RecurringRuleResponse response = new RecurringRuleResponse(
                ruleId, "Office Rent", "Monthly rent", "OPERATING_EXPENSE_PAYMENT",
                accountId, "HBL Bank", "BANK", null, null,
                "50000.00", "PKR", "MONTHLY", "2026-11-01", null, "2026-11-01",
                "PAUSED", 2L, Instant.now(), Instant.now()
        );
        when(ruleService.pauseRule(eq(context), eq(ruleId))).thenReturn(response);

        mockMvc.perform(post("/api/finance/recurring/rules/{id}/pause", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    @DisplayName("GET /api/finance/recurring/rules/{id}/preview returns 200 OK with preview items")
    void testPreviewScheduleReturns200() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ)))
                .thenReturn(context);

        OccurrencePreviewItem item = new OccurrencePreviewItem(
                "2026-11-01", "50000.00", "PKR", "OPERATING_EXPENSE_PAYMENT"
        );
        when(ruleService.previewSchedule(eq(context), eq(ruleId), any())).thenReturn(List.of(item));

        mockMvc.perform(get("/api/finance/recurring/rules/{id}/preview", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scheduledDate").value("2026-11-01"))
                .andExpect(jsonPath("$[0].amount").value("50000.00"));
    }

    @Test
    @DisplayName("POST /api/finance/recurring/occurrences/generate-due returns 200 OK")
    void testGenerateDueReturns200() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenReturn(context);

        OccurrenceGenerateDueResponse response = new OccurrenceGenerateDueResponse(2, 1, "Generated 2 drafts across due recurring rules");
        when(ruleService.generateDueOccurrencesForBusiness(eq(context))).thenReturn(response);

        mockMvc.perform(post("/api/finance/recurring/occurrences/generate-due"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedCount").value(2));
    }

    @Test
    @DisplayName("POST /api/finance/recurring/occurrences/{id}/skip returns 200 OK")
    void testSkipOccurrenceReturns200() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenReturn(context);

        RecurringOccurrenceResponse response = new RecurringOccurrenceResponse(
                occurrenceId, ruleId, "Office Rent", 1L, "2026-11-01", "50000.00", "PKR",
                "OPERATING_EXPENSE_PAYMENT", accountId, "HBL Bank", null, null,
                "Monthly rent", "SKIPPED", null, null, Instant.now(), null,
                Instant.now(), "Landlord requested skip", null
        );
        when(ruleService.skipOccurrence(eq(context), eq(occurrenceId), any())).thenReturn(response);

        String json = """
                {
                    "reason": "Landlord requested skip"
                }
                """;

        mockMvc.perform(post("/api/finance/recurring/occurrences/{id}/skip", occurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.skippedReason").value("Landlord requested skip"));
    }

    @Test
    @DisplayName("Unauthorized role write attempt returns 403 Forbidden")
    void testUnauthorizedRoleReturns403() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenThrow(new AccessDeniedException("Insufficient business permissions"));

        String json = """
                {
                    "name": "Office Rent",
                    "eventType": "OPERATING_EXPENSE_PAYMENT",
                    "accountId": "%s",
                    "amount": "50000.00",
                    "frequency": "MONTHLY",
                    "startDate": "2026-11-01"
                }
                """.formatted(accountId);

        mockMvc.perform(post("/api/finance/recurring/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }
}
