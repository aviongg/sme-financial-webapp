package com.app.sme_health_backend.counterparty;

import com.app.sme_health_backend.counterparty.dto.CounterpartyCreateRequest;
import com.app.sme_health_backend.counterparty.dto.CounterpartyDetailResponse;
import com.app.sme_health_backend.counterparty.dto.CounterpartyListResponse;
import com.app.sme_health_backend.counterparty.dto.CounterpartyUpdateRequest;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/finance/counterparties")
public class CounterpartyController {

    private final CounterpartyService counterpartyService;
    private final BusinessAuthorizationService authService;

    public CounterpartyController(
            CounterpartyService counterpartyService,
            BusinessAuthorizationService authService
    ) {
        this.counterpartyService = counterpartyService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<CounterpartyDetailResponse> createCounterparty(
            @RequestBody CounterpartyCreateRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );

        CounterpartyDetailResponse response = counterpartyService.createCounterparty(context, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<CounterpartyListResponse> listCounterparties(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );

        CounterpartyListResponse response = counterpartyService.listCounterparties(
                context, search, type, status, page, size
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CounterpartyDetailResponse> getCounterparty(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );

        CounterpartyDetailResponse response = counterpartyService.getCounterparty(context, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CounterpartyDetailResponse> updateCounterparty(
            @PathVariable UUID id,
            @RequestBody CounterpartyUpdateRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );

        CounterpartyDetailResponse response = counterpartyService.updateCounterparty(context, id, requestDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<CounterpartyDetailResponse> archiveCounterparty(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );

        CounterpartyDetailResponse response = counterpartyService.archiveCounterparty(context, id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<CounterpartyDetailResponse> restoreCounterparty(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );

        CounterpartyDetailResponse response = counterpartyService.restoreCounterparty(context, id);
        return ResponseEntity.ok(response);
    }
}
