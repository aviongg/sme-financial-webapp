package com.app.sme_health_backend.funding;

import com.app.sme_health_backend.funding.controller.FundingScenarioController;
import com.app.sme_health_backend.funding.dto.*;
import com.app.sme_health_backend.funding.exception.FundingScenarioValidationException;
import com.app.sme_health_backend.funding.service.FundingScenarioService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.shared.exception.ActiveBusinessRequiredException;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FundingScenarioController.class)
@Import(GlobalExceptionHandler.class)
class FundingScenarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private FundingScenarioService scenarioService;

    @MockitoBean
    private BusinessAuthorizationService authorizationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID businessId = UUID.randomUUID();

    private FundingScenarioCompareRequest validRequest;
    private FundingScenarioCompareResponse sampleResponse;

    @BeforeEach
    void setUp() {
        LocalDate asOf = LocalDate.of(2026, 11, 1);
        validRequest = new FundingScenarioCompareRequest(
                "PKR",
                asOf.toString(),
                30,
                new OpeningLiquidityRequest(OpeningLiquiditySource.MANUAL_ASSUMPTION, new BigDecimal("100000.00"), "Initial liquidity"),
                new ScenarioDefinition("Base Plan", "Base description", List.of(
                        new PlannedCashEventInput("evt-1", asOf.plusDays(5).toString(), CashFlowDirection.OUTFLOW, new BigDecimal("40000.00"), "Inventory Purchase", "SUPPLIES")
                )),
                null
        );

        ScenarioResult baselineResult = new ScenarioResult(
                "Base Plan",
                "0.00",
                "40000.00",
                "-40000.00",
                "60000.00",
                "0.00",
                null,
                null,
                null,
                0,
                false,
                true,
                List.of()
        );

        sampleResponse = new FundingScenarioCompareResponse(
                "PKR",
                asOf,
                asOf,
                asOf.plusDays(30),
                30,
                new OpeningLiquiditySummary("MANUAL_ASSUMPTION", "100000.00", "Initial liquidity"),
                baselineResult,
                null,
                null,
                new FundingScenarioProvenance("MANUAL_ASSUMPTION", "HYPOTHETICAL_SIMULATION", "2026-11-06T12:00:00Z", "funding-scenario-v1"),
                List.of(),
                List.of("UNPOSTED_SIMULATION")
        );
    }

    @ParameterizedTest
    @EnumSource(value = MembershipRole.class, names = {"OWNER", "ACCOUNTANT", "MANAGER", "VIEWER"})
    @DisplayName("RBAC matrix: all 4 business roles can execute scenario comparison")
    void allRolesPermitted(MembershipRole role) throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, role);
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenReturn(context);
        when(scenarioService.compareScenarios(eq(context), any()))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/finance/funding/scenarios/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PKR"))
                .andExpect(jsonPath("$.horizonDays").value(30))
                .andExpect(jsonPath("$.openingLiquidity.source").value("MANUAL_ASSUMPTION"))
                .andExpect(jsonPath("$.openingLiquidity.amount").value("100000.00"))
                .andExpect(jsonPath("$.baseline.scenarioName").value("Base Plan"))
                .andExpect(jsonPath("$.baseline.closingProjectedLiquidity").value("60000.00"))
                .andExpect(jsonPath("$.baseline.hasDeficit").value(false));
    }

    @Test
    @DisplayName("Unauthenticated or unauthorized request throws AccessDeniedException returning 403")
    void unauthorizedAccessDenied() throws Exception {
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenThrow(new AccessDeniedException("Access denied: active business permission required"));

        mockMvc.perform(post("/api/finance/funding/scenarios/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Missing active business context throws ActiveBusinessRequiredException returning 409 Conflict")
    void missingActiveBusinessThrowsConflict() throws Exception {
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenThrow(new ActiveBusinessRequiredException());

        mockMvc.perform(post("/api/finance/funding/scenarios/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("active_business_required"));
    }

    @Test
    @DisplayName("Validation exception in service returns HTTP 400 with field errors")
    void validationExceptionHandledWithBadRequest() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenReturn(context);
        when(scenarioService.compareScenarios(eq(context), any()))
                .thenThrow(new FundingScenarioValidationException("currency", "Unsupported currency 'USD'; scenario engine currently supports PKR only"));

        mockMvc.perform(post("/api/finance/funding/scenarios/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.currency").value("Unsupported currency 'USD'; scenario engine currently supports PKR only"));
    }
}
