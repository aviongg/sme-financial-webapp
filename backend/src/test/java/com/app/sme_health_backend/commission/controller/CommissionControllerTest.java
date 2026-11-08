package com.app.sme_health_backend.commission.controller;

import com.app.sme_health_backend.commission.dto.*;
import com.app.sme_health_backend.commission.exception.CommissionEarningConflictException;
import com.app.sme_health_backend.commission.exception.CommissionRuleNotFoundException;
import com.app.sme_health_backend.commission.model.*;
import com.app.sme_health_backend.commission.service.CommissionEarningService;
import com.app.sme_health_backend.commission.service.CommissionRuleService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import tools.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommissionController.class)
@Import(GlobalExceptionHandler.class)
class CommissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CommissionRuleService ruleService;

    @MockitoBean
    private CommissionEarningService earningService;

    @MockitoBean
    private com.app.sme_health_backend.commission.service.CommissionSettlementService settlementService;

    @MockitoBean
    private BusinessAuthorizationService authorizationService;

    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID salespersonId = UUID.randomUUID();
    private final UUID counterpartyId = UUID.randomUUID();
    private final UUID ruleId = UUID.randomUUID();
    private final UUID earningId = UUID.randomUUID();
    private final UUID allocationId = UUID.randomUUID();

    private BusinessAccessContext ownerContext;
    private BusinessAccessContext salespersonContext;

    @BeforeEach
    void setUp() {
        ownerContext = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        salespersonContext = new BusinessAccessContext(userId, businessId, MembershipRole.SALESPERSON);
    }

    @Test
    @DisplayName("Owner creates commission rule successfully (201 Created)")
    void createRule_Success() throws Exception {
        CommissionRuleCreateRequest request = new CommissionRuleCreateRequest(
                salespersonId,
                "Standard 5% Collection Rule",
                CalculationType.PERCENTAGE,
                EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.00"),
                null,
                "PKR",
                LocalDate.of(2026, 1, 1),
                null
        );

        CommissionRuleResponse response = new CommissionRuleResponse(
                ruleId, businessId, salespersonId, "Tariq Mahmood", "Standard 5% Collection Rule",
                CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.0000"), null, "PKR",
                LocalDate.of(2026, 1, 1), null, CommissionRuleStatus.ACTIVE,
                Instant.now(), Instant.now()
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_MANAGE)))
                .thenReturn(ownerContext);
        when(ruleService.createRule(eq(ownerContext), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/finance/commissions/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ruleId.toString()))
                .andExpect(jsonPath("$.ruleName").value("Standard 5% Collection Rule"))
                .andExpect(jsonPath("$.calculationType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Salesperson is forbidden from creating commission rules (403 Forbidden)")
    void createRule_ForbiddenForSalesperson() throws Exception {
        CommissionRuleCreateRequest request = new CommissionRuleCreateRequest(
                null, "Sneaky Rule", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("10.00"), null, "PKR", LocalDate.now(), null
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_MANAGE)))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(post("/api/finance/commissions/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Listing rules returns 200 OK")
    void listRules_Success() throws Exception {
        CommissionRuleResponse rule = new CommissionRuleResponse(
                ruleId, businessId, null, null, "Default Rule",
                CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("3.0000"), null, "PKR",
                LocalDate.of(2026, 1, 1), null, CommissionRuleStatus.ACTIVE,
                Instant.now(), Instant.now()
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(ownerContext);
        when(ruleService.listRules(eq(ownerContext), any()))
                .thenReturn(List.of(rule));

        mockMvc.perform(get("/api/finance/commissions/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ruleName").value("Default Rule"));
    }

    @Test
    @DisplayName("Get rule by ID returns 200 OK or 404 when not found")
    void getRule_SuccessAndNotFound() throws Exception {
        CommissionRuleResponse rule = new CommissionRuleResponse(
                ruleId, businessId, null, null, "Default Rule",
                CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("3.0000"), null, "PKR",
                LocalDate.of(2026, 1, 1), null, CommissionRuleStatus.ACTIVE,
                Instant.now(), Instant.now()
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(ownerContext);
        when(ruleService.getRule(eq(ownerContext), eq(ruleId))).thenReturn(rule);

        mockMvc.perform(get("/api/finance/commissions/rules/{id}", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ruleId.toString()));

        UUID missingId = UUID.randomUUID();
        when(ruleService.getRule(eq(ownerContext), eq(missingId)))
                .thenThrow(new CommissionRuleNotFoundException("Rule not found"));

        mockMvc.perform(get("/api/finance/commissions/rules/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    @DisplayName("Updating rule status returns 200 OK")
    void updateRuleStatus_Success() throws Exception {
        CommissionRuleStatusUpdateRequest request = new CommissionRuleStatusUpdateRequest(CommissionRuleStatus.ARCHIVED);
        CommissionRuleResponse rule = new CommissionRuleResponse(
                ruleId, businessId, null, null, "Default Rule",
                CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("3.0000"), null, "PKR",
                LocalDate.of(2026, 1, 1), null, CommissionRuleStatus.ARCHIVED,
                Instant.now(), Instant.now()
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_MANAGE)))
                .thenReturn(ownerContext);
        when(ruleService.updateRuleStatus(eq(ownerContext), eq(ruleId), eq(CommissionRuleStatus.ARCHIVED)))
                .thenReturn(rule);

        mockMvc.perform(patch("/api/finance/commissions/rules/{id}/status", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("Discover eligible collections returns 200 OK")
    void listEligibleCollections_Success() throws Exception {
        EligibleCollectionResponse item = new EligibleCollectionResponse(
                allocationId,
                UUID.randomUUID(),
                counterpartyId,
                "Acme Mills Ltd",
                salespersonId,
                "Tariq Mahmood",
                new BigDecimal("100000.00"),
                LocalDate.of(2026, 3, 1),
                "PKR",
                ruleId,
                "Standard 5% Collection Rule",
                CalculationType.PERCENTAGE,
                new BigDecimal("5.0000"),
                new BigDecimal("5000.00"),
                false,
                null
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(ownerContext);
        when(earningService.discoverEligibleCollections(eq(ownerContext)))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/finance/commissions/eligible-collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].counterpartyName").value("Acme Mills Ltd"))
                .andExpect(jsonPath("$[0].estimatedCommission").value(5000.00));
    }

    @Test
    @DisplayName("Preview calculation returns 200 OK")
    void previewCalculation_Success() throws Exception {
        CommissionCalculationRequest request = new CommissionCalculationRequest(
                CommissionSourceType.OBLIGATION_ALLOCATION,
                allocationId.toString(),
                counterpartyId,
                salespersonId,
                new BigDecimal("50000.00"),
                LocalDate.of(2026, 3, 1),
                "PKR",
                "Preview calculation"
        );

        CommissionCalculationPreviewResponse response = new CommissionCalculationPreviewResponse(
                salespersonId,
                "Tariq Mahmood",
                counterpartyId,
                "Acme Mills Ltd",
                ruleId,
                "Standard 5% Collection Rule",
                CalculationType.PERCENTAGE,
                new BigDecimal("5.0000"),
                null,
                new BigDecimal("50000.00"),
                new BigDecimal("2500.00"),
                "PKR",
                Instant.now(),
                true,
                null
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(ownerContext);
        when(earningService.previewCalculation(eq(ownerContext), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/finance/commissions/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculatedCommission").value(2500.00));
    }

    @Test
    @DisplayName("Owner records commission earning successfully (201 Created)")
    void recordEarning_Success() throws Exception {
        CommissionEarningCreateRequest request = new CommissionEarningCreateRequest(
                CommissionSourceType.OBLIGATION_ALLOCATION,
                allocationId.toString(),
                salespersonId,
                ruleId,
                "Earned on payment allocation"
        );

        CommissionEarningResponse response = new CommissionEarningResponse(
                earningId, businessId, salespersonId, "Tariq Mahmood",
                ruleId, "Standard 5% Collection Rule",
                counterpartyId, "Acme Mills Ltd",
                CommissionSourceType.OBLIGATION_ALLOCATION, allocationId.toString(),
                "ALLOCATION:" + allocationId,
                new BigDecimal("100000.00"),
                new BigDecimal("5.0000"),
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                new BigDecimal("5000.00"),
                "PKR",
                CommissionEarningStatus.EARNED,
                null, null, null,
                "Earned on payment allocation",
                Instant.now(), Instant.now()
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_MANAGE)))
                .thenReturn(ownerContext);
        when(earningService.recordEarning(eq(ownerContext), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/finance/commissions/earnings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(earningId.toString()))
                .andExpect(jsonPath("$.salespersonName").value("Tariq Mahmood"))
                .andExpect(jsonPath("$.commissionAmount").value(5000.00))
                .andExpect(jsonPath("$.status").value("EARNED"));
    }

    @Test
    @DisplayName("Duplicate earning recognition returns 409 Conflict")
    void recordEarning_DuplicateConflict() throws Exception {
        CommissionEarningCreateRequest request = new CommissionEarningCreateRequest(
                CommissionSourceType.OBLIGATION_ALLOCATION,
                allocationId.toString(),
                salespersonId,
                ruleId,
                null
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_MANAGE)))
                .thenReturn(ownerContext);
        when(earningService.recordEarning(eq(ownerContext), any()))
                .thenThrow(new CommissionEarningConflictException("Commission already earned for source ALLOCATION:" + allocationId));

        mockMvc.perform(post("/api/finance/commissions/earnings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"));
    }

    @Test
    @DisplayName("Salesperson can access their own earnings via /my-earnings (200 OK)")
    void getMyEarnings_Success() throws Exception {
        CommissionEarningResponse response = new CommissionEarningResponse(
                earningId, businessId, salespersonId, "Sales Rep",
                ruleId, "Standard 5% Collection Rule",
                counterpartyId, "Acme Mills Ltd",
                CommissionSourceType.OBLIGATION_ALLOCATION, allocationId.toString(),
                "ALLOCATION:" + allocationId,
                new BigDecimal("100000.00"),
                new BigDecimal("5.0000"),
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                new BigDecimal("5000.00"),
                "PKR",
                CommissionEarningStatus.EARNED,
                null, null, null,
                null,
                Instant.now(), Instant.now()
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(salespersonContext);
        when(earningService.listEarnings(eq(salespersonContext), eq(null), any()))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/finance/commissions/my-earnings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].commissionAmount").value(5000.00));
    }

    @Test
    @DisplayName("Get commission summary returns 200 OK")
    void getSummary_Success() throws Exception {
        CommissionSummaryResponse summary = new CommissionSummaryResponse(
                new BigDecimal("50000.00"),
                new BigDecimal("40000.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("15000.00"),
                new BigDecimal("30000.00"),
                new BigDecimal("20000.00"),
                12L,
                8L,
                5L,
                2L,
                "PKR"
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(ownerContext);
        when(earningService.getSummary(eq(ownerContext), any()))
                .thenReturn(summary);

        mockMvc.perform(get("/api/finance/commissions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEarnedAmount").value(50000.00))
                .andExpect(jsonPath("$.totalPaidAmount").value(20000.00))
                .andExpect(jsonPath("$.totalPendingAmount").value(30000.00))
                .andExpect(jsonPath("$.earningsCount").value(12));
    }
}
