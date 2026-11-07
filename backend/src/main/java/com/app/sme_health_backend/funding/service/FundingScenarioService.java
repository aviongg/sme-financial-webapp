package com.app.sme_health_backend.funding.service;

import com.app.sme_health_backend.financialquery.FinancialQueryFacade;
import com.app.sme_health_backend.financialquery.dto.CashPositionQuery;
import com.app.sme_health_backend.financialquery.dto.CashPositionResult;
import com.app.sme_health_backend.financialquery.dto.CurrencyCashSummary;
import com.app.sme_health_backend.financialquery.dto.FinancialQueryEnvelope;
import com.app.sme_health_backend.funding.dto.*;
import com.app.sme_health_backend.funding.exception.FundingScenarioValidationException;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service orchestrating financial funding scenario evaluations and comparisons.
 * <p>
 * Combines authoritative ledger cash positions (via {@link FinancialQueryFacade}) or
 * user-supplied manual assumptions with hypothetical planned cash flow events to produce
 * a deterministic comparison between baseline and alternative plans.
 */
@Service
public class FundingScenarioService {

    public static final String SUPPORTED_CURRENCY = "PKR";
    public static final int MIN_HORIZON_DAYS = 7;
    public static final int MAX_HORIZON_DAYS = 90;

    private final FinancialQueryFacade financialQuery;
    private final FundingScenarioCalculator calculator;

    @Autowired
    public FundingScenarioService(@Autowired(required = false) FinancialQueryFacade financialQuery) {
        this.financialQuery = financialQuery;
        this.calculator = new FundingScenarioCalculator();
    }

    /**
     * Internal constructor for tests injecting a custom calculator or mock facade.
     */
    public FundingScenarioService(FinancialQueryFacade financialQuery, FundingScenarioCalculator calculator) {
        this.financialQuery = financialQuery;
        this.calculator = calculator != null ? calculator : new FundingScenarioCalculator();
    }

