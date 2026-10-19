package com.app.sme_health_backend.counterparty;

import com.app.sme_health_backend.counterparty.dto.CounterpartyCreateRequest;
import com.app.sme_health_backend.counterparty.dto.CounterpartyDetailResponse;
import com.app.sme_health_backend.counterparty.dto.CounterpartyListResponse;
import com.app.sme_health_backend.counterparty.dto.CounterpartySummaryItem;
import com.app.sme_health_backend.counterparty.dto.CounterpartyUpdateRequest;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CounterpartyController.class)
@Import(GlobalExceptionHandler.class)
class CounterpartyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CounterpartyService counterpartyService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private UUID businessId;
    private UUID userId;
    private UUID counterpartyId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        counterpartyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("OWNER can create a counterparty (201 Created)")
    void testOwnerCanCreateCounterparty() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        CounterpartyDetailResponse response = new CounterpartyDetailResponse(
                counterpartyId, businessId, "New Customer", "CUSTOMER", "ACTIVE",
                "test@example.com", "+923001234567", "Address", "REF-1", "Notes", 0L, Instant.now(), Instant.now()
        );
        when(counterpartyService.createCounterparty(eq(context), any())).thenReturn(response);

        mockMvc.perform(post("/api/finance/counterparties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "displayName": "New Customer",
                                    "type": "CUSTOMER",
                                    "email": "test@example.com",
                                    "phone": "+923001234567"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(counterpartyId.toString()))
                .andExpect(jsonPath("$.displayName").value("New Customer"))
                .andExpect(jsonPath("$.type").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("MANAGER cannot create a counterparty (403 Forbidden)")
    void testManagerCannotCreateCounterparty() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenThrow(new AccessDeniedException("Access denied: missing FINANCIAL_TRANSACTION_POST"));

        mockMvc.perform(post("/api/finance/counterparties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "displayName": "Forbidden Customer",
                                    "type": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("VIEWER cannot create a counterparty (403 Forbidden)")
    void testViewerCannotCreateCounterparty() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenThrow(new AccessDeniedException("Access denied: missing FINANCIAL_TRANSACTION_POST"));

        mockMvc.perform(post("/api/finance/counterparties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "displayName": "Forbidden Supplier",
                                    "type": "SUPPLIER"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("VIEWER can list counterparties (200 OK)")
    void testViewerCanListCounterparties() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.VIEWER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        CounterpartySummaryItem item = new CounterpartySummaryItem(
                counterpartyId, "Visible Customer", "CUSTOMER", "ACTIVE",
                null, null, null, null, 0L, Instant.now(), Instant.now()
        );
        CounterpartyListResponse listResponse = new CounterpartyListResponse(List.of(item), 0, 20, 1, 1);
        when(counterpartyService.listCounterparties(eq(context), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(listResponse);

        mockMvc.perform(get("/api/finance/counterparties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].displayName").value("Visible Customer"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Returns 400 Bad Request with field errors when validation fails")
    void testValidationFailureReturns400() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);
        when(counterpartyService.createCounterparty(eq(context), any()))
                .thenThrow(new CounterpartyValidationException(Map.of("displayName", "Display name is required")));

        mockMvc.perform(post("/api/finance/counterparties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.displayName").value("Display name is required"));
    }

    @Test
    @DisplayName("Returns 409 Conflict on version mismatch")
    void testConflictReturns409() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);
        when(counterpartyService.updateCounterparty(eq(context), eq(counterpartyId), any()))
                .thenThrow(new CounterpartyConflictException("Counterparty version mismatch: expected 1 but received 0"));

        mockMvc.perform(put("/api/finance/counterparties/" + counterpartyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "version": 0,
                                    "displayName": "Stale Update",
                                    "type": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Counterparty version mismatch: expected 1 but received 0"));
    }

    @Test
    @DisplayName("Returns 404 Not Found when counterparty does not exist or cross-business")
    void testNotFoundReturns404() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);
        when(counterpartyService.getCounterparty(eq(context), eq(counterpartyId)))
                .thenThrow(new ResourceNotFoundException("Counterparty not found"));

        mockMvc.perform(get("/api/finance/counterparties/" + counterpartyId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("OWNER can archive a counterparty (200 OK)")
    void testArchiveCounterparty() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        CounterpartyDetailResponse response = new CounterpartyDetailResponse(
                counterpartyId, businessId, "Archived Co", "SUPPLIER", "ARCHIVED",
                null, null, null, null, null, 1L, Instant.now(), Instant.now()
        );
        when(counterpartyService.archiveCounterparty(eq(context), eq(counterpartyId))).thenReturn(response);

        mockMvc.perform(post("/api/finance/counterparties/" + counterpartyId + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("OWNER can restore an archived counterparty (200 OK)")
    void testRestoreCounterparty() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        CounterpartyDetailResponse response = new CounterpartyDetailResponse(
                counterpartyId, businessId, "Restored Co", "SUPPLIER", "ACTIVE",
                null, null, null, null, null, 2L, Instant.now(), Instant.now()
        );
        when(counterpartyService.restoreCounterparty(eq(context), eq(counterpartyId))).thenReturn(response);

        mockMvc.perform(post("/api/finance/counterparties/" + counterpartyId + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
