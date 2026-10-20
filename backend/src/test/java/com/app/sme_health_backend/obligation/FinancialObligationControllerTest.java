package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.aging.DirectionAgingSummary;
import com.app.sme_health_backend.obligation.aging.ObligationAgingResult;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.obligation.dto.ObligationCreateRequest;
import com.app.sme_health_backend.obligation.dto.ObligationDetailResponse;
import com.app.sme_health_backend.obligation.dto.ObligationListResponse;
import com.app.sme_health_backend.obligation.dto.ObligationSettlementRequest;
import com.app.sme_health_backend.obligation.dto.ObligationSettlementResponse;
import com.app.sme_health_backend.obligation.dto.ObligationSummaryResponse;
import com.app.sme_health_backend.obligation.dto.ObligationSummaryTotalsResponse;
import com.app.sme_health_backend.obligation.dto.ObligationVoidRequest;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinancialObligationController.class)
@Import(GlobalExceptionHandler.class)
class FinancialObligationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialObligationService obligationService;

    @MockitoBean
    private PaymentPlanService paymentPlanService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private UUID businessId;
    private UUID userId;
    private UUID obligationId;
    private UUID counterpartyId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        obligationId = UUID.randomUUID();
        counterpartyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("OWNER can create an obligation (201 Created)")
    void testCreateObligation_Success() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        ObligationDetailResponse response = new ObligationDetailResponse(
                obligationId, businessId, counterpartyId, "Test Customer", "CUSTOMER",
                "RECEIVABLE", "MANUAL_DECLARATION", null,
                "INV-100", LocalDate.now(), LocalDate.now().plusDays(15),
                "100000.00", "0.00", "100000.00", "PKR",
                "OPEN", "UNPAID", "NOT_YET_DUE", 0, "Sales invoice", null, null,
                List.of(), Instant.now().toString()
        );

        when(obligationService.createObligation(eq(context), any(ObligationCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/finance/obligations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "counterpartyId": "%s",
                                    "direction": "RECEIVABLE",
                                    "amount": "100000.00",
                                    "issueDate": "%s",
                                    "dueDate": "%s",
                                    "referenceNumber": "INV-100",
                                    "description": "Sales invoice"
                                }
                                """.formatted(counterpartyId, LocalDate.now(), LocalDate.now().plusDays(15))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(obligationId.toString()))
                .andExpect(jsonPath("$.counterpartyId").value(counterpartyId.toString()))
                .andExpect(jsonPath("$.direction").value("RECEIVABLE"))
                .andExpect(jsonPath("$.originalAmount").value("100000.00"))
                .andExpect(jsonPath("$.outstandingAmount").value("100000.00"))
                .andExpect(jsonPath("$.currency").value("PKR"));
    }

    @Test
    @DisplayName("MANAGER cannot create an obligation (403 Forbidden)")
    void testCreateObligation_Forbidden() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenThrow(new AccessDeniedException("Access denied: missing FINANCIAL_TRANSACTION_POST"));

        mockMvc.perform(post("/api/finance/obligations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "counterpartyId": "%s",
                                    "direction": "RECEIVABLE",
                                    "amount": "100000.00",
                                    "issueDate": "%s"
                                }
                                """.formatted(counterpartyId, LocalDate.now())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("VIEWER can list obligations (200 OK)")
    void testListObligations_Success() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.VIEWER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        ObligationSummaryResponse item = new ObligationSummaryResponse(
                obligationId, businessId, counterpartyId, "Test Customer", "CUSTOMER",
                "RECEIVABLE", "MANUAL_DECLARATION", null,
                "INV-100", LocalDate.now(), LocalDate.now().plusDays(15),
                "100000.00", "0.00", "100000.00", "PKR",
                "OPEN", "UNPAID", "NOT_YET_DUE", 0, null, Instant.now().toString()
        );

        when(obligationService.listObligations(eq(context), eq("RECEIVABLE"), eq(null), eq("OPEN"), eq(null), eq(null), any()))
                .thenReturn(new ObligationListResponse(List.of(item), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/finance/obligations")
                        .param("direction", "RECEIVABLE")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(obligationId.toString()))
                .andExpect(jsonPath("$.items[0].originalAmount").value("100000.00"));
    }

    @Test
    @DisplayName("Get obligation detail returns 404 when not found")
    void testGetObligation_NotFound() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        when(obligationService.getObligation(eq(context), eq(obligationId)))
                .thenThrow(new ResourceNotFoundException("Obligation not found"));

        mockMvc.perform(get("/api/finance/obligations/" + obligationId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("OWNER can void an obligation (200 OK)")
    void testVoidObligation_Success() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        ObligationDetailResponse response = new ObligationDetailResponse(
                obligationId, businessId, counterpartyId, "Customer", "CUSTOMER",
                "RECEIVABLE", "MANUAL_DECLARATION", null,
                null, LocalDate.now(), null,
                "50000.00", "0.00", "0.00", "PKR",
                "VOIDED", "UNPAID", "UNKNOWN_DUE_DATE", null, "Voided: mistake", LocalDate.now(), "Entered wrong amount",
                List.of(), Instant.now().toString()
        );

        when(obligationService.voidObligation(eq(context), eq(obligationId), any(ObligationVoidRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/finance/obligations/" + obligationId + "/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reason": "Entered wrong amount"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"));
    }

    @Test
    @DisplayName("Settlement requires Idempotency-Key header (400 Bad Request if missing)")
    void testRecordSettlement_MissingIdempotencyKey() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        when(obligationService.recordSettlement(eq(context), eq(obligationId), eq(null), any(ObligationSettlementRequest.class)))
                .thenThrow(new ObligationValidationException(
                        "idempotencyKey", "Idempotency-Key header is required for settlement operations"
                ));

        mockMvc.perform(post("/api/finance/obligations/" + obligationId + "/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "accountId": "%s",
                                    "amount": "40000.00",
                                    "settlementDate": "%s"
                                }
                                """.formatted(UUID.randomUUID(), LocalDate.now())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.idempotencyKey").value("Idempotency-Key header is required for settlement operations"));
    }

    @Test
    @DisplayName("Settlement records successfully with 201 Created")
    void testRecordSettlement_Success() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        UUID accountId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        UUID allocId = UUID.randomUUID();

        ObligationSettlementResponse response = new ObligationSettlementResponse(
                allocId, obligationId, txId, "40000.00", LocalDate.now(), "40000.00", "60000.00", "PARTIALLY_PAID", Instant.now().toString()
        );

        when(obligationService.recordSettlement(eq(context), eq(obligationId), eq("test-idemp-1"), any(ObligationSettlementRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/finance/obligations/" + obligationId + "/settlements")
                        .header("Idempotency-Key", "test-idemp-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "accountId": "%s",
                                    "amount": "40000.00",
                                    "settlementDate": "%s",
                                    "reference": "CHK-001",
                                    "description": "Partial payment"
                                }
                                """.formatted(accountId, LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocationId").value(allocId.toString()))
                .andExpect(jsonPath("$.amount").value("40000.00"))
                .andExpect(jsonPath("$.resultingAccountBalance").value("40000.00"))
                .andExpect(jsonPath("$.remainingOutstandingAmount").value("60000.00"))
                .andExpect(jsonPath("$.settlementStatus").value("PARTIALLY_PAID"));
    }

    @Test
    @DisplayName("Summary totals endpoint returns 200 OK")
    void testGetSummaryTotals_Success() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        ObligationSummaryTotalsResponse summary = new ObligationSummaryTotalsResponse(
                "PKR", LocalDate.now(),
                "100000.00", "80000.00",
                "60000.00", "50000.00",
                "20000.00", "10000.00",
                3L, 2L,
                "40000.00", "40000.00"
        );

        when(obligationService.getTotalsSummary(eq(context), eq(null))).thenReturn(summary);

        mockMvc.perform(get("/api/finance/obligations/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecordedReceivables").value("100000.00"))
                .andExpect(jsonPath("$.outstandingReceivables").value("60000.00"))
                .andExpect(jsonPath("$.totalRecordedPayables").value("80000.00"))
                .andExpect(jsonPath("$.outstandingPayables").value("50000.00"));
    }

    @Test
    @DisplayName("Aging endpoint returns 200 OK with 7 canonical buckets")
    void testGetAging_Success() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        DirectionAgingSummary recSummary = new DirectionAgingSummary(
                ObligationDirection.RECEIVABLE, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, Map.of(), 0, 0, 0
        );
        DirectionAgingSummary paySummary = new DirectionAgingSummary(
                ObligationDirection.PAYABLE, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, Map.of(), 0, 0, 0
        );
        ObligationAgingResult aging = new ObligationAgingResult(
                LocalDate.now(), "PKR", recSummary, paySummary, List.of()
        );

        when(obligationService.getAging(eq(context), eq(null))).thenReturn(aging);

        mockMvc.perform(get("/api/finance/obligations/aging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PKR"));
    }
}
