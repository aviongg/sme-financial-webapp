package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.obligation.dto.*;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PaymentPlanService {

    public static final String OPERATION_PAYMENT_PLAN_SET = "PAYMENT_PLAN_SET";
    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");
    private static final Pattern EXACT_TWO_DECIMAL_AMOUNT = Pattern.compile("^(?:0|[1-9]\\d*)\\.\\d{2}$");

    private final ObligationPaymentScheduleRepository scheduleRepository;
    private final FinancialObligationRepository obligationRepository;
    private final ObligationPaymentAllocationRepository allocationRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final FinancialIdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public PaymentPlanService(
            ObligationPaymentScheduleRepository scheduleRepository,
            FinancialObligationRepository obligationRepository,
            ObligationPaymentAllocationRepository allocationRepository,
            CounterpartyRepository counterpartyRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            ObjectMapper objectMapper
    ) {
        this.scheduleRepository = scheduleRepository;
        this.obligationRepository = obligationRepository;
        this.allocationRepository = allocationRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PaymentPlanResponse getPaymentPlan(BusinessAccessContext context, UUID obligationId) {
        FinancialObligation ob = obligationRepository.findByIdAndBusinessId(obligationId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Obligation not found"));

        BigDecimal paid = allocationRepository.sumEffectiveAllocations(context.businessId(), obligationId);
        BigDecimal outstanding = ob.getOriginalAmount().subtract(paid);
        if (outstanding.compareTo(BigDecimal.ZERO) < 0) outstanding = BigDecimal.ZERO;

        List<ObligationPaymentSchedule> schedules = scheduleRepository.findByBusinessIdAndObligationIdOrderByInstallmentNumberAsc(context.businessId(), obligationId);

        LocalDate today = LocalDate.now(KARACHI_ZONE);
        BigDecimal totalPlanned = BigDecimal.ZERO;
        List<PaymentPlanInstallmentResponse> installmentResponses = new ArrayList<>();

        for (ObligationPaymentSchedule s : schedules) {
            totalPlanned = totalPlanned.add(s.getExpectedAmount());
            boolean isOverdue = s.getStatus() == ScheduleStatus.PLANNED && s.getExpectedDate().isBefore(today);

            installmentResponses.add(new PaymentPlanInstallmentResponse(
                    s.getId(),
                    s.getInstallmentNumber(),
                    s.getExpectedDate(),
                    s.getExpectedAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    s.getStatus().name(),
                    s.getNotes(),
                    isOverdue
            ));
        }

        String coverageStatus;
        if (totalPlanned.compareTo(BigDecimal.ZERO) == 0) {
            coverageStatus = "UNPLANNED";
        } else if (totalPlanned.compareTo(outstanding) == 0) {
            coverageStatus = "FULL_COVERAGE";
        } else if (totalPlanned.compareTo(outstanding) < 0) {
            coverageStatus = "PARTIAL_COVERAGE";
        } else {
            coverageStatus = "OVER_COVERAGE";
        }

        return new PaymentPlanResponse(
                ob.getId(),
                ob.getReferenceNumber(),
                ob.getOriginalAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                outstanding.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                totalPlanned.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                coverageStatus,
                installmentResponses
        );
    }

    @Transactional
    public PaymentPlanResponse setPaymentPlan(
            BusinessAccessContext context,
            String idempotencyKey,
            UUID obligationId,
            PaymentPlanRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ObligationValidationException("Idempotency-Key", "Missing required header: Idempotency-Key");
        }
        String trimmedKey = idempotencyKey.trim();
        if (trimmedKey.length() > 100) {
            throw new ObligationValidationException("Idempotency-Key", "Idempotency-Key must not exceed 100 characters");
        }

        if (request == null) {
            throw new ObligationValidationException("body", "Request body is required");
        }

        String payloadHash = computePlanPayloadHash(obligationId, request);
        Optional<FinancialIdempotencyRecord> existingOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                context.businessId(),
                OPERATION_PAYMENT_PLAN_SET,
                trimmedKey
        );
        if (existingOpt.isPresent()) {
            FinancialIdempotencyRecord existing = existingOpt.get();
            if (existing.getRequestHash().equals(payloadHash)) {
                return deserializeResponse(existing.getResponseBody());
            } else {
                throw new DuplicateResourceException(
                        "Idempotency key '" + trimmedKey + "' has already been used with a different request payload"
                );
            }
        }

        FinancialObligation obligation = obligationRepository.findForUpdateByIdAndBusinessId(obligationId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Obligation not found"));

        if (obligation.getStatus() != ObligationStatus.OPEN) {
            throw new ObligationConflictException("Obligation is voided and cannot have a payment plan");
        }

        List<PaymentPlanInstallmentRequest> items = request.installments() != null ? request.installments() : Collections.emptyList();
        if (items.size() > 50) {
            throw new ObligationValidationException("installments", "Cannot create more than 50 installments in a payment plan");
        }

        Set<Integer> seenNumbers = new HashSet<>();
        List<ObligationPaymentSchedule> toSave = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            PaymentPlanInstallmentRequest item = items.get(i);
            if (item == null) {
                throw new ObligationValidationException("installments[" + i + "]", "Installment item cannot be null");
            }
            if (item.installmentNumber() == null || item.installmentNumber() <= 0) {
                throw new ObligationValidationException("installments[" + i + "].installmentNumber", "Installment number must be a positive integer");
            }
            if (!seenNumbers.add(item.installmentNumber())) {
                throw new ObligationValidationException("installments[" + i + "].installmentNumber", "Duplicate installment number: " + item.installmentNumber());
            }

            if (item.expectedDate() == null || item.expectedDate().isBlank()) {
                throw new ObligationValidationException("installments[" + i + "].expectedDate", "Expected date is required");
            }
            LocalDate expectedDate;
            try {
                expectedDate = LocalDate.parse(item.expectedDate().trim());
            } catch (DateTimeParseException e) {
                throw new ObligationValidationException("installments[" + i + "].expectedDate", "Invalid expectedDate format. Expected YYYY-MM-DD");
            }

            if (item.expectedAmount() == null || item.expectedAmount().isBlank()) {
                throw new ObligationValidationException("installments[" + i + "].expectedAmount", "Expected amount is required");
            }
            String amtStr = item.expectedAmount().trim();
            if (!EXACT_TWO_DECIMAL_AMOUNT.matcher(amtStr).matches()) {
                throw new ObligationValidationException("installments[" + i + "].expectedAmount", "Expected amount must have exactly 2 decimal places");
            }
            BigDecimal expectedAmount = new BigDecimal(amtStr);
            if (expectedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ObligationValidationException("installments[" + i + "].expectedAmount", "Expected amount must be strictly positive");
            }

            toSave.add(new ObligationPaymentSchedule(
                    context.businessId(),
                    obligation.getId(),
                    item.installmentNumber(),
                    expectedDate,
                    expectedAmount,
                    item.notes() != null && !item.notes().isBlank() ? item.notes().trim() : null,
                    context.userId()
            ));
        }

        // Replace existing schedules
        scheduleRepository.deleteByBusinessIdAndObligationId(context.businessId(), obligation.getId());
        scheduleRepository.saveAllAndFlush(toSave);

        PaymentPlanResponse response = getPaymentPlan(context, obligation.getId());

        String responseJson = serializeResponse(response);
        FinancialIdempotencyRecord idempotencyRecord = new FinancialIdempotencyRecord(
                context.businessId(),
                OPERATION_PAYMENT_PLAN_SET,
                trimmedKey,
                payloadHash,
                obligation.getId(),
                200,
                responseJson
        );
        idempotencyRepository.saveAndFlush(idempotencyRecord);

        return response;
    }

    @Transactional(readOnly = true)
    public PaymentCalendarResponse getPaymentCalendar(
            BusinessAccessContext context,
            String directionStr,
            String startDateStr,
            String endDateStr
    ) {
        LocalDate today = LocalDate.now(KARACHI_ZONE);

        LocalDate startDate = today;
        if (startDateStr != null && !startDateStr.isBlank()) {
            try {
                startDate = LocalDate.parse(startDateStr.trim());
            } catch (DateTimeParseException e) {
                throw new ObligationValidationException("startDate", "Invalid startDate format. Expected YYYY-MM-DD");
            }
        }

        LocalDate endDate = today.plusDays(30);
        if (endDateStr != null && !endDateStr.isBlank()) {
            try {
                endDate = LocalDate.parse(endDateStr.trim());
            } catch (DateTimeParseException e) {
                throw new ObligationValidationException("endDate", "Invalid endDate format. Expected YYYY-MM-DD");
            }
        }

        if (endDate.isBefore(startDate)) {
            throw new ObligationValidationException("endDate", "endDate cannot precede startDate");
        }

        ObligationDirection filterDirection = null;
        if (directionStr != null && !directionStr.isBlank()) {
            try {
                filterDirection = ObligationDirection.valueOf(directionStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ObligationValidationException("direction", "Invalid direction: " + directionStr);
            }
        }

        final ObligationDirection finalDir = filterDirection;
        List<FinancialObligation> allObligations = obligationRepository.findByBusinessId(context.businessId());
        if (finalDir != null) {
            allObligations = allObligations.stream().filter(o -> o.getDirection() == finalDir).toList();
        }

        Set<UUID> cpIds = allObligations.stream().map(FinancialObligation::getCounterpartyId).collect(Collectors.toSet());
        Map<UUID, Counterparty> cpMap = cpIds.isEmpty() ? Collections.emptyMap() :
                counterpartyRepository.findAllById(cpIds).stream()
                        .filter(c -> c.getBusinessId().equals(context.businessId()))
                        .collect(Collectors.toMap(Counterparty::getId, c -> c));

        List<ObligationPaymentAllocation> allAllocations = allocationRepository.findByBusinessId(context.businessId());
        Map<UUID, BigDecimal> paidByObligation = new HashMap<>();
        for (ObligationPaymentAllocation a : allAllocations) {
            if (a.getStatus() == AllocationStatus.EFFECTIVE) {
                paidByObligation.merge(a.getObligationId(), a.getAmount(), BigDecimal::add);
            }
        }

        List<ObligationPaymentSchedule> allPlannedSchedules = scheduleRepository.findByBusinessId(context.businessId())
                .stream()
                .filter(s -> s.getStatus() == ScheduleStatus.PLANNED)
                .sorted(Comparator.comparing(ObligationPaymentSchedule::getExpectedDate)
                        .thenComparingInt(ObligationPaymentSchedule::getInstallmentNumber))
                .toList();

        Map<UUID, List<ObligationPaymentSchedule>> schedulesByObligation = new LinkedHashMap<>();
        for (ObligationPaymentSchedule s : allPlannedSchedules) {
            schedulesByObligation.computeIfAbsent(s.getObligationId(), k -> new ArrayList<>()).add(s);
        }

        Map<UUID, FinancialObligation> obligationById = allObligations.stream()
                .collect(Collectors.toMap(FinancialObligation::getId, o -> o));

        final LocalDate finalStartDate = startDate;
        final LocalDate finalEndDate = endDate;

        List<PaymentCalendarItemResponse> calendarItems = new ArrayList<>();

        BigDecimal totalExpRec = BigDecimal.ZERO;
        BigDecimal totalExpPay = BigDecimal.ZERO;
        BigDecimal overdueRec = BigDecimal.ZERO;
        BigDecimal overduePay = BigDecimal.ZERO;
        BigDecimal upcomingRec = BigDecimal.ZERO;
        BigDecimal upcomingPay = BigDecimal.ZERO;

        Map<UUID, BigDecimal> remainingPlannedByObligation = new HashMap<>();

        // 1. Add scheduled installments in range, absorbing effective partial settlements chronologically
        //    and capping scheduled exposure at remaining obligation outstanding
        for (Map.Entry<UUID, List<ObligationPaymentSchedule>> entry : schedulesByObligation.entrySet()) {
            FinancialObligation ob = obligationById.get(entry.getKey());
            if (ob == null || ob.getStatus() != ObligationStatus.OPEN) continue;

            BigDecimal paid = paidByObligation.getOrDefault(ob.getId(), BigDecimal.ZERO);
            BigDecimal outstanding = ob.getOriginalAmount().subtract(paid);
            if (outstanding.compareTo(BigDecimal.ZERO) < 0) outstanding = BigDecimal.ZERO;
            if (outstanding.compareTo(BigDecimal.ZERO) == 0) continue; // Settled obligation

            BigDecimal remainingPaidToAbsorb = paid;
            BigDecimal remainingOutstandingCap = outstanding;
            BigDecimal totalRemainingPlannedForOb = BigDecimal.ZERO;

            for (ObligationPaymentSchedule s : entry.getValue()) {
                BigDecimal installmentExp = s.getExpectedAmount();
                if (remainingPaidToAbsorb.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal absorbed = remainingPaidToAbsorb.min(installmentExp);
                    installmentExp = installmentExp.subtract(absorbed);
                    remainingPaidToAbsorb = remainingPaidToAbsorb.subtract(absorbed);
                }
                if (installmentExp.compareTo(remainingOutstandingCap) > 0) {
                    installmentExp = remainingOutstandingCap;
                }
                if (installmentExp.compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // Installment satisfied by prior settlement
                }

                remainingOutstandingCap = remainingOutstandingCap.subtract(installmentExp);
                totalRemainingPlannedForOb = totalRemainingPlannedForOb.add(installmentExp);

                if (!s.getExpectedDate().isBefore(finalStartDate) && !s.getExpectedDate().isAfter(finalEndDate)) {
                    Counterparty cp = cpMap.get(ob.getCounterpartyId());
                    boolean isOverdue = s.getExpectedDate().isBefore(today);

                    if (ob.getDirection() == ObligationDirection.RECEIVABLE) {
                        totalExpRec = totalExpRec.add(installmentExp);
                        if (isOverdue) overdueRec = overdueRec.add(installmentExp);
                        else upcomingRec = upcomingRec.add(installmentExp);
                    } else {
                        totalExpPay = totalExpPay.add(installmentExp);
                        if (isOverdue) overduePay = overduePay.add(installmentExp);
                        else upcomingPay = upcomingPay.add(installmentExp);
                    }

                    calendarItems.add(new PaymentCalendarItemResponse(
                            ob.getId(),
                            ob.getDirection().name(),
                            ob.getCounterpartyId(),
                            cp != null ? cp.getDisplayName() : "Unknown Counterparty",
                            ob.getReferenceNumber(),
                            ob.getSourceType().name(),
                            "SCHEDULED_INSTALLMENT",
                            s.getExpectedDate(),
                            installmentExp.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                            paid.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                            outstanding.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                            isOverdue,
                            s.getNotes()
                    ));
                }
            }
            remainingPlannedByObligation.put(ob.getId(), totalRemainingPlannedForOb);
        }

        // 2. Add unscheduled residual obligation exposure due within range
        for (FinancialObligation ob : allObligations) {
            if (ob.getStatus() != ObligationStatus.OPEN) continue;
            if (ob.getDueDate() == null) continue;

            if (!ob.getDueDate().isBefore(startDate) && !ob.getDueDate().isAfter(endDate)) {
                BigDecimal paid = paidByObligation.getOrDefault(ob.getId(), BigDecimal.ZERO);
                BigDecimal outstanding = ob.getOriginalAmount().subtract(paid);
                if (outstanding.compareTo(BigDecimal.ZERO) < 0) outstanding = BigDecimal.ZERO;
                if (outstanding.compareTo(BigDecimal.ZERO) == 0) continue; // Already settled

                BigDecimal plannedTotal = remainingPlannedByObligation.getOrDefault(ob.getId(), BigDecimal.ZERO);
                BigDecimal unscheduledResidual = outstanding.subtract(plannedTotal);
                if (unscheduledResidual.compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // Fully covered by active scheduled installments; prevent double counting
                }

                Counterparty cp = cpMap.get(ob.getCounterpartyId());
                boolean isOverdue = ob.getDueDate().isBefore(today);

                if (ob.getDirection() == ObligationDirection.RECEIVABLE) {
                    totalExpRec = totalExpRec.add(unscheduledResidual);
                    if (isOverdue) overdueRec = overdueRec.add(unscheduledResidual);
                    else upcomingRec = upcomingRec.add(unscheduledResidual);
                } else {
                    totalExpPay = totalExpPay.add(unscheduledResidual);
                    if (isOverdue) overduePay = overduePay.add(unscheduledResidual);
                    else upcomingPay = upcomingPay.add(unscheduledResidual);
                }

                String notes = plannedTotal.compareTo(BigDecimal.ZERO) > 0
                        ? "Unscheduled residual exposure (Remaining plan covers PKR " + plannedTotal.setScale(2, RoundingMode.UNNECESSARY).toPlainString() + ")"
                        : ob.getDescription();

                calendarItems.add(new PaymentCalendarItemResponse(
                        ob.getId(),
                        ob.getDirection().name(),
                        ob.getCounterpartyId(),
                        cp != null ? cp.getDisplayName() : "Unknown Counterparty",
                        ob.getReferenceNumber(),
                        ob.getSourceType().name(),
                        "OBLIGATION_DUE",
                        ob.getDueDate(),
                        unscheduledResidual.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                        paid.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                        outstanding.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                        isOverdue,
                        notes
                ));
            }
        }

        // Sort items by date ASC
        calendarItems.sort(Comparator.comparing(PaymentCalendarItemResponse::date));

        PaymentCalendarSummaryResponse summary = new PaymentCalendarSummaryResponse(
                totalExpRec.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                totalExpPay.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                overdueRec.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                overduePay.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                upcomingRec.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                upcomingPay.setScale(2, RoundingMode.UNNECESSARY).toPlainString()
        );

        return new PaymentCalendarResponse(startDate, endDate, summary, calendarItems);
    }

    private String computePlanPayloadHash(UUID obligationId, PaymentPlanRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append(obligationId != null ? obligationId.toString() : "").append("|");
        if (req.installments() != null) {
            for (PaymentPlanInstallmentRequest it : req.installments()) {
                sb.append(it.installmentNumber()).append(":").append(it.expectedDate()).append(":").append(it.expectedAmount()).append(";");
            }
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String serializeResponse(PaymentPlanResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize payment plan response", e);
        }
    }

    private PaymentPlanResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, PaymentPlanResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize payment plan response", e);
        }
    }
}
