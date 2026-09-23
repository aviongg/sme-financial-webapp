package com.app.sme_health_backend.records.controller;

import com.app.sme_health_backend.records.dto.MonthlyRecordRequest;
import com.app.sme_health_backend.records.dto.MonthlyRecordResponse;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
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

    public MonthlyRecordController(MonthlyRecordService monthlyRecordService) {
        this.monthlyRecordService = monthlyRecordService;
    }

    @PostMapping
    public ResponseEntity<MonthlyRecordResponse> saveMonthlyRecord(
            @Valid @RequestBody MonthlyRecordRequest request
    ) {
        MonthlyRecord record = toEntity(request);

        MonthlyRecord savedRecord =
                monthlyRecordService.saveMonthlyRecord(record);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MonthlyRecordResponse.fromEntity(savedRecord));
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<MonthlyRecordResponse> getRecordById(@PathVariable String id) {
        UUID recordId;
        try {
            recordId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid record ID format: " + id);
        }

        return monthlyRecordService
                .getRecordById(recordId)
                .map(MonthlyRecordResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Monthly record not found with id: " + id
                ));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<MonthlyRecordResponse>> getUserRecords(
            @PathVariable UUID userId
    ) {
        List<MonthlyRecordResponse> responses =
                monthlyRecordService.getUserRecords(userId)
                        .stream()
                        .map(MonthlyRecordResponse::fromEntity)
                        .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{userId}/{month}")
    public ResponseEntity<MonthlyRecordResponse> getMonthlyRecord(
            @PathVariable UUID userId,
            @PathVariable String month
    ) {
        return monthlyRecordService
                .getMonthlyRecord(userId, month)
                .map(MonthlyRecordResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private MonthlyRecord toEntity(MonthlyRecordRequest request) {
        MonthlyRecord record = new MonthlyRecord();

        record.setUserId(request.getUserId());
        record.setMonth(request.getMonth());

        record.setCashInflow(request.getCashInflow());
        record.setCashOutflow(request.getCashOutflow());
        record.setRevenue(request.getRevenue());
        record.setCogs(request.getCogs());
        record.setOperatingExpenses(request.getOperatingExpenses());
        record.setCashBalanceEom(request.getCashBalanceEom());

        record.setReceivablesOutstanding(
                request.getReceivablesOutstanding()
        );
        record.setPayablesOutstanding(
                request.getPayablesOutstanding()
        );
        record.setInventoryValue(
                request.getInventoryValue()
        );
        record.setLoanOutstanding(
                request.getLoanOutstanding()
        );
        record.setInterestExpense(
                request.getInterestExpense()
        );

        record.setFinancingType(request.getFinancingType());

        return record;
    }
}