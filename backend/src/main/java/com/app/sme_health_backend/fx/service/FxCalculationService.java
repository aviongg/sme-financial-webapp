package com.app.sme_health_backend.fx.service;

import com.app.sme_health_backend.fx.dto.*;
import com.app.sme_health_backend.fx.exception.FxValidationException;
import com.app.sme_health_backend.fx.model.CurrencyMetadata;
import com.app.sme_health_backend.fx.model.ExchangeRateQuote;
import com.app.sme_health_backend.fx.model.FxDirection;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic pure Java foreign exchange calculation engine.
 * Authoritative financial math for currency conversions, direction-aware fee handling,
 * and rate scenario variance. Strictly read-only, non-posting calculation.
 */
@Service
public class FxCalculationService {

    private final CurrencyMetadataService currencyMetadataService;

    public FxCalculationService(CurrencyMetadataService currencyMetadataService) {
        this.currencyMetadataService = currencyMetadataService;
    }

    public FxPreviewResponse previewConversion(FxPreviewRequest request) {
        validateRequest(request);

        String baseCode = request.baseCurrency().trim().toUpperCase();
        String quoteCode = request.quoteCurrency().trim().toUpperCase();
        CurrencyMetadata baseMeta = currencyMetadataService.requireCurrency(baseCode);
        CurrencyMetadata quoteMeta = currencyMetadataService.requireCurrency(quoteCode);

        ExchangeRateQuote quote = new ExchangeRateQuote(
                baseCode,
                quoteCode,
                request.rate(),
                null,
                request.sourceType(),
                Instant.now(),
                null,
                null
        );

        BigDecimal reciprocalRate = quote.calculateReciprocalRate(8);

        String principalCurrency = request.principalCurrency().trim().toUpperCase();
        boolean isBasePrincipal = principalCurrency.equals(baseCode);
        String settlementCurrency = isBasePrincipal ? quoteCode : baseCode;
        CurrencyMetadata settlementMeta = isBasePrincipal ? quoteMeta : baseMeta;

        BigDecimal principalRounded = (isBasePrincipal ? baseMeta : quoteMeta).round(request.principalAmount());

        // 1. Gross converted amount in settlement currency
        BigDecimal grossSettlementAmount;
        if (isBasePrincipal) {
            // Converting Base -> Quote: Gross = Principal * Rate
            BigDecimal rawGross = principalRounded.multiply(request.rate());
            grossSettlementAmount = settlementMeta.round(rawGross);
        } else {
            // Converting Quote -> Base: Gross = Principal / Rate
            BigDecimal rawGross = principalRounded.divide(request.rate(), 12, RoundingMode.HALF_UP);
            grossSettlementAmount = settlementMeta.round(rawGross);
        }

        // 2. Fee Calculation in settlement currency
        String feeCurrency = request.feeCurrency() != null && !request.feeCurrency().isBlank()
                ? request.feeCurrency().trim().toUpperCase()
                : settlementCurrency;

        BigDecimal rawFixedFee = request.fixedFee() != null ? request.fixedFee() : BigDecimal.ZERO;
        BigDecimal fixedFeeInSettlement;
        if (feeCurrency.equals(settlementCurrency)) {
            fixedFeeInSettlement = settlementMeta.round(rawFixedFee);
        } else if (feeCurrency.equals(principalCurrency)) {
            if (isBasePrincipal) {
                // Fee is in Base, settlement is in Quote: fee * rate
                fixedFeeInSettlement = settlementMeta.round(rawFixedFee.multiply(request.rate()));
            } else {
                // Fee is in Quote, settlement is in Base: fee / rate
                fixedFeeInSettlement = settlementMeta.round(rawFixedFee.divide(request.rate(), 12, RoundingMode.HALF_UP));
            }
        } else {
            // Unsupported third-party fee currency
            throw new FxValidationException("Fee currency must match either principal currency or settlement currency: " + feeCurrency);
        }

        BigDecimal percentageFeePercent = request.percentageFeePercent() != null ? request.percentageFeePercent() : BigDecimal.ZERO;
        BigDecimal percentageFeeInSettlement = settlementMeta.round(
                grossSettlementAmount.multiply(percentageFeePercent).divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP)
        );

        BigDecimal totalFeeAmount = fixedFeeInSettlement.add(percentageFeeInSettlement);

        // 3. Direction-aware settlement amounts
        BigDecimal netSettlementAmount;
        BigDecimal totalSettlementDebited;
        BigDecimal effectiveRate;

        if (request.direction() == FxDirection.OUTGOING) {
            // Outgoing payment (e.g. paying overseas vendor):
            // Settlement account pays gross converted principal + fees
            totalSettlementDebited = grossSettlementAmount.add(totalFeeAmount);
            netSettlementAmount = grossSettlementAmount; // Target beneficiary receives the gross converted funds
            effectiveRate = isBasePrincipal
                    ? totalSettlementDebited.divide(principalRounded, 6, RoundingMode.HALF_UP)
                    : principalRounded.divide(totalSettlementDebited, 6, RoundingMode.HALF_UP);
        } else {
            // Incoming receipt (e.g. customer wire inward):
            // Settlement account receives gross converted principal minus fees
            netSettlementAmount = grossSettlementAmount.subtract(totalFeeAmount);
            totalSettlementDebited = grossSettlementAmount;
            effectiveRate = isBasePrincipal
                    ? netSettlementAmount.divide(principalRounded, 6, RoundingMode.HALF_UP)
                    : principalRounded.divide(netSettlementAmount, 6, RoundingMode.HALF_UP);
        }

