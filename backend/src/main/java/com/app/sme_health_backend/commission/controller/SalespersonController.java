package com.app.sme_health_backend.commission.controller;

import com.app.sme_health_backend.commission.dto.*;
import com.app.sme_health_backend.commission.model.SalespersonStatus;
import com.app.sme_health_backend.commission.service.SalespersonService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/salespersons")
public class SalespersonController {

    private final SalespersonService salespersonService;
    private final BusinessAuthorizationService authorizationService;

    public SalespersonController(
            SalespersonService salespersonService,
            BusinessAuthorizationService authorizationService
    ) {
        this.salespersonService = salespersonService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    public ResponseEntity<SalespersonResponse> createSalesperson(
            @Valid @RequestBody SalespersonCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.SALESPERSON_MANAGE
        );
        SalespersonResponse response = salespersonService.createSalesperson(context, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SalespersonResponse>> listSalespersons(
            @RequestParam(required = false) SalespersonStatus status,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.SALESPERSON_MANAGE
        );
        List<SalespersonResponse> response = salespersonService.listSalespersons(context, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<SalespersonSelfResponse> getCurrentSalesperson(
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        SalespersonSelfResponse response = salespersonService.getSalespersonForCurrentUser(context);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-assignments")
    public ResponseEntity<List<SalesAssignmentResponse>> getCurrentUserAssignments(
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        List<SalesAssignmentResponse> response = salespersonService.listCurrentUserAssignments(context);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalespersonResponse> getSalesperson(
            @PathVariable UUID id,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.SALESPERSON_MANAGE
        );
        SalespersonResponse response = salespersonService.getSalesperson(context, id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<SalespersonResponse> updateSalespersonStatus(
            @PathVariable UUID id,
            @Valid @RequestBody SalespersonStatusUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.SALESPERSON_MANAGE
        );
        SalespersonResponse response = salespersonService.updateSalespersonStatus(context, id, request.status());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/assignments")
    public ResponseEntity<SalesAssignmentResponse> assignCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody SalesAssignmentCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.SALESPERSON_MANAGE
        );
        SalesAssignmentResponse response = salespersonService.assignCustomer(context, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/assignments")
    public ResponseEntity<List<SalesAssignmentResponse>> listAssignments(
            @PathVariable UUID id,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.SALESPERSON_MANAGE
        );
        List<SalesAssignmentResponse> response = salespersonService.listAssignments(context, id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/assignments/{assignmentId}")
    public ResponseEntity<SalesAssignmentResponse> terminateAssignment(
            @PathVariable UUID id,
            @PathVariable UUID assignmentId,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.SALESPERSON_MANAGE
        );
        SalesAssignmentResponse response = salespersonService.terminateAssignment(context, id, assignmentId);
        return ResponseEntity.ok(response);
    }
}
