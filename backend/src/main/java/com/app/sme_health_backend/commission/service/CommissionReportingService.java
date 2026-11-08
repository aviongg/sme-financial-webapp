package com.app.sme_health_backend.commission.service;

import com.app.sme_health_backend.commission.dto.CommissionAnalyticsOverviewResponse;
import com.app.sme_health_backend.commission.dto.CommissionAuditEventResponse;
import com.app.sme_health_backend.commission.dto.CommissionPeriodTrendResponse;
import com.app.sme_health_backend.commission.dto.SalespersonPerformanceResponse;
import com.app.sme_health_backend.commission.dto.SalespersonStatementResponse;
import com.app.sme_health_backend.commission.entity.CommissionAdvance;
import com.app.sme_health_backend.commission.entity.CommissionEarning;
import com.app.sme_health_backend.commission.entity.CommissionPayout;
import com.app.sme_health_backend.commission.entity.CommissionRule;
import com.app.sme_health_backend.commission.entity.SalesAssignment;
import com.app.sme_health_backend.commission.entity.SalespersonProfile;
import com.app.sme_health_backend.commission.exception.CommissionSettlementNotFoundException;
import com.app.sme_health_backend.commission.model.AssignmentStatus;
import com.app.sme_health_backend.commission.model.CommissionAdvanceStatus;
import com.app.sme_health_backend.commission.model.CommissionAuditEventType;
import com.app.sme_health_backend.commission.model.CommissionEarningStatus;
import com.app.sme_health_backend.commission.model.CommissionPayoutStatus;
import com.app.sme_health_backend.commission.model.CommissionPayoutType;
import com.app.sme_health_backend.commission.model.SalespersonStatus;
import com.app.sme_health_backend.commission.repository.CommissionAdvanceRepository;
import com.app.sme_health_backend.commission.repository.CommissionEarningRepository;
import com.app.sme_health_backend.commission.repository.CommissionPayoutRepository;
import com.app.sme_health_backend.commission.repository.CommissionRuleRepository;
import com.app.sme_health_backend.commission.repository.SalesAssignmentRepository;
import com.app.sme_health_backend.commission.repository.SalespersonProfileRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CommissionReportingService {

    public static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");

    private final CommissionEarningRepository earningRepository;
    private final CommissionPayoutRepository payoutRepository;
    private final CommissionAdvanceRepository advanceRepository;
    private final CommissionRuleRepository ruleRepository;
    private final SalespersonProfileRepository salespersonRepository;
    private final SalesAssignmentRepository assignmentRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final BusinessRepository businessRepository;
    private final BusinessAuthorizationService authorizationService;

    public CommissionReportingService(
            CommissionEarningRepository earningRepository,
            CommissionPayoutRepository payoutRepository,
            CommissionAdvanceRepository advanceRepository,
            CommissionRuleRepository ruleRepository,
            SalespersonProfileRepository salespersonRepository,
            SalesAssignmentRepository assignmentRepository,
            CounterpartyRepository counterpartyRepository,
            BusinessRepository businessRepository,
            BusinessAuthorizationService authorizationService
    ) {
        this.earningRepository = earningRepository;
        this.payoutRepository = payoutRepository;
        this.advanceRepository = advanceRepository;
        this.ruleRepository = ruleRepository;
        this.salespersonRepository = salespersonRepository;
        this.assignmentRepository = assignmentRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.businessRepository = businessRepository;
        this.authorizationService = authorizationService;
    }

    /**
     * Authoritative overview analytics: flows vs stocks.
     */
    public CommissionAnalyticsOverviewResponse getOverview(
            BusinessAccessContext context,
            LocalDate startDate,
            LocalDate endDate,
            UUID salespersonId
    ) {
        UUID businessId = context.businessId();

        List<CommissionEarning> allEarnings = (salespersonId != null)
                ? earningRepository.findByBusinessIdAndSalespersonId(businessId, salespersonId)
                : earningRepository.findByBusinessId(businessId);

        List<CommissionAdvance> allAdvances = (salespersonId != null)
                ? advanceRepository.findByBusinessIdAndSalespersonId(businessId, salespersonId)
                : advanceRepository.findByBusinessId(businessId);

        List<CommissionPayout> allPayouts = (salespersonId != null)
                ? payoutRepository.findByBusinessIdAndSalespersonId(businessId, salespersonId)
                : payoutRepository.findByBusinessId(businessId);

        List<SalespersonProfile> allSalespersons = salespersonRepository.findByBusinessId(businessId);
        long totalSalespersons = allSalespersons.size();
        long activeSalespersons = allSalespersons.stream().filter(sp -> sp.getStatus() == SalespersonStatus.ACTIVE).count();

        // --- Cumulative-to-date stocks & totals ---
        BigDecimal totalEarned = BigDecimal.ZERO;
        BigDecimal totalApproved = BigDecimal.ZERO;
        BigDecimal totalUnapproved = BigDecimal.ZERO;

        for (CommissionEarning e : allEarnings) {
            if (e.getStatus() == CommissionEarningStatus.VOID) continue;
            totalEarned = totalEarned.add(e.getCommissionAmount());

            if (isApprovedOrSettled(e.getStatus())) {
                BigDecimal appAmt = e.getApprovedAmount() != null ? e.getApprovedAmount() : e.getCommissionAmount();
                totalApproved = totalApproved.add(appAmt);
            } else if (e.getStatus() == CommissionEarningStatus.EARNED) {
                totalUnapproved = totalUnapproved.add(e.getCommissionAmount());
            }
        }

        BigDecimal totalAdvancesPaid = BigDecimal.ZERO;
        BigDecimal totalAdvancesApplied = BigDecimal.ZERO;
        for (CommissionAdvance a : allAdvances) {
            if (a.getStatus() == CommissionAdvanceStatus.VOID) continue;
            totalAdvancesPaid = totalAdvancesPaid.add(a.getAmount());
            totalAdvancesApplied = totalAdvancesApplied.add(a.getAppliedAmount());
        }
        BigDecimal totalAdvancesUnapplied = totalAdvancesPaid.subtract(totalAdvancesApplied);

        BigDecimal totalGrossPayouts = BigDecimal.ZERO;
        BigDecimal totalReversals = BigDecimal.ZERO;
        for (CommissionPayout p : allPayouts) {
            if (p.getPayoutType() == CommissionPayoutType.PAYOUT) {
                totalGrossPayouts = totalGrossPayouts.add(p.getAmount());
                if (p.getStatus() == CommissionPayoutStatus.REVERSED) {
                    totalReversals = totalReversals.add(p.getAmount());
                }
            }
        }
        BigDecimal totalNetPayouts = totalGrossPayouts.subtract(totalReversals);
        BigDecimal totalSettled = totalAdvancesApplied.add(totalNetPayouts);
        BigDecimal totalOutstandingPayable = totalApproved.subtract(totalSettled);
        BigDecimal totalNetCashOutflow = totalAdvancesPaid.add(totalNetPayouts);

        // --- Period Flow Measures ---
        BigDecimal periodEarned = null;
        BigDecimal periodApproved = null;
        BigDecimal periodAdvancesPaid = null;
        BigDecimal periodAdvancesApplied = null;
        BigDecimal periodGrossPayouts = null;
        BigDecimal periodReversals = null;
        BigDecimal periodNetPayouts = null;
        BigDecimal periodSettled = null;
        BigDecimal periodNetCashOutflow = null;

        if (startDate != null || endDate != null) {
            periodEarned = BigDecimal.ZERO;
            periodApproved = BigDecimal.ZERO;

            for (CommissionEarning e : allEarnings) {
                if (e.getStatus() == CommissionEarningStatus.VOID) continue;
                LocalDate earningDate = e.getCreatedAt().atZone(KARACHI_ZONE).toLocalDate();
                if (inRange(earningDate, startDate, endDate)) {
                    periodEarned = periodEarned.add(e.getCommissionAmount());
                }

                if (isApprovedOrSettled(e.getStatus())) {
                    LocalDate appDate = e.getApprovedAt() != null
                            ? e.getApprovedAt().atZone(KARACHI_ZONE).toLocalDate()
                            : earningDate;
                    if (inRange(appDate, startDate, endDate)) {
                        BigDecimal appAmt = e.getApprovedAmount() != null ? e.getApprovedAmount() : e.getCommissionAmount();
                        periodApproved = periodApproved.add(appAmt);
                    }
                }
            }

            periodAdvancesPaid = BigDecimal.ZERO;
            for (CommissionAdvance a : allAdvances) {
                if (a.getStatus() == CommissionAdvanceStatus.VOID) continue;
                if (inRange(a.getPaymentDate(), startDate, endDate)) {
                    periodAdvancesPaid = periodAdvancesPaid.add(a.getAmount());
                }
            }

            periodAdvancesApplied = BigDecimal.ZERO;
            periodGrossPayouts = BigDecimal.ZERO;
            periodReversals = BigDecimal.ZERO;

            for (CommissionPayout p : allPayouts) {
                LocalDate pDate = p.getPaymentDate();
                if (!inRange(pDate, startDate, endDate)) continue;

                if (p.getPayoutType() == CommissionPayoutType.ADVANCE_APPLICATION && p.getStatus() == CommissionPayoutStatus.SETTLED) {
                    periodAdvancesApplied = periodAdvancesApplied.add(p.getAmount());
                } else if (p.getPayoutType() == CommissionPayoutType.PAYOUT) {
                    periodGrossPayouts = periodGrossPayouts.add(p.getAmount());
                    if (p.getStatus() == CommissionPayoutStatus.REVERSED) {
                        periodReversals = periodReversals.add(p.getAmount());
                    }
                }
            }

            periodNetPayouts = periodGrossPayouts.subtract(periodReversals);
            periodSettled = periodAdvancesApplied.add(periodNetPayouts);
            periodNetCashOutflow = periodAdvancesPaid.add(periodNetPayouts);
        }

        return new CommissionAnalyticsOverviewResponse(
                scale(totalEarned),
                scale(totalApproved),
                scale(totalUnapproved),
                scale(totalAdvancesPaid),
                scale(totalAdvancesApplied),
                scale(totalAdvancesUnapplied),
                scale(totalGrossPayouts),
                scale(totalReversals),
                scale(totalNetPayouts),
                scale(totalSettled),
                scale(totalOutstandingPayable),
                scale(totalNetCashOutflow),
                startDate,
                endDate,
                scaleOrNull(periodEarned),
                scaleOrNull(periodApproved),
                scaleOrNull(periodAdvancesPaid),
                scaleOrNull(periodAdvancesApplied),
                scaleOrNull(periodGrossPayouts),
                scaleOrNull(periodReversals),
                scaleOrNull(periodNetPayouts),
                scaleOrNull(periodSettled),
                scaleOrNull(periodNetCashOutflow),
                "PKR",
                totalSalespersons,
                activeSalespersons
        );
    }

    /**
     * Salesperson performance breakdown.
     */
    public List<SalespersonPerformanceResponse> getSalespersonBreakdown(
            BusinessAccessContext context,
            LocalDate startDate,
            LocalDate endDate
    ) {
        UUID businessId = context.businessId();
        List<SalespersonProfile> salespersons = salespersonRepository.findByBusinessId(businessId);
        salespersons.sort(Comparator.comparing(SalespersonProfile::getDisplayName, String.CASE_INSENSITIVE_ORDER));

        List<SalespersonPerformanceResponse> results = new ArrayList<>();

        for (SalespersonProfile sp : salespersons) {
            UUID spId = sp.getId();

            List<CommissionEarning> earnings = earningRepository.findByBusinessIdAndSalespersonId(businessId, spId);
            List<CommissionAdvance> advances = advanceRepository.findByBusinessIdAndSalespersonId(businessId, spId);
            List<CommissionPayout> payouts = payoutRepository.findByBusinessIdAndSalespersonId(businessId, spId);
            List<SalesAssignment> activeAssignments = assignmentRepository.findByBusinessIdAndSalespersonIdAndStatus(
                    businessId, spId, AssignmentStatus.ACTIVE
            );

            BigDecimal attributedCollections = BigDecimal.ZERO;
            BigDecimal earned = BigDecimal.ZERO;
            BigDecimal approved = BigDecimal.ZERO;
            BigDecimal unapproved = BigDecimal.ZERO;

            for (CommissionEarning e : earnings) {
                if (e.getStatus() == CommissionEarningStatus.VOID) continue;
                LocalDate eDate = e.getCreatedAt().atZone(KARACHI_ZONE).toLocalDate();
                if (startDate != null && eDate.isBefore(startDate)) continue;
                if (endDate != null && eDate.isAfter(endDate)) continue;

                attributedCollections = attributedCollections.add(e.getSaleAmount());
                earned = earned.add(e.getCommissionAmount());

                if (isApprovedOrSettled(e.getStatus())) {
                    BigDecimal appAmt = e.getApprovedAmount() != null ? e.getApprovedAmount() : e.getCommissionAmount();
                    approved = approved.add(appAmt);
                } else if (e.getStatus() == CommissionEarningStatus.EARNED) {
                    unapproved = unapproved.add(e.getCommissionAmount());
                }
            }

            BigDecimal advancesPaid = BigDecimal.ZERO;
            BigDecimal advancesApplied = BigDecimal.ZERO;
            for (CommissionAdvance a : advances) {
                if (a.getStatus() == CommissionAdvanceStatus.VOID) continue;
                if (startDate != null && a.getPaymentDate().isBefore(startDate)) continue;
                if (endDate != null && a.getPaymentDate().isAfter(endDate)) continue;

                advancesPaid = advancesPaid.add(a.getAmount());
                advancesApplied = advancesApplied.add(a.getAppliedAmount());
            }
            BigDecimal unappliedAdvanceBalance = advancesPaid.subtract(advancesApplied);

            BigDecimal directPayouts = BigDecimal.ZERO;
            BigDecimal reversals = BigDecimal.ZERO;

            for (CommissionPayout p : payouts) {
                if (p.getPayoutType() != CommissionPayoutType.PAYOUT) continue;
                if (startDate != null && p.getPaymentDate().isBefore(startDate)) continue;
                if (endDate != null && p.getPaymentDate().isAfter(endDate)) continue;

                directPayouts = directPayouts.add(p.getAmount());
                if (p.getStatus() == CommissionPayoutStatus.REVERSED) {
                    reversals = reversals.add(p.getAmount());
                }
            }
            BigDecimal netPayouts = directPayouts.subtract(reversals);
            BigDecimal totalSettled = advancesApplied.add(netPayouts);
            BigDecimal outstandingPayable = approved.subtract(totalSettled);

            results.add(new SalespersonPerformanceResponse(
                    spId,
                    sp.getDisplayName(),
                    sp.getEmail(),
                    sp.getStatus(),
                    activeAssignments.size(),
                    scale(attributedCollections),
                    scale(earned),
                    scale(approved),
                    scale(unapproved),
                    scale(advancesPaid),
                    scale(advancesApplied),
                    scale(unappliedAdvanceBalance),
                    scale(directPayouts),
                    scale(reversals),
                    scale(netPayouts),
                    scale(totalSettled),
                    scale(outstandingPayable),
                    "PKR"
            ));
        }

        return results;
    }

    /**
     * Monthly trend metrics for the past N months.
     */
    public List<CommissionPeriodTrendResponse> getPeriodTrends(BusinessAccessContext context, int monthsCount) {
        UUID businessId = context.businessId();
        int safeMonths = Math.min(Math.max(monthsCount, 1), 24);

        YearMonth currentYm = YearMonth.now(KARACHI_ZONE);
        List<YearMonth> periodMonths = new ArrayList<>();
        for (int i = safeMonths - 1; i >= 0; i--) {
            periodMonths.add(currentYm.minusMonths(i));
        }

        List<CommissionEarning> allEarnings = earningRepository.findByBusinessId(businessId);
        List<CommissionAdvance> allAdvances = advanceRepository.findByBusinessId(businessId);
        List<CommissionPayout> allPayouts = payoutRepository.findByBusinessId(businessId);

        List<CommissionPeriodTrendResponse> trends = new ArrayList<>();

        for (YearMonth ym : periodMonths) {
            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();

            BigDecimal earned = BigDecimal.ZERO;
            BigDecimal approved = BigDecimal.ZERO;

            for (CommissionEarning e : allEarnings) {
                if (e.getStatus() == CommissionEarningStatus.VOID) continue;
                LocalDate d = e.getCreatedAt().atZone(KARACHI_ZONE).toLocalDate();
                if (inRange(d, start, end)) {
                    earned = earned.add(e.getCommissionAmount());
                }
                if (isApprovedOrSettled(e.getStatus())) {
                    LocalDate appDate = e.getApprovedAt() != null ? e.getApprovedAt().atZone(KARACHI_ZONE).toLocalDate() : d;
                    if (inRange(appDate, start, end)) {
                        BigDecimal appAmt = e.getApprovedAmount() != null ? e.getApprovedAmount() : e.getCommissionAmount();
                        approved = approved.add(appAmt);
                    }
                }
            }

            BigDecimal advancesPaid = BigDecimal.ZERO;
            for (CommissionAdvance a : allAdvances) {
                if (a.getStatus() == CommissionAdvanceStatus.VOID) continue;
                if (inRange(a.getPaymentDate(), start, end)) {
                    advancesPaid = advancesPaid.add(a.getAmount());
                }
            }

            BigDecimal advancesApplied = BigDecimal.ZERO;
            BigDecimal grossPayouts = BigDecimal.ZERO;
            BigDecimal reversals = BigDecimal.ZERO;

            for (CommissionPayout p : allPayouts) {
                if (!inRange(p.getPaymentDate(), start, end)) continue;

                if (p.getPayoutType() == CommissionPayoutType.ADVANCE_APPLICATION && p.getStatus() == CommissionPayoutStatus.SETTLED) {
                    advancesApplied = advancesApplied.add(p.getAmount());
                } else if (p.getPayoutType() == CommissionPayoutType.PAYOUT) {
                    grossPayouts = grossPayouts.add(p.getAmount());
                    if (p.getStatus() == CommissionPayoutStatus.REVERSED) {
                        reversals = reversals.add(p.getAmount());
                    }
                }
            }

            BigDecimal netPayouts = grossPayouts.subtract(reversals);
            BigDecimal netCash = advancesPaid.add(netPayouts);

            trends.add(new CommissionPeriodTrendResponse(
                    ym.format(DateTimeFormatter.ofPattern("yyyy-MM")),
                    scale(earned),
                    scale(approved),
                    scale(advancesPaid),
                    scale(advancesApplied),
                    scale(netPayouts),
                    scale(netCash),
                    "PKR"
            ));
        }

        return trends;
    }

    /**
     * Unified chronological audit ledger events.
     */
    public List<CommissionAuditEventResponse> getAuditLedger(
            BusinessAccessContext context,
            LocalDate startDate,
            LocalDate endDate,
            UUID salespersonId,
            UUID counterpartyId
    ) {
        UUID businessId = context.businessId();

        List<CommissionEarning> earnings = (salespersonId != null)
                ? earningRepository.findByBusinessIdAndSalespersonId(businessId, salespersonId)
                : earningRepository.findByBusinessId(businessId);

        List<CommissionAdvance> advances = (salespersonId != null)
                ? advanceRepository.findByBusinessIdAndSalespersonId(businessId, salespersonId)
                : advanceRepository.findByBusinessId(businessId);

        List<CommissionPayout> payouts = (salespersonId != null)
                ? payoutRepository.findByBusinessIdAndSalespersonId(businessId, salespersonId)
                : payoutRepository.findByBusinessId(businessId);

        Map<UUID, String> salespersonNames = salespersonRepository.findByBusinessId(businessId).stream()
                .collect(Collectors.toMap(SalespersonProfile::getId, SalespersonProfile::getDisplayName));

        Map<UUID, String> counterpartyNames = counterpartyRepository.findAllByBusinessIdAndStatusOrderByDisplayNameAsc(
                businessId, com.app.sme_health_backend.counterparty.CounterpartyStatus.ACTIVE
        ).stream().collect(Collectors.toMap(Counterparty::getId, Counterparty::getDisplayName));

        List<CommissionAuditEventResponse> events = new ArrayList<>();

        // Earnings events
        for (CommissionEarning e : earnings) {
            if (counterpartyId != null && !e.getCounterpartyId().equals(counterpartyId)) continue;
            LocalDate eDate = e.getCreatedAt().atZone(KARACHI_ZONE).toLocalDate();

            if (inRange(eDate, startDate, endDate)) {
                events.add(new CommissionAuditEventResponse(
                        UUID.randomUUID(),
                        e.getCreatedAt(),
                        e.getSalespersonId(),
                        salespersonNames.getOrDefault(e.getSalespersonId(), "Unknown Salesperson"),
                        e.getCounterpartyId(),
                        counterpartyNames.getOrDefault(e.getCounterpartyId(), "Unknown Customer"),
                        CommissionAuditEventType.EARNING_RECOGNIZED,
                        scale(e.getCommissionAmount()),
                        e.getCurrency(),
                        e.getStatus().name(),
                        e.getId().toString(),
                        null,
                        null,
                        e.getNotes() != null ? e.getNotes() : "Commission recognized from collection"
                ));
            }

            if (e.getApprovedAt() != null) {
                LocalDate aDate = e.getApprovedAt().atZone(KARACHI_ZONE).toLocalDate();
                if (inRange(aDate, startDate, endDate)) {
                    BigDecimal appAmt = e.getApprovedAmount() != null ? e.getApprovedAmount() : e.getCommissionAmount();
                    events.add(new CommissionAuditEventResponse(
                            UUID.randomUUID(),
                            e.getApprovedAt(),
                            e.getSalespersonId(),
                            salespersonNames.getOrDefault(e.getSalespersonId(), "Unknown Salesperson"),
                            e.getCounterpartyId(),
                            counterpartyNames.getOrDefault(e.getCounterpartyId(), "Unknown Customer"),
                            CommissionAuditEventType.EARNING_APPROVED,
                            scale(appAmt),
                            e.getCurrency(),
                            CommissionEarningStatus.APPROVED.name(),
                            e.getId().toString(),
                            null,
                            null,
                            "Commission approved for settlement"
                    ));
                }
            }
        }

        // Advance events
        for (CommissionAdvance a : advances) {
            if (counterpartyId != null) continue; // advances are not customer-specific
            if (inRange(a.getPaymentDate(), startDate, endDate)) {
                events.add(new CommissionAuditEventResponse(
                        UUID.randomUUID(),
                        a.getCreatedAt(),
                        a.getSalespersonId(),
                        salespersonNames.getOrDefault(a.getSalespersonId(), "Unknown Salesperson"),
                        null,
                        null,
                        CommissionAuditEventType.ADVANCE_ISSUED,
                        scale(a.getAmount()),
                        a.getCurrency(),
                        a.getStatus().name(),
                        a.getId().toString(),
                        a.getFinancialTransactionId(),
                        null,
                        a.getNotes() != null ? a.getNotes() : "Cash advance issued"
                ));
            }
        }

        // Payout events
        for (CommissionPayout p : payouts) {
            if (!inRange(p.getPaymentDate(), startDate, endDate)) continue;

            CommissionAuditEventType type = (p.getPayoutType() == CommissionPayoutType.ADVANCE_APPLICATION)
                    ? CommissionAuditEventType.ADVANCE_APPLIED
                    : CommissionAuditEventType.PAYOUT_SETTLED;

            events.add(new CommissionAuditEventResponse(
                    UUID.randomUUID(),
                    p.getCreatedAt(),
                    p.getSalespersonId(),
                    salespersonNames.getOrDefault(p.getSalespersonId(), "Unknown Salesperson"),
                    null,
                    null,
                    type,
                    scale(p.getAmount()),
                    p.getCurrency(),
                    p.getStatus().name(),
                    p.getId().toString(),
                    p.getFinancialTransactionId(),
                    null,
                    p.getNotes() != null ? p.getNotes() : (type == CommissionAuditEventType.ADVANCE_APPLIED ? "Advance applied to commission" : "Direct payout disbursed")
            ));

            if (p.getStatus() == CommissionPayoutStatus.REVERSED) {
                events.add(new CommissionAuditEventResponse(
                        UUID.randomUUID(),
                        p.getUpdatedAt(),
                        p.getSalespersonId(),
                        salespersonNames.getOrDefault(p.getSalespersonId(), "Unknown Salesperson"),
                        null,
                        null,
                        CommissionAuditEventType.PAYOUT_REVERSED,
                        scale(p.getAmount()),
                        p.getCurrency(),
                        CommissionPayoutStatus.REVERSED.name(),
                        p.getId().toString(),
                        p.getFinancialTransactionId(),
                        p.getReversalTransactionId(),
                        p.getReversalReason() != null ? p.getReversalReason() : "Payout reversed"
                ));
            }
        }

        events.sort(Comparator.comparing(CommissionAuditEventResponse::timestamp).reversed());
        return events;
    }

    /**
     * Authoritative bilingual salesperson statement.
     */
    public SalespersonStatementResponse getSalespersonStatement(
            BusinessAccessContext context,
            UUID requestedSalespersonId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        UUID businessId = context.businessId();

        UUID effectiveSalespersonId = resolveEffectiveSalespersonId(context, requestedSalespersonId);

        SalespersonProfile sp = salespersonRepository.findByIdAndBusinessId(effectiveSalespersonId, businessId)
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Salesperson not found: " + effectiveSalespersonId));

        Business business = businessRepository.findById(businessId).orElse(null);
        String businessName = business != null ? business.getBusinessName() : "FinSight Business";

        LocalDate safeStart = (startDate != null) ? startDate : YearMonth.now(KARACHI_ZONE).atDay(1);
        LocalDate safeEnd = (endDate != null) ? endDate : LocalDate.now(KARACHI_ZONE);

        if (safeEnd.isBefore(safeStart)) {
            throw new IllegalArgumentException("End date cannot precede start date");
        }

        // Assigned customers
        List<SalesAssignment> assignments = assignmentRepository.findByBusinessIdAndSalespersonId(businessId, effectiveSalespersonId);
        Map<UUID, String> customerNames = counterpartyRepository.findAllById(
                assignments.stream().map(SalesAssignment::getCounterpartyId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(Counterparty::getId, Counterparty::getDisplayName));

        List<SalespersonStatementResponse.StatementCustomer> statementCustomers = assignments.stream()
                .filter(a -> a.getEffectiveStartDate().isBefore(safeEnd.plusDays(1)) &&
                        (a.getEffectiveEndDate() == null || !a.getEffectiveEndDate().isBefore(safeStart)))
                .map(a -> new SalespersonStatementResponse.StatementCustomer(
                        a.getCounterpartyId(),
                        customerNames.getOrDefault(a.getCounterpartyId(), "Customer " + a.getCounterpartyId()),
                        a.getEffectiveStartDate(),
                        a.getEffectiveEndDate()
                ))
                .toList();

        // All earnings and settlements
        List<CommissionEarning> allEarnings = earningRepository.findByBusinessIdAndSalespersonId(businessId, effectiveSalespersonId);
        List<CommissionAdvance> allAdvances = advanceRepository.findByBusinessIdAndSalespersonId(businessId, effectiveSalespersonId);
        List<CommissionPayout> allPayouts = payoutRepository.findByBusinessIdAndSalespersonId(businessId, effectiveSalespersonId);

        // Map rule names
        Map<UUID, String> ruleDescriptions = ruleRepository.findByBusinessId(businessId).stream()
                .collect(Collectors.toMap(CommissionRule::getId, CommissionRule::getRuleName));

        // 1. Calculate Opening Settlement Position: Net approved payable prior to safeStart
        BigDecimal priorApproved = BigDecimal.ZERO;
        for (CommissionEarning e : allEarnings) {
            if (e.getStatus() == CommissionEarningStatus.VOID) continue;
            LocalDate appDate = e.getApprovedAt() != null
                    ? e.getApprovedAt().atZone(KARACHI_ZONE).toLocalDate()
                    : e.getCreatedAt().atZone(KARACHI_ZONE).toLocalDate();

            if (isApprovedOrSettled(e.getStatus()) && appDate.isBefore(safeStart)) {
                BigDecimal appAmt = e.getApprovedAmount() != null ? e.getApprovedAmount() : e.getCommissionAmount();
                priorApproved = priorApproved.add(appAmt);
            }
        }

        BigDecimal priorSettled = BigDecimal.ZERO;
        for (CommissionPayout p : allPayouts) {
            if (p.getPaymentDate().isBefore(safeStart)) {
                if (p.getStatus() == CommissionPayoutStatus.SETTLED) {
                    priorSettled = priorSettled.add(p.getAmount());
                } else if (p.getStatus() == CommissionPayoutStatus.REVERSED && p.getPayoutType() == CommissionPayoutType.PAYOUT) {
                    // Reversed payout did not effectively settle
                    // We don't add it
                }
            }
        }
        BigDecimal openingSettlementPosition = priorApproved.subtract(priorSettled);

        // 2. Period Earnings & Flows
        BigDecimal periodEarned = BigDecimal.ZERO;
        BigDecimal periodApproved = BigDecimal.ZERO;
        List<SalespersonStatementResponse.StatementEarning> statementEarnings = new ArrayList<>();

        for (CommissionEarning e : allEarnings) {
            if (e.getStatus() == CommissionEarningStatus.VOID) continue;
            LocalDate earningDate = e.getCreatedAt().atZone(KARACHI_ZONE).toLocalDate();

            if (inRange(earningDate, safeStart, safeEnd)) {
                periodEarned = periodEarned.add(e.getCommissionAmount());

                BigDecimal appAmt = e.getApprovedAmount() != null ? e.getApprovedAmount() : e.getCommissionAmount();
                if (isApprovedOrSettled(e.getStatus())) {
                    periodApproved = periodApproved.add(appAmt);
                }

                statementEarnings.add(new SalespersonStatementResponse.StatementEarning(
                        e.getId(),
                        earningDate,
                        e.getCounterpartyId(),
                        customerNames.getOrDefault(e.getCounterpartyId(), "Customer " + e.getCounterpartyId()),
                        e.getSaleAmount(),
                        ruleDescriptions.getOrDefault(e.getRuleId(), "Standard Commission"),
                        e.getCommissionAmount(),
                        isApprovedOrSettled(e.getStatus()) ? appAmt : BigDecimal.ZERO,
                        e.getStatus().name(),
                        e.getNotes()
                ));
            }
        }

        // 3. Period Settlements
        BigDecimal periodAdvancesIssued = BigDecimal.ZERO;
        BigDecimal periodAdvancesApplied = BigDecimal.ZERO;
        BigDecimal periodGrossPayouts = BigDecimal.ZERO;
        BigDecimal periodPayoutReversals = BigDecimal.ZERO;
        List<SalespersonStatementResponse.StatementSettlement> statementSettlements = new ArrayList<>();

        for (CommissionAdvance a : allAdvances) {
            if (a.getStatus() == CommissionAdvanceStatus.VOID) continue;
            if (inRange(a.getPaymentDate(), safeStart, safeEnd)) {
                periodAdvancesIssued = periodAdvancesIssued.add(a.getAmount());
                statementSettlements.add(new SalespersonStatementResponse.StatementSettlement(
                        a.getId(),
                        a.getPaymentDate(),
                        "ADVANCE_ISSUED",
                        a.getAmount(),
                        a.getCurrency(),
                        a.getStatus().name(),
                        a.getFinancialTransactionId(),
                        a.getNotes() != null ? a.getNotes() : "Cash advance"
                ));
            }
        }

        for (CommissionPayout p : allPayouts) {
            if (!inRange(p.getPaymentDate(), safeStart, safeEnd)) continue;

            if (p.getPayoutType() == CommissionPayoutType.ADVANCE_APPLICATION) {
                if (p.getStatus() == CommissionPayoutStatus.SETTLED) {
                    periodAdvancesApplied = periodAdvancesApplied.add(p.getAmount());
                }
                statementSettlements.add(new SalespersonStatementResponse.StatementSettlement(
                        p.getId(),
                        p.getPaymentDate(),
                        "ADVANCE_APPLIED",
                        p.getAmount(),
                        p.getCurrency(),
                        p.getStatus().name(),
                        null,
                        p.getNotes() != null ? p.getNotes() : "Advance applied to earning"
                ));
            } else if (p.getPayoutType() == CommissionPayoutType.PAYOUT) {
                periodGrossPayouts = periodGrossPayouts.add(p.getAmount());
                statementSettlements.add(new SalespersonStatementResponse.StatementSettlement(
                        p.getId(),
                        p.getPaymentDate(),
                        "PAYOUT",
                        p.getAmount(),
                        p.getCurrency(),
                        p.getStatus().name(),
                        p.getFinancialTransactionId(),
                        p.getNotes() != null ? p.getNotes() : "Direct commission payout"
                ));

                if (p.getStatus() == CommissionPayoutStatus.REVERSED) {
                    periodPayoutReversals = periodPayoutReversals.add(p.getAmount());
                    statementSettlements.add(new SalespersonStatementResponse.StatementSettlement(
                        p.getId(),
                        p.getPaymentDate(),
                        "PAYOUT_REVERSED",
                        p.getAmount(),
                        p.getCurrency(),
                        CommissionPayoutStatus.REVERSED.name(),
                        p.getReversalTransactionId(),
                        p.getReversalReason() != null ? p.getReversalReason() : "Payout reversed"
                    ));
                }
            }
        }

        BigDecimal periodNetPayouts = periodGrossPayouts.subtract(periodPayoutReversals);
        BigDecimal periodTotalSettled = periodAdvancesApplied.add(periodNetPayouts);
        BigDecimal closingSettlementPosition = openingSettlementPosition.add(periodApproved).subtract(periodTotalSettled);

        BigDecimal unappliedAdvanceBalance = advanceRepository.sumUnappliedAdvancesBySalesperson(businessId, effectiveSalespersonId);

        statementEarnings.sort(Comparator.comparing(SalespersonStatementResponse.StatementEarning::date).reversed());
        statementSettlements.sort(Comparator.comparing(SalespersonStatementResponse.StatementSettlement::date).reversed());

        String notes = "Certified FinSight Commission Statement. Cash movements correspond to double-entry ledger postings. Advances are reported on a cash-disbursed basis and reduce payable obligations upon application.";

        return new SalespersonStatementResponse(
                businessId,
                businessName,
                effectiveSalespersonId,
                sp.getDisplayName(),
                sp.getEmail(),
                sp.getPhone(),
                safeStart,
                safeEnd,
                Instant.now(),
                scale(openingSettlementPosition),
                scale(periodEarned),
                scale(periodApproved),
                scale(periodAdvancesIssued),
                scale(periodAdvancesApplied),
                scale(periodGrossPayouts),
                scale(periodPayoutReversals),
                scale(periodNetPayouts),
                scale(periodTotalSettled),
                scale(closingSettlementPosition),
                scale(unappliedAdvanceBalance),
                "PKR",
                statementCustomers,
                statementEarnings,
                statementSettlements,
                notes
        );
    }

    /**
     * Generates CSV export byte array with formula injection protection.
     */
    public byte[] exportStatementCsv(
            BusinessAccessContext context,
            UUID requestedSalespersonId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        SalespersonStatementResponse stmt = getSalespersonStatement(context, requestedSalespersonId, startDate, endDate);

        StringBuilder sb = new StringBuilder();
        // Header
        sb.append("FINSIGHT COMMISSION STATEMENT\n");
        sb.append("Business,").append(escapeCsv(stmt.businessName())).append("\n");
        sb.append("Salesperson,").append(escapeCsv(stmt.salespersonName())).append("\n");
        sb.append("Email,").append(escapeCsv(stmt.salespersonEmail())).append("\n");
        sb.append("Period,").append(stmt.startDate()).append(" to ").append(stmt.endDate()).append("\n");
        sb.append("Generated,").append(stmt.generatedAt().atZone(KARACHI_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append("\n");
        sb.append("Currency,").append(stmt.currency()).append("\n\n");

        // Summary
        sb.append("SETTLEMENT SUMMARY\n");
        sb.append("Metric,Amount (PKR)\n");
        sb.append("Opening Settlement Position,").append(stmt.openingSettlementPosition()).append("\n");
        sb.append("Period Earned,").append(stmt.periodEarned()).append("\n");
        sb.append("Period Approved,").append(stmt.periodApproved()).append("\n");
        sb.append("Period Advances Issued,").append(stmt.periodAdvancesIssued()).append("\n");
        sb.append("Period Advances Applied,").append(stmt.periodAdvancesApplied()).append("\n");
        sb.append("Period Direct Payouts,").append(stmt.periodGrossPayouts()).append("\n");
        sb.append("Period Payout Reversals,").append(stmt.periodPayoutReversals()).append("\n");
        sb.append("Period Net Payouts,").append(stmt.periodNetPayouts()).append("\n");
        sb.append("Period Total Settled,").append(stmt.periodTotalSettled()).append("\n");
        sb.append("Closing Settlement Position,").append(stmt.closingSettlementPosition()).append("\n");
        sb.append("Unapplied Advance Balance,").append(stmt.unappliedAdvanceBalance()).append("\n\n");

        // Earnings
        sb.append("COMMISSION EARNINGS\n");
        sb.append("Date,Customer,Collection Amount,Rule,Earned Amount,Approved Amount,Status,Notes\n");
        for (SalespersonStatementResponse.StatementEarning e : stmt.earnings()) {
            sb.append(e.date()).append(",")
                    .append(escapeCsv(e.counterpartyName())).append(",")
                    .append(e.collectionAmount()).append(",")
                    .append(escapeCsv(e.ruleDescription())).append(",")
                    .append(e.earnedAmount()).append(",")
                    .append(e.approvedAmount()).append(",")
                    .append(escapeCsv(e.status())).append(",")
                    .append(escapeCsv(e.notes())).append("\n");
        }
        sb.append("\n");

        // Settlements
        sb.append("SETTLEMENTS & ADVANCES\n");
        sb.append("Date,Type,Amount,Currency,Status,Transaction ID,Notes\n");
        for (SalespersonStatementResponse.StatementSettlement s : stmt.settlements()) {
            sb.append(s.date()).append(",")
                    .append(escapeCsv(s.settlementType())).append(",")
                    .append(s.amount()).append(",")
                    .append(s.currency()).append(",")
                    .append(escapeCsv(s.status())).append(",")
                    .append(escapeCsv(s.linkedTransactionId() != null ? s.linkedTransactionId().toString() : "")).append(",")
                    .append(escapeCsv(s.notes())).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Resolves and authorizes salesperson ID based on role.
     * If the caller is a SALESPERSON, they can ONLY view their own statement.
     */
    public UUID resolveEffectiveSalespersonId(BusinessAccessContext context, UUID requestedSalespersonId) {
        if (context == null) {
            throw new AccessDeniedException("Unauthorized context");
        }

        Optional<SalespersonProfile> currentProfile = salespersonRepository.findByBusinessIdAndUserId(
                context.businessId(), context.userId()
        );

        // If active user is a SALESPERSON membership role
        boolean isSalespersonRole = context.role() == MembershipRole.SALESPERSON;

        if (isSalespersonRole) {
            SalespersonProfile sp = currentProfile.orElseThrow(() ->
                    new AccessDeniedException("No salesperson profile associated with active user"));

            if (sp.getStatus() != SalespersonStatus.ACTIVE) {
                throw new AccessDeniedException("Salesperson account is inactive or suspended");
            }

            // Salespeople can only ever query themselves
            if (requestedSalespersonId != null && !requestedSalespersonId.equals(sp.getId())) {
                throw new AccessDeniedException("Salespeople are not authorized to view other salespersons' statements");
            }
            return sp.getId();
        }

        // For OWNER, ACCOUNTANT, MANAGER
        if (requestedSalespersonId == null) {
            throw new IllegalArgumentException("Salesperson ID must be specified");
        }

        return requestedSalespersonId;
    }

    /**
     * Escapes CSV values and protects against CSV formula injection.
     */
    public static String escapeCsv(String value) {
        if (value == null) return "";
        String sanitized = value.trim();

        // Formula injection protection: if string begins with =, +, -, @, prepend a single quote
        if (!sanitized.isEmpty()) {
            char first = sanitized.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@') {
                sanitized = "'" + sanitized;
            }
        }

        // Quote and escape if contains comma, quote, or newline
        if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n") || sanitized.contains("\r")) {
            sanitized = "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }

    private boolean inRange(LocalDate date, LocalDate start, LocalDate end) {
        if (date == null) return false;
        if (start != null && date.isBefore(start)) return false;
        if (end != null && date.isAfter(end)) return false;
        return true;
    }

    private boolean isApprovedOrSettled(CommissionEarningStatus s) {
        return s == CommissionEarningStatus.APPROVED ||
                s == CommissionEarningStatus.PARTIALLY_SETTLED ||
                s == CommissionEarningStatus.SETTLED ||
                s == CommissionEarningStatus.PAID;
    }

    private BigDecimal scale(BigDecimal val) {
        return (val != null) ? val.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleOrNull(BigDecimal val) {
        return (val != null) ? val.setScale(2, RoundingMode.HALF_UP) : null;
    }
}