    public FundingScenarioCompareResponse compareScenarios(
            BusinessAccessContext context,
            FundingScenarioCompareRequest request
    ) {
        if (request == null) {
            throw new FundingScenarioValidationException("request", "Request body cannot be null");
        }

        // 1. Validate Currency
        String currency = request.currency();
        if (currency == null || currency.isBlank()) {
            currency = SUPPORTED_CURRENCY;
        } else {
            currency = currency.trim().toUpperCase();
        }
        if (!SUPPORTED_CURRENCY.equals(currency)) {
            throw new FundingScenarioValidationException("currency",
                    "Unsupported currency '" + currency + "'; scenario engine currently supports " + SUPPORTED_CURRENCY + " only");
        }

        // 2. Validate Horizon
        int horizonDays = request.horizonDays() != null ? request.horizonDays() : 30;
        if (horizonDays < MIN_HORIZON_DAYS || horizonDays > MAX_HORIZON_DAYS) {
            throw new FundingScenarioValidationException("horizonDays",
                    "Horizon must be between " + MIN_HORIZON_DAYS + " and " + MAX_HORIZON_DAYS + " days, received: " + horizonDays);
        }

        // 3. Validate As-Of Date
        LocalDate asOfDate;
        if (request.asOfDate() == null || request.asOfDate().isBlank()) {
            asOfDate = LocalDate.now();
        } else {
            try {
                asOfDate = LocalDate.parse(request.asOfDate().trim());
            } catch (DateTimeParseException ex) {
                throw new FundingScenarioValidationException("asOfDate",
                        "Invalid asOfDate '" + request.asOfDate() + "'; expected YYYY-MM-DD");
            }
        }

        LocalDate horizonStart = asOfDate;
        LocalDate horizonEnd = asOfDate.plusDays(horizonDays);

        // 4. Resolve Opening Liquidity
        OpeningLiquidityRequest openingReq = request.openingLiquidity();
        if (openingReq == null) {
            throw new FundingScenarioValidationException("openingLiquidity", "Opening liquidity configuration is required");
        }
        if (openingReq.source() == null) {
            throw new FundingScenarioValidationException("openingLiquidity.source", "Opening liquidity source (LEDGER_CASH or MANUAL_ASSUMPTION) is required");
        }

        BigDecimal openingAmount;
        String sourceLabel;
        String openingNote;

        if (openingReq.source() == OpeningLiquiditySource.MANUAL_ASSUMPTION) {
            if (openingReq.manualAmount() == null) {
                throw new FundingScenarioValidationException("openingLiquidity.manualAmount",
                        "Manual opening amount is required when source is MANUAL_ASSUMPTION");
            }
            if (openingReq.manualAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new FundingScenarioValidationException("openingLiquidity.manualAmount",
                        "Manual opening amount must be non-negative (>= 0), received: " + openingReq.manualAmount());
            }
            try {
                openingAmount = openingReq.manualAmount().setScale(2, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException e) {
                throw new FundingScenarioValidationException("openingLiquidity.manualAmount",
                        "Manual opening amount must have at most 2 decimal places, received: " + openingReq.manualAmount());
            }
            sourceLabel = "MANUAL_ASSUMPTION";
            openingNote = openingReq.description() != null && !openingReq.description().isBlank()
                    ? openingReq.description().trim()
                    : "Hypothetical opening liquidity assumption provided by user.";
        } else {
            // LEDGER_CASH
            if (financialQuery == null) {
                throw new FundingScenarioValidationException("openingLiquidity.source",
                        "Authoritative ledger financial query facade is not available");
            }
            FinancialQueryEnvelope<CashPositionResult> envelope;
            try {
                envelope = financialQuery.getCashPosition(context, new CashPositionQuery(asOfDate.toString(), null));
            } catch (Exception ex) {
                throw new FundingScenarioValidationException("openingLiquidity.source",
                        "Failed to query authoritative ledger cash position: " + ex.getMessage());
            }

            if (envelope == null || envelope.status() != com.app.sme_health_backend.financialquery.model.CapabilityStatus.AVAILABLE || envelope.data() == null) {
                String reason = envelope != null && envelope.unavailableReason() != null
                        ? envelope.unavailableReason()
                        : "Ledger cash position is unavailable";
                throw new FundingScenarioValidationException("openingLiquidity.source", reason);
            }

            CurrencyCashSummary matchingSummary = null;
            for (CurrencyCashSummary cs : envelope.data().currencySummaries()) {
                if (currency.equalsIgnoreCase(cs.currency())) {
                    matchingSummary = cs;
                    break;
                }
            }

            if (matchingSummary == null || matchingSummary.currentBalance() == null) {
                throw new FundingScenarioValidationException("openingLiquidity.source",
                        "No authoritative ledger cash accounts found for currency " + currency);
            }

            openingAmount = new BigDecimal(matchingSummary.currentBalance()).setScale(2, RoundingMode.HALF_UP);
            sourceLabel = "POSTED_ACTUAL";
            openingNote = "Derived from posted double-entry financial ledger accounts as of " + asOfDate + ".";
        }

        OpeningLiquiditySummary openingSummary = new OpeningLiquiditySummary(
                sourceLabel,
                openingAmount.toPlainString(),
                openingNote
        );

        // 5. Calculate Baseline Scenario
        ScenarioDefinition baselineDef = request.baselineScenario();
        if (baselineDef == null) {
            throw new FundingScenarioValidationException("baselineScenario", "Baseline scenario definition is required");
        }

        ScenarioResult baselineResult = calculator.calculateScenario(
                baselineDef.scenarioName() != null ? baselineDef.scenarioName() : "Baseline Plan",
                openingAmount,
                horizonStart,
                horizonEnd,
                baselineDef.events()
        );

        // 6. Calculate Alternative Scenario (if provided)
        ScenarioResult alternativeResult = null;
        ScenarioComparisonSummary comparison = null;

        if (request.alternativeScenario() != null) {
            ScenarioDefinition altDef = request.alternativeScenario();
            alternativeResult = calculator.calculateScenario(
                    altDef.scenarioName() != null ? altDef.scenarioName() : "Alternative Plan",
                    openingAmount,
                    horizonStart,
                    horizonEnd,
                    altDef.events()
            );
            comparison = calculator.compareScenarios(baselineResult, alternativeResult);
        }

        // 7. Assemble Warnings and Limitations
        List<String> warnings = new ArrayList<>();
        if (openingReq.source() == OpeningLiquiditySource.LEDGER_CASH) {
            warnings.add("Opening liquidity reflects posted ledger cash; it does not constitute an approved credit line or guaranteed funding facility.");
        }
        if (baselineResult.hasDeficit()) {
            warnings.add("Funding gap detected: baseline scenario has a peak funding requirement of PKR "
                    + baselineResult.peakFundingRequirement() + " on " + baselineResult.peakDeficitDate() + ".");
            if (!baselineResult.isRecovered()) {
                warnings.add("Baseline scenario does not recover to non-negative territory within the "
                        + horizonDays + "-day evaluation horizon.");
            }
        }
        if (alternativeResult != null && alternativeResult.hasDeficit() && !alternativeResult.isRecovered()) {
            warnings.add("Alternative scenario does not recover to non-negative territory within the "
                    + horizonDays + "-day evaluation horizon.");
        }

        List<String> limitations = List.of(
                "UNPOSTED_SIMULATION: Planned cash flows are hypothetical simulations and have not been posted to the authoritative ledger.",
                "INTRA_DAY_NETTING: Same-day events are aggregated at end-of-day; intra-day liquidity may require earlier funding."
        );

        FundingScenarioProvenance provenance = new FundingScenarioProvenance(
                sourceLabel,
                "HYPOTHETICAL_SIMULATION",
                Instant.now().toString(),
                "funding-scenario-v1"
        );

        return new FundingScenarioCompareResponse(
                currency,
                asOfDate,
                horizonStart,
                horizonEnd,
                horizonDays,
                openingSummary,
                baselineResult,
                alternativeResult,
                comparison,
                provenance,
                warnings,
                limitations
        );
    }
}
