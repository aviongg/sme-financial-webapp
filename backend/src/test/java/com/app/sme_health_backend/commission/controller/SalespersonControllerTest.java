package com.app.sme_health_backend.commission.controller;

import com.app.sme_health_backend.commission.dto.*;
import com.app.sme_health_backend.commission.exception.SalespersonConflictException;
import com.app.sme_health_backend.commission.exception.SalespersonNotFoundException;
import com.app.sme_health_backend.commission.model.AssignmentStatus;
import com.app.sme_health_backend.commission.model.SalespersonStatus;
import com.app.sme_health_backend.commission.service.SalespersonService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SalespersonController.class)
@Import(GlobalExceptionHandler.class)
class SalespersonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private SalespersonService salespersonService;

    @MockitoBean
    private BusinessAuthorizationService authorizationService;

    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID salespersonId = UUID.randomUUID();
    private final UUID counterpartyId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();

    private BusinessAccessContext ownerContext;
    private BusinessAccessContext salespersonContext;

    @BeforeEach
    void setUp() {
        ownerContext = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        salespersonContext = new BusinessAccessContext(userId, businessId, MembershipRole.SALESPERSON);
    }

    @Test
    @DisplayName("Owner creates salesperson successfully (201 Created)")
    void createSalesperson_Success() throws Exception {
        SalespersonCreateRequest request = new SalespersonCreateRequest(
                "Tariq Mahmood", "tariq@example.com", "+923001234567", null, "Notes"
        );
        SalespersonResponse response = new SalespersonResponse(
                salespersonId, businessId, null, "Tariq Mahmood", "tariq@example.com",
                "+923001234567", SalespersonStatus.ACTIVE, "Notes", 0, Instant.now(), Instant.now()
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.SALESPERSON_MANAGE)))
                .thenReturn(ownerContext);
        when(salespersonService.createSalesperson(eq(ownerContext), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/finance/salespersons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(salespersonId.toString()))
                .andExpect(jsonPath("$.displayName").value("Tariq Mahmood"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Salesperson role is forbidden from creating salespersons (403 Forbidden)")
    void createSalesperson_ForbiddenForSalespersonRole() throws Exception {
        SalespersonCreateRequest request = new SalespersonCreateRequest(
                "Tariq Mahmood", "tariq@example.com", null, null, null
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.SALESPERSON_MANAGE)))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(post("/api/finance/salespersons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Salesperson role is forbidden from listing all salespersons (403 Forbidden)")
    void listSalespersons_ForbiddenForSalespersonRole() throws Exception {
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.SALESPERSON_MANAGE)))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/api/finance/salespersons"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Duplicate email returns 409 Conflict")
    void createSalesperson_DuplicateEmail() throws Exception {
        SalespersonCreateRequest request = new SalespersonCreateRequest(
                "Tariq Mahmood", "tariq@example.com", null, null, null
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.SALESPERSON_MANAGE)))
                .thenReturn(ownerContext);
        when(salespersonService.createSalesperson(eq(ownerContext), any()))
                .thenThrow(new SalespersonConflictException("Salesperson with email 'tariq@example.com' already exists"));

        mockMvc.perform(post("/api/finance/salespersons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"));
    }

    @Test
    @DisplayName("Salesperson can access own profile via /me (200 OK)")
    void getCurrentSalesperson_Success() throws Exception {
        SalespersonSelfResponse response = new SalespersonSelfResponse(
                salespersonId, businessId, userId, "Sales Rep", "rep@example.com",
                null, SalespersonStatus.ACTIVE, null, List.of(), Instant.now(), Instant.now()
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(salespersonContext);
        when(salespersonService.getSalespersonForCurrentUser(eq(salespersonContext)))
                .thenReturn(response);

        mockMvc.perform(get("/api/finance/salespersons/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(salespersonId.toString()))
                .andExpect(jsonPath("$.displayName").value("Sales Rep"))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("Suspended salesperson calling /me returns 403 Forbidden")
    void getCurrentSalesperson_SuspendedDenied() throws Exception {
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.COMMISSION_READ)))
                .thenReturn(salespersonContext);
        when(salespersonService.getSalespersonForCurrentUser(eq(salespersonContext)))
                .thenThrow(new AccessDeniedException("Salesperson account is inactive or suspended"));

        mockMvc.perform(get("/api/finance/salespersons/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Owner can assign customer to salesperson (201 Created)")
    void assignCustomer_Success() throws Exception {
        SalesAssignmentCreateRequest request = new SalesAssignmentCreateRequest(
                counterpartyId, LocalDate.of(2026, 1, 1), null, "VIP Account"
        );
        SalesAssignmentResponse response = new SalesAssignmentResponse(
                assignmentId, businessId, salespersonId, "Tariq Mahmood",
                counterpartyId, "Acme Mills Ltd", AssignmentStatus.ACTIVE,
                LocalDate.of(2026, 1, 1), null, userId, "VIP Account", Instant.now()
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.SALESPERSON_MANAGE)))
                .thenReturn(ownerContext);
        when(salespersonService.assignCustomer(eq(ownerContext), eq(salespersonId), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/finance/salespersons/{id}/assignments", salespersonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(assignmentId.toString()))
                .andExpect(jsonPath("$.counterpartyName").value("Acme Mills Ltd"));
    }

    @Test
    @DisplayName("Terminating assignment returns 200 OK with INACTIVE status")
    void terminateAssignment_Success() throws Exception {
        SalesAssignmentResponse response = new SalesAssignmentResponse(
                assignmentId, businessId, salespersonId, "Tariq Mahmood",
                counterpartyId, "Acme Mills Ltd", AssignmentStatus.INACTIVE,
                LocalDate.of(2026, 1, 1), LocalDate.now(), userId, null, Instant.now()
        );

        when(authorizationService.requirePermission(any(), eq(BusinessPermission.SALESPERSON_MANAGE)))
                .thenReturn(ownerContext);
        when(salespersonService.terminateAssignment(eq(ownerContext), eq(salespersonId), eq(assignmentId)))
                .thenReturn(response);

        mockMvc.perform(delete("/api/finance/salespersons/{id}/assignments/{assignmentId}", salespersonId, assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    @DisplayName("Non-existent salesperson returns 404 Not Found")
    void getSalesperson_NotFound() throws Exception {
        when(authorizationService.requirePermission(any(), eq(BusinessPermission.SALESPERSON_MANAGE)))
                .thenReturn(ownerContext);
        when(salespersonService.getSalesperson(eq(ownerContext), eq(salespersonId)))
                .thenThrow(new SalespersonNotFoundException("Salesperson not found"));

        mockMvc.perform(get("/api/finance/salespersons/{id}", salespersonId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }
}
