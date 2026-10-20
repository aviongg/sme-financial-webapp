package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/finance/advances")
public class AdvanceController {

    private final AdvanceService advanceService;
    private final BusinessAuthorizationService authService;

    public AdvanceController(
            AdvanceService advanceService,
            BusinessAuthorizationService authService
    ) {
        this.advanceService = advanceService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<AdvanceDetailResponse> createAdvance(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) AdvanceCreateRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        AdvanceDetailResponse response = advanceService.createAdvance(context, idempotencyKey, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<AdvanceListResponse> listAdvances(
            @RequestParam(name = "direction", required = false) String direction,
            @RequestParam(name = "counterpartyId", required = false) UUID counterpartyId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt"));
        AdvanceListResponse response = advanceService.listAdvances(context, direction, counterpartyId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdvanceDetailResponse> getAdvance(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        AdvanceDetailResponse response = advanceService.getAdvance(context, id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/applications")
    public ResponseEntity<AdvanceDetailResponse> applyAdvance(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) AdvanceApplyRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        AdvanceDetailResponse response = advanceService.applyAdvance(context, idempotencyKey, id, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/applications/{applicationId}/reverse")
    public ResponseEntity<AdvanceDetailResponse> reverseApplication(
            @PathVariable("id") UUID id,
            @PathVariable("applicationId") UUID applicationId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) AdvanceApplicationReverseRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        AdvanceDetailResponse response = advanceService.reverseApplication(context, idempotencyKey, id, applicationId, requestDto);
        return ResponseEntity.ok(response);
    }
}
