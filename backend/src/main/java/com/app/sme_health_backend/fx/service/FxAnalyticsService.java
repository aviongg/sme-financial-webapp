package com.app.sme_health_backend.fx.service;

import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.fx.dto.*;
import com.app.sme_health_backend.fx.entity.FxObligationRecognitionEvidence;
import com.app.sme_health_backend.fx.entity.FxObligationSettlement;
import com.app.sme_health_backend.fx.model.*;
import com.app.sme_health_backend.fx.repository.FxObligationRecognitionEvidenceRepository;
import com.app.sme_health_backend.fx.repository.FxObligationSettlementRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.obligation.*;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FxAnalyticsService {

    private final FxObligationSettlementRepository settlementRepository;
    private final FxObligationRecognitionEvidenceRepository recognitionEvidenceRepository;
    private final FinancialObligationRepository obligationRepository;
    private final ObligationPaymentAllocationRepository allocationRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final CurrencyMetadataService currencyMetadataService;
    private final FxSettlementService settlementService;

    public FxAnalyticsService(
            FxObligationSettlementRepository settlementRepository,
            FxObligationRecognitionEvidenceRepository recognitionEvidenceRepository,
            FinancialObligationRepository obligationRepository,
            ObligationPaymentAllocationRepository allocationRepository,
            CounterpartyRepository counterpartyRepository,
            CurrencyMetadataService currencyMetadataService,
            FxSettlementService settlementService
    ) {
        this.settlementRepository = settlementRepository;
        this.recognitionEvidenceRepository = recognitionEvidenceRepository;
        this.obligationRepository = obligationRepository;
        this.allocationRepository = allocationRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.currencyMetadataService = currencyMetadataService;
        this.settlementService = settlementService;
    }

    @Transactional(readOnly = true)
    public List<CurrencyExposureSummary> getCurrencyExposures(BusinessAccessContext context) {
        List<FinancialObligation> allObligations = obligationRepository.findByBusinessId(context.businessId())
                .stream()
                .filter(o -> o.getStatus() != ObligationStatus.VOIDED)
                .toList();

        List<FxObligationSettlement> allSettlements = settlementRepository
                .findAllByBusinessIdOrderBySettlementDateDescCreatedAtDesc(context.businessId());

        // Batch fetch allocations to accurately compute remaining balances
        List<UUID> obIds = allObligations.stream().map(FinancialObligation::getId).toList();
        Map<UUID, BigDecimal> paidMap = new HashMap<>();
        if (!obIds.isEmpty()) {
            for (Object[] row : allocationRepository.sumEffectiveAllocationsByObligationIds(context.businessId(), obIds)) {
                paidMap.put((UUID) row[0], (BigDecimal) row[1]);
            }
        }

        // Group obligations by currency
        Set<String> activeCurrencies = new LinkedHashSet<>();
        allObligations.forEach(o -> activeCurrencies.add(o.getCurrency().toUpperCase(Locale.ROOT)));
        allSettlements.forEach(s -> activeCurrencies.add(s.getObligationCurrency().toUpperCase(Locale.ROOT)));

        List<CurrencyExposureSummary> exposures = new ArrayList<>();
        for (String curr : activeCurrencies) {
            Optional<CurrencyMetadata> metaOpt = currencyMetadataService.getCurrency(curr);
            String displayName = metaOpt.map(CurrencyMetadata::displayName).orElse(curr);
            String symbol = metaOpt.map(CurrencyMetadata::symbol).orElse(curr);
            int minorUnits = metaOpt.map(CurrencyMetadata::minorUnit).orElse(2);

            BigDecimal outstandingRec = BigDecimal.ZERO;
            BigDecimal outstandingPay = BigDecimal.ZERO;
            int totalObCount = 0;

            for (FinancialObligation ob : allObligations) {
                if (ob.getCurrency().equalsIgnoreCase(curr)) {
                    totalObCount++;
                    BigDecimal paid = paidMap.getOrDefault(ob.getId(), BigDecimal.ZERO);
                    BigDecimal outstanding = ob.getOriginalAmount().subtract(paid);
                    if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                        if (ob.getDirection() == ObligationDirection.RECEIVABLE) {
                            outstandingRec = outstandingRec.add(outstanding);
                        } else {
                            outstandingPay = outstandingPay.add(outstanding);
                        }
                    }
                }
            }

            BigDecimal settledRec = BigDecimal.ZERO;
            BigDecimal settledPay = BigDecimal.ZERO;
            int totalSettleCount = 0;

            for (FxObligationSettlement s : allSettlements) {
                if (s.getObligationCurrency().equalsIgnoreCase(curr)) {
                    totalSettleCount++;
                    if (s.getStatus() == FxSettlementStatus.SETTLED) {
                        if (s.getSettlementDirection() == SettlementDirection.RECEIVABLE_COLLECTION) {
                            settledRec = settledRec.add(s.getObligationAmount());
                        } else {
                            settledPay = settledPay.add(s.getObligationAmount());
                        }
                    }
                }
            }

            BigDecimal netExposure = outstandingRec.subtract(outstandingPay);

            exposures.add(new CurrencyExposureSummary(
                    curr,
                    displayName,
                    symbol,
                    minorUnits,
                    currencyMetadataService.round(curr, outstandingRec),
                    currencyMetadataService.round(curr, outstandingPay),
                    currencyMetadataService.round(curr, netExposure),
                    currencyMetadataService.round(curr, settledRec),
                    currencyMetadataService.round(curr, settledPay),
                    totalObCount,
                    totalSettleCount
            ));
        }

        return exposures;
    }

    @Transactional(readOnly = true)
    public List<FxPeriodTrendSummary> getPeriodicTrends(BusinessAccessContext context) {
        return getPeriodicTrends(context, "PKR");
    }

    @Transactional(readOnly = true)
    public List<FxPeriodTrendSummary> getPeriodicTrends(BusinessAccessContext context, String reportingCurrency) {
        final String repCurr = (reportingCurrency != null && !reportingCurrency.isBlank()) ? reportingCurrency : "PKR";
        List<FxObligationSettlement> allSettlements = settlementRepository
                .findAllByBusinessIdOrderBySettlementDateDescCreatedAtDesc(context.businessId());

        DateTimeFormatter periodFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, List<FxObligationSettlement>> byPeriod = allSettlements.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getSettlementDate().format(periodFormatter),
                        TreeMap::new,
                        Collectors.toList()
                ));

        List<FxPeriodTrendSummary> trends = new ArrayList<>();
        for (Map.Entry<String, List<FxObligationSettlement>> entry : byPeriod.entrySet()) {
            String period = entry.getKey();
            List<FxObligationSettlement> list = entry.getValue();

            // Filter settlements strictly to those whose account currency matches the reporting currency policy
            List<FxObligationSettlement> active = list.stream()
                    .filter(s -> s.getStatus() == FxSettlementStatus.SETTLED && (s.getAccountCurrency() == null || s.getAccountCurrency().equalsIgnoreCase(repCurr)))
                    .toList();

            int count = active.size();
            BigDecimal principal = currencyMetadataService.round(repCurr, active.stream()
                    .map(FxObligationSettlement::getConvertedPrincipal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            BigDecimal gains = currencyMetadataService.round(repCurr, active.stream()
                    .filter(s -> s.getRealizedFxImpact() == RealizedFxImpact.GAIN && s.getRealizedFxDifference() != null)
                    .map(FxObligationSettlement::getRealizedFxDifference)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            BigDecimal losses = currencyMetadataService.round(repCurr, active.stream()
                    .filter(s -> s.getRealizedFxImpact() == RealizedFxImpact.LOSS && s.getRealizedFxDifference() != null)
                    .map(FxObligationSettlement::getRealizedFxDifference)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            BigDecimal netFx = currencyMetadataService.round(repCurr, gains.subtract(losses));

            BigDecimal fees = currencyMetadataService.round(repCurr, active.stream()
                    .map(FxObligationSettlement::getFeeAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            BigDecimal netCash = currencyMetadataService.round(repCurr, active.stream()
                    .map(s -> s.getSettlementDirection() == SettlementDirection.RECEIVABLE_COLLECTION
                            ? s.getTotalAccountMovement()
                            : s.getTotalAccountMovement().negate())
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            trends.add(new FxPeriodTrendSummary(
                    period,
                    count,
                    principal,
                    gains,
                    losses,
                    netFx,
                    fees,
                    netCash
            ));
        }

        return trends;
    }

    @Transactional(readOnly = true)
    public FxAnalyticsOverviewResponse getAnalyticsOverview(BusinessAccessContext context) {
        List<CurrencyExposureSummary> exposures = getCurrencyExposures(context);
        FxPeriodSummaryResponse performance = settlementService.getPeriodSummary(context, null, null);
        List<FxPeriodTrendSummary> trends = getPeriodicTrends(context);

        List<FinancialObligation> foreignObligations = obligationRepository.findByBusinessId(context.businessId())
                .stream()
                .filter(o -> !o.getCurrency().equalsIgnoreCase("PKR") && o.getStatus() != ObligationStatus.VOIDED)
                .toList();

        int totalForeign = foreignObligations.size();
        int openForeign = (int) foreignObligations.stream().filter(o -> o.getStatus() == ObligationStatus.OPEN).count();

        return new FxAnalyticsOverviewResponse(
                context.businessId(),
                "PKR",
                exposures,
                performance,
                trends,
                totalForeign,
                openForeign,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public List<FxSettlementReportItem> getSettlementsReport(BusinessAccessContext context, FxSettlementReportFilter filter) {
        List<FxObligationSettlement> all = settlementRepository
                .findAllByBusinessIdOrderBySettlementDateDescCreatedAtDesc(context.businessId());

        // Batch load obligations and counterparties
        Map<UUID, FinancialObligation> obligationMap = obligationRepository.findByBusinessId(context.businessId())
                .stream().collect(Collectors.toMap(FinancialObligation::getId, o -> o, (a, b) -> a));

        Map<UUID, Counterparty> counterpartyMap = counterpartyRepository.findByBusinessId(context.businessId())
                .stream().collect(Collectors.toMap(Counterparty::getId, c -> c, (a, b) -> a));

        Map<UUID, FxObligationRecognitionEvidence> evidenceMap = recognitionEvidenceRepository.findAll()
                .stream()
                .filter(e -> e.getBusinessId().equals(context.businessId()))
                .collect(Collectors.toMap(FxObligationRecognitionEvidence::getObligationId, e -> e, (a, b) -> a));

        return all.stream()
                .filter(s -> filter.currency() == null || s.getObligationCurrency().equalsIgnoreCase(filter.currency()))
                .filter(s -> filter.direction() == null || s.getSettlementDirection() == filter.direction())
                .filter(s -> filter.status() == null || s.getStatus() == filter.status())
                .filter(s -> filter.evidenceStatus() == null || s.getRecognitionEvidenceStatus() == filter.evidenceStatus())
                .filter(s -> filter.startDate() == null || !s.getSettlementDate().isBefore(filter.startDate()))
                .filter(s -> filter.endDate() == null || !s.getSettlementDate().isAfter(filter.endDate()))
                .filter(s -> {
                    if (filter.counterpartyId() == null) return true;
                    FinancialObligation ob = obligationMap.get(s.getObligationId());
                    return ob != null && filter.counterpartyId().equals(ob.getCounterpartyId());
                })
                .skip(filter.offset() != null ? filter.offset() : 0)
                .limit(filter.limit() != null ? filter.limit() : 50)
                .map(s -> {
                    FinancialObligation ob = obligationMap.get(s.getObligationId());
                    Counterparty cp = (ob != null) ? counterpartyMap.get(ob.getCounterpartyId()) : null;
                    FxObligationRecognitionEvidence evidence = evidenceMap.get(s.getObligationId());

                    String cpName = (cp != null) ? cp.getDisplayName() : "Unknown Counterparty";
                    UUID cpId = (ob != null) ? ob.getCounterpartyId() : null;
                    String refNum = (ob != null) ? ob.getReferenceNumber() : null;

                    BigDecimal recognitionRate = (evidence != null) ? evidence.getRecognitionRate() : null;
                    RecognitionProvenance provenance = (evidence != null)
                            ? RecognitionProvenance.fromStringOrDefault(evidence.getEvidenceProvenance())
                            : RecognitionProvenance.UNAVAILABLE;

                    BigDecimal netVariance = (s.getAllocatedBookValue() != null)
                            ? s.getTotalAccountMovement().subtract(s.getAllocatedBookValue())
                            : null;

                    String feeTreatment = s.getSettlementDirection() == SettlementDirection.PAYABLE_SETTLEMENT
                            ? "FEE_ADDED_TO_CASH_DEBIT"
                            : "FEE_DEDUCTED_FROM_CASH_CREDIT";

                    boolean cashReconciled = s.getSettlementDirection() == SettlementDirection.PAYABLE_SETTLEMENT
                            ? s.getConvertedPrincipal().add(s.getFeeAmount()).compareTo(s.getTotalAccountMovement()) == 0
                            : s.getConvertedPrincipal().subtract(s.getFeeAmount()).compareTo(s.getTotalAccountMovement()) == 0;

                    return new FxSettlementReportItem(
                            s.getId(),
                            s.getSettlementDate(),
                            s.getSettlementDirection(),
                            cpId,
                            cpName,
                            s.getObligationId(),
                            refNum,
                            s.getObligationCurrency(),
                            s.getObligationAmount(),
                            s.getAccountId(),
                            s.getAccountCurrency(),
                            s.getConvertedPrincipal(),
                            s.getFeeAmount(),
                            s.getFeeCurrency(),
                            feeTreatment,
                            s.getTotalAccountMovement(),
                            s.getExchangeRate(),
                            s.getRateSource(),
                            recognitionRate,
                            s.getAllocatedBookValue(),
                            s.getRealizedFxDifference(),
                            s.getRealizedFxImpact(),
                            s.getRecognitionEvidenceStatus(),
                            provenance,
                            netVariance,
                            cashReconciled,
                            s.getStatus(),
                            s.getReversalTransactionId(),
                            s.getReversalReason()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public String exportSettlementsCsv(BusinessAccessContext context, FxSettlementReportFilter filter) {
        List<FxSettlementReportItem> items = getSettlementsReport(
                context,
                new FxSettlementReportFilter(
                        filter.currency(),
                        filter.counterpartyId(),
                        filter.direction(),
                        filter.status(),
                        filter.evidenceStatus(),
                        filter.startDate(),
                        filter.endDate(),
                        500, // export maximum
                        0
                )
        );

        StringWriter sw = new StringWriter();
        sw.append("Settlement ID,Date,Direction,Counterparty,Obligation Ref,Obligation Currency,Obligation Amount,Account Currency,Converted Principal,Bank Fee,Fee Treatment,Total Cash Movement,Exchange Rate,Book Value,Recognition Rate,Realized FX Difference,Realized FX Impact,Evidence Status,Provenance,Status,Reversal Reason\r\n");

        for (FxSettlementReportItem item : items) {
            sw.append(escapeCsv(item.settlementId() != null ? item.settlementId().toString() : "")).append(",");
            sw.append(escapeCsv(item.settlementDate() != null ? item.settlementDate().toString() : "")).append(",");
            sw.append(escapeCsv(item.direction() != null ? item.direction().name() : "")).append(",");
            sw.append(escapeCsv(item.counterpartyName() != null ? item.counterpartyName() : "")).append(",");
            sw.append(escapeCsv(item.referenceNumber() != null ? item.referenceNumber() : "")).append(",");
            sw.append(escapeCsv(item.obligationCurrency() != null ? item.obligationCurrency() : "")).append(",");
            sw.append(item.obligationAmount() != null ? item.obligationAmount().toPlainString() : "").append(",");
            sw.append(escapeCsv(item.accountCurrency() != null ? item.accountCurrency() : "")).append(",");
            sw.append(item.convertedPrincipal() != null ? item.convertedPrincipal().toPlainString() : "").append(",");
            sw.append(item.feeAmount() != null ? item.feeAmount().toPlainString() : "").append(",");
            sw.append(escapeCsv(item.feeTreatment() != null ? item.feeTreatment() : "")).append(",");
            sw.append(item.totalAccountMovement() != null ? item.totalAccountMovement().toPlainString() : "").append(",");
            sw.append(item.exchangeRate() != null ? item.exchangeRate().toPlainString() : "").append(",");
            sw.append(item.allocatedBookValue() != null ? item.allocatedBookValue().toPlainString() : "").append(",");
            sw.append(item.recognitionRate() != null ? item.recognitionRate().toPlainString() : "").append(",");
            sw.append(item.realizedFxDifference() != null ? item.realizedFxDifference().toPlainString() : "").append(",");
            sw.append(escapeCsv(item.realizedFxImpact() != null ? item.realizedFxImpact().name() : "")).append(",");
            sw.append(escapeCsv(item.recognitionEvidenceStatus() != null ? item.recognitionEvidenceStatus().name() : "")).append(",");
            sw.append(escapeCsv(item.recognitionProvenance() != null ? item.recognitionProvenance().name() : "")).append(",");
            sw.append(escapeCsv(item.status() != null ? item.status().name() : "")).append(",");
            sw.append(escapeCsv(item.reversalReason() != null ? item.reversalReason() : "")).append("\r\n");
        }

        return sw.toString();
    }

    /**
     * Sanitizes CSV values against formula injection (protecting against =, +, -, @ triggers).
     */
    private String escapeCsv(String value) {
        if (value == null) return "\"\"";
        String sanitized = value;
        if (sanitized.startsWith("=") || sanitized.startsWith("+") || sanitized.startsWith("-") || sanitized.startsWith("@")) {
            sanitized = "'" + sanitized;
        }
        if (sanitized.contains("\"") || sanitized.contains(",") || sanitized.contains("\n") || sanitized.contains("\r")) {
            sanitized = "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }
}