        // 4. Rate Scenario Variance Comparison (if comparison rate provided)
        RateScenarioVariance scenarioVariance = null;
        if (request.comparisonRate() != null && request.comparisonRate().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal compRate = request.comparisonRate();
            BigDecimal compGross;
            if (isBasePrincipal) {
                compGross = settlementMeta.round(principalRounded.multiply(compRate));
            } else {
                compGross = settlementMeta.round(principalRounded.divide(compRate, 12, RoundingMode.HALF_UP));
            }

            BigDecimal compSettlement = request.direction() == FxDirection.OUTGOING
                    ? compGross.add(totalFeeAmount)
                    : compGross.subtract(totalFeeAmount);

            BigDecimal varianceAmount = compGross.subtract(grossSettlementAmount);
            BigDecimal variancePercentage = grossSettlementAmount.compareTo(BigDecimal.ZERO) > 0
                    ? varianceAmount.divide(grossSettlementAmount, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            String impact;
            int compResult = varianceAmount.compareTo(BigDecimal.ZERO);
            if (compResult == 0) {
                impact = "NEUTRAL";
            } else if (request.direction() == FxDirection.OUTGOING) {
                // Outgoing: higher rate = higher cost = UNFAVORABLE
                impact = compResult > 0 ? "UNFAVORABLE" : "FAVORABLE";
            } else {
                // Incoming: higher rate = higher proceeds = FAVORABLE
                impact = compResult > 0 ? "FAVORABLE" : "UNFAVORABLE";
            }

            scenarioVariance = new RateScenarioVariance(
                    compRate,
                    compGross,
                    compSettlement,
                    varianceAmount,
                    variancePercentage,
                    impact
            );
        }

        FxPreviewProvenance provenance = new FxPreviewProvenance(
                "PURE_JAVA_DETERMINISTIC_PREVIEW",
                "Deterministic non-posting calculation preview. No financial accounts, ledger transactions, or balances were altered.",
                Instant.now().toString()
        );

        return new FxPreviewResponse(
                baseCode,
                quoteCode,
                request.rate(),
                reciprocalRate,
                "1 " + baseCode + " = " + request.rate().toPlainString() + " " + quoteCode,
                request.sourceType(),
                request.direction(),
                principalRounded,
                principalCurrency,
                settlementCurrency,
                grossSettlementAmount,
                totalFeeAmount,
                fixedFeeInSettlement,
                percentageFeeInSettlement,
                settlementCurrency,
                netSettlementAmount,
                totalSettlementDebited,
                effectiveRate,
                scenarioVariance,
                provenance
        );
    }

    private void validateRequest(FxPreviewRequest req) {
        List<String> errors = new ArrayList<>();
        if (req == null) {
            throw new FxValidationException("Preview request body is required");
        }
        if (req.baseCurrency() == null || req.baseCurrency().isBlank()) {
            errors.add("baseCurrency is required");
        } else if (!currencyMetadataService.isSupported(req.baseCurrency())) {
            errors.add("baseCurrency '" + req.baseCurrency() + "' is not supported");
        }

        if (req.quoteCurrency() == null || req.quoteCurrency().isBlank()) {
            errors.add("quoteCurrency is required");
        } else if (!currencyMetadataService.isSupported(req.quoteCurrency())) {
            errors.add("quoteCurrency '" + req.quoteCurrency() + "' is not supported");
        }

        if (req.baseCurrency() != null && req.quoteCurrency() != null &&
                req.baseCurrency().trim().equalsIgnoreCase(req.quoteCurrency().trim())) {
            errors.add("baseCurrency and quoteCurrency cannot be identical");
        }

        if (req.rate() == null) {
            errors.add("rate is required");
        } else if (req.rate().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("rate must be strictly positive");
        }

        if (req.direction() == null) {
            errors.add("direction is required (OUTGOING or INCOMING)");
        }

        if (req.principalAmount() == null) {
            errors.add("principalAmount is required");
        } else if (req.principalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("principalAmount must be strictly positive");
        }

        if (req.principalCurrency() != null && !req.principalCurrency().isBlank()) {
            String pCurr = req.principalCurrency().trim();
            if (!pCurr.equalsIgnoreCase(req.baseCurrency()) && !pCurr.equalsIgnoreCase(req.quoteCurrency())) {
                errors.add("principalCurrency must be either baseCurrency or quoteCurrency");
            }
        }

        if (req.fixedFee() != null && req.fixedFee().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("fixedFee cannot be negative");
        }

        if (req.percentageFeePercent() != null) {
            if (req.percentageFeePercent().compareTo(BigDecimal.ZERO) < 0 ||
                    req.percentageFeePercent().compareTo(BigDecimal.valueOf(100)) > 0) {
                errors.add("percentageFeePercent must be between 0 and 100");
            }
        }

        if (req.comparisonRate() != null && req.comparisonRate().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("comparisonRate must be strictly positive");
        }

        if (!errors.isEmpty()) {
            throw new FxValidationException("FX Preview request validation failed: " + String.join("; ", errors), errors);
        }
    }
}

