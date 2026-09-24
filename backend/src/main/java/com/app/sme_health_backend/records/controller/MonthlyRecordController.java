package com.app.sme_health_backend.records.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.records.dto.MonthlyRecordRequest;
import com.app.sme_health_backend.records.dto.MonthlyRecordResponse;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
import com.app.sme_health_backend.shared.dto.MonthQueryRequest;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/records/monthly")
public class MonthlyRecordController {

    private final MonthlyRecordService monthlyRecordService;
    private final BusinessAuthorizationService authService;

    public MonthlyRecordController(
            MonthlyRecordService monthlyRecordService,
            BusinessAuthorizationService authService
    ) {
        this.monthlyRecordService = monthlyRecordService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<MonthlyRecordResponse> saveMonthlyRecord(
            @Valid @RequestBody MonthlyRecordRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);

        MonthlyRecord record = toEntity(requestDto, context.businessId());
        MonthlyRecord savedRecord = monthlyRecordService.saveMonthlyRecord(record);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MonthlyRecordResponse.fromEntity(savedRecord));
    }

    @GetMapping
    public ResponseEntity<List<MonthlyRecordResponse>> getActiveBusinessRecords(HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        List<MonthlyRecordResponse> responses =
                monthlyRecordService.getUserRecords(context.businessId())
                        .stream()
                        .map(MonthlyRecordResponse::fromEntity)
                        .toList();

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/query")
    public ResponseEntity<MonthlyRecordResponse> queryMonthlyRecord(
            @Valid @RequestBody MonthQueryRequest queryRequest,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        return monthlyRecordService
                .getMonthlyRecord(context.businessId(), queryRequest.month())
                .map(MonthlyRecordResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Monthly record not found for month: " + queryRequest.month()
                ));
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<MonthlyRecordResponse> getRecordById(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        UUID recordId;
        try {
            recordId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid record ID format: " + id);
        }

        return monthlyRecordService
                .getRecordById(recordId, context.businessId())
                .map(MonthlyRecordResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Monthly record not found with id: " + id
                ));
    }

    private MonthlyRecord toEntity(MonthlyRecordRequest request, UUID businessId) {
        MonthlyRecord record = new MonthlyRecord();

        record.setUserId(businessId);
        record.setMonth(request.getMonth());

        record.setCashInflow(request.getCashInflow());
        record.setCashOutflow(request.getCashOutflow());
        record.setRevenue(request.getRevenue());
        record.setCogs(request.getCogs());
        record.setOperatingExpenses(request.getOperatingExpenses());
        record.setCashBalanceEom(request.getCashBalanceEom());

        record.setReceivablesOutstanding(request.getReceivablesOutstanding());
        record.setPayablesOutstanding(request.getPayablesOutstanding());
        record.setInventoryValue(request.getInventoryValue());
        record.setLoanOutstanding(request.getLoanOutstanding());
        record.setInterestExpense(request.getInterestExpense());

        record.setFinancingType(request.getFinancingType());

        return record;
    }
}