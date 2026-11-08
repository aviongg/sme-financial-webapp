package com.app.sme_health_backend.fx;

import com.app.sme_health_backend.fx.controller.FxController;
import com.app.sme_health_backend.fx.dto.FxPreviewProvenance;
import com.app.sme_health_backend.fx.dto.FxPreviewRequest;
import com.app.sme_health_backend.fx.dto.FxPreviewResponse;
import com.app.sme_health_backend.fx.exception.FxValidationException;
import com.app.sme_health_backend.fx.model.CurrencyMetadata;
import com.app.sme_health_backend.fx.model.FxDirection;
import com.app.sme_health_backend.fx.model.RateSourceType;
import com.app.sme_health_backend.fx.service.CurrencyMetadataService;
import com.app.sme_health_backend.fx.service.FxCalculationService;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FxController.class)
@Import(GlobalExceptionHandler.class)
class FxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private FxCalculationService calculationService;

    @MockitoBean
    private CurrencyMetadataService currencyMetadataService;

    @MockitoBean
    private BusinessAuthorizationService authorizationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID businessId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BusinessAccessContext context = new BusinessAccessContext(
                userId, businessId, MembershipRole.OWNER
        );
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenReturn(context);
    }

    @Test
    @DisplayName("GET /api/finance/fx/currencies returns 200 OK with currency metadata list")
    void getCurrenciesReturns200() throws Exception {
        when(currencyMetadataService.getAllCurrencies()).thenReturn(List.of(
                new CurrencyMetadata("PKR", "Pakistani Rupee", "₨", 2, RoundingMode.HALF_UP, true),
                new CurrencyMetadata("USD", "US Dollar", "$", 2, RoundingMode.HALF_UP, true),
                new CurrencyMetadata("JPY", "Japanese Yen", "¥", 0, RoundingMode.HALF_UP, true)
        ));

        mockMvc.perform(get("/api/finance/fx/currencies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("PKR"))
                .andExpect(jsonPath("$[0].minorUnit").value(2))
                .andExpect(jsonPath("$[1].code").value("USD"))
                .andExpect(jsonPath("$[2].code").value("JPY"))
                .andExpect(jsonPath("$[2].minorUnit").value(0));
    }

    @Test
    @DisplayName("POST /api/finance/fx/preview returns 200 OK with calculation result")
    void previewConversionReturns200() throws Exception {
        FxPreviewRequest request = new FxPreviewRequest(
                "USD",
                "PKR",
                new BigDecimal("278.50"),
                FxDirection.OUTGOING,
                new BigDecimal("100.00"),
                "USD",
                new BigDecimal("150.00"),
                "PKR",
                BigDecimal.ZERO,
                null,
                RateSourceType.MANUAL_USER_RATE
        );

        FxPreviewResponse response = new FxPreviewResponse(
                "USD",
                "PKR",
                new BigDecimal("278.50"),
                new BigDecimal("0.00359066"),
                "1 USD = 278.50 PKR",
                RateSourceType.MANUAL_USER_RATE,
                FxDirection.OUTGOING,
                new BigDecimal("100.00"),
                "USD",
                "PKR",
                new BigDecimal("27850.00"),
                new BigDecimal("150.00"),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                "PKR",
                new BigDecimal("27850.00"),
                new BigDecimal("28000.00"),
                new BigDecimal("280.000000"),
                null,
                new FxPreviewProvenance("PURE_JAVA_DETERMINISTIC_PREVIEW", "Preview only", Instant.now().toString())
        );

        when(calculationService.previewConversion(any())).thenReturn(response);

        mockMvc.perform(post("/api/finance/fx/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.quoteCurrency").value("PKR"))
                .andExpect(jsonPath("$.grossSettlementAmount").value(27850.00))
                .andExpect(jsonPath("$.totalSettlementDebited").value(28000.00))
                .andExpect(jsonPath("$.provenance.calculationType").value("PURE_JAVA_DETERMINISTIC_PREVIEW"));
    }

    @Test
    @DisplayName("POST /api/finance/fx/preview returns 400 Bad Request on validation failure")
    void previewConversionReturns400OnValidationFailure() throws Exception {
        when(calculationService.previewConversion(any()))
                .thenThrow(new FxValidationException("FX Preview request validation failed: rate must be strictly positive",
                        List.of("rate must be strictly positive")));

        FxPreviewRequest invalidRequest = new FxPreviewRequest(
                "USD",
                "PKR",
                new BigDecimal("-5.0"),
                FxDirection.OUTGOING,
                new BigDecimal("100.00"),
                "USD",
                BigDecimal.ZERO,
                "PKR",
                BigDecimal.ZERO,
                null,
                RateSourceType.MANUAL_USER_RATE
        );

        mockMvc.perform(post("/api/finance/fx/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.errors[0]").value("rate must be strictly positive"));
    }

    @Test
    @DisplayName("Endpoints return 403 Forbidden when FINANCIAL_DATA_READ is denied")
    void endpointsReturn403WhenPermissionDenied() throws Exception {
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenThrow(new AccessDeniedException("Role SALESPERSON does not have permission: FINANCIAL_DATA_READ"));

        mockMvc.perform(get("/api/finance/fx/currencies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
