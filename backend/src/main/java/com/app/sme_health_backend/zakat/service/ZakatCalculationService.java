package com.app.sme_health_backend.zakat.service;

import com.app.sme_health_backend.zakat.dto.ZakatPreviewRequest;
import com.app.sme_health_backend.zakat.dto.ZakatPreviewResponse;
import com.app.sme_health_backend.zakat.policy.ZakatPolicy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.app.sme_health_backend.zakat.dto.ZakatPreviewRequest.*;
import static com.app.sme_health_backend.zakat.dto.ZakatPreviewResponse.*;
import static com.app.sme_health_backend.zakat.dto.ZakatTypes.*;

@Service
public class ZakatCalculationService {
    private static final ZakatPolicy POLICY = ZakatPolicy.ACTIVE_PROFILE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public ZakatPreviewResponse calculate(ZakatPreviewRequest request) {
        return calculate(request, null);
    }

    public ZakatPreviewResponse calculate(ZakatPreviewRequest request, MonthlyRecordSource source) {
        if (request == null) {
            throw new IllegalArgumentException("Preview request is required");
        }
        Evaluation evaluation = new Evaluation();
        Assessment assessment = request.assessment() == null
                ? new Assessment(null, null, null, null, null, null, null, null) : request.assessment();
        Currency currency = validateAssessment(assessment, evaluation);
        BigDecimal nisab = assessment.metalWeightGrams() == null || assessment.metalPricePerGram() == null
                ? null : assessment.metalWeightGrams().multiply(assessment.metalPricePerGram());

        if (source != null && assessment.assessmentDate() != null
                && !assessment.assessmentDate().equals(source.balancesDate())) {
            evaluation.warnings.add("ASSESSMENT_DATE_DIFFERS_FROM_RECORD_MONTH_END");
        }
        if (source != null && request.assets() != null && request.assets().inventory() != null
                && source.inventoryValue() != null && request.assets().inventory().size() == 1
                && request.assets().inventory().getFirst() != null) {
            BigDecimal suppliedValue = request.assets().inventory().getFirst().amount();
            if (suppliedValue != null && suppliedValue.compareTo(source.inventoryValue()) != 0) {
                evaluation.warnings.add("INVENTORY_VALUATION_DIFFERS_FROM_RECORDED_VALUE");
            }
        }

        Financing financing = request.financing() == null ? new Financing(null, null, null) : request.financing();
        FinancingComplianceStatus financingStatus = evaluateFinancing(financing, evaluation);
        BigDecimal grossAssets = evaluateAssets(request.assets(), evaluation);
        BigDecimal liabilities = evaluateLiabilities(request.liabilities(), financing, evaluation);
        BigDecimal netAssets = grossAssets == null || liabilities == null
                ? null : grossAssets.subtract(liabilities).max(ZERO);

        if (evaluation.unsupported || evaluation.manualReview) {
            grossAssets = null;
            liabilities = null;
            netAssets = null;
        }
        Boolean nisabMet = netAssets == null || nisab == null ? null : netAssets.compareTo(nisab) >= 0;
        HaulStatus haul = assessment.haulStatus() == null ? HaulStatus.UNKNOWN : assessment.haulStatus();
        CalculationStatus status = determineStatus(evaluation, haul, nisabMet);
        BigDecimal unroundedDue = switch (status) {
            case CALCULATED -> netAssets.multiply(POLICY.zakatRate());
            case BELOW_NISAB, NOT_DUE_HAUL_NOT_COMPLETED -> ZERO;
            default -> null;
        };
        BigDecimal roundedDue = unroundedDue == null ? null
                : unroundedDue.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);

        return new ZakatPreviewResponse(
                POLICY.ruleProfile(), POLICY.ruleVersion(), assessment.assessmentDate(), assessment.currency(),
                status, POLICY.zakatRate(), POLICY.nisabMetal() + "_WEIGHT_X_PRICE_PER_GRAM", assessment.nisabMetal(),
                assessment.metalWeightGrams(), assessment.metalPricePerGram(), assessment.priceSource(),
                assessment.priceTimestamp(), nisab, haul,
                List.copyOf(evaluation.assets), List.copyOf(evaluation.liabilities),
                grossAssets, liabilities, netAssets, nisabMet, roundedDue, unroundedDue,
                List.copyOf(evaluation.missing), List.copyOf(evaluation.warnings), financingStatus,
                POLICY.debtPolicy(), ZakatPolicy.DISCLOSURE, request, source
        );
    }

    private Currency validateAssessment(Assessment assessment, Evaluation evaluation) {
        evaluation.require("assessment.assessmentDate", assessment.assessmentDate());
        evaluation.require("assessment.currency", assessment.currency());
        Currency currency = null;
        if (assessment.currency() != null && !assessment.currency().isBlank()) {
            try {
                currency = Currency.getInstance(assessment.currency());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("currency must be a supported ISO 4217 code");
            }
            if (currency.getDefaultFractionDigits() < 0) {
                throw new IllegalArgumentException("currency must have a standard monetary minor unit");
            }
        }
        evaluation.require("assessment.nisabMetal", assessment.nisabMetal());
        if (assessment.nisabMetal() != null && !assessment.nisabMetal().isBlank()
                && !POLICY.nisabMetal().equals(assessment.nisabMetal())) {
            throw new IllegalArgumentException(POLICY.ruleProfile() + " requires " + POLICY.nisabMetal());
        }
        evaluation.require("assessment.metalWeightGrams", assessment.metalWeightGrams());
        if (assessment.metalWeightGrams() != null
                && assessment.metalWeightGrams().compareTo(POLICY.metalWeightGrams()) != 0) {
            throw new IllegalArgumentException(POLICY.ruleProfile() + " requires " + POLICY.metalWeightGrams() + " grams");
        }
        evaluation.require("assessment.metalPricePerGram", assessment.metalPricePerGram());
        if (assessment.metalPricePerGram() != null && assessment.metalPricePerGram().signum() <= 0) {
            throw new IllegalArgumentException("metalPricePerGram must be positive");
        }
        evaluation.require("assessment.priceSource", assessment.priceSource());
        evaluation.require("assessment.priceTimestamp", assessment.priceTimestamp());
        if (assessment.assessmentDate() != null && assessment.priceTimestamp() != null
                && !assessment.assessmentDate().equals(assessment.priceTimestamp().toLocalDate())) {
            evaluation.warnings.add("PRICE_DATE_DIFFERS_FROM_ASSESSMENT_DATE");
        }
        if (assessment.haulStatus() == null || assessment.haulStatus() == HaulStatus.UNKNOWN) {
            evaluation.missing.add("assessment.haulStatus");
        }
        return currency;
    }

    private FinancingComplianceStatus evaluateFinancing(Financing financing, Evaluation evaluation) {
        nonNegative("financing.loanOutstanding", financing.loanOutstanding());
        nonNegative("financing.interestExpense", financing.interestExpense());
        if (positive(financing.interestExpense())) {
            evaluation.warnings.add("RIBA_RELATED_EXPENSE_RECORDED");
        } else if (financing.interestExpense() == null) {
            evaluation.warnings.add("INTEREST_EXPENSE_UNKNOWN");
        }
        if (financing.financingType() == null) {
            evaluation.warnings.add("FINANCING_CLASSIFICATION_UNKNOWN");
            return FinancingComplianceStatus.UNKNOWN;
        }
        return switch (financing.financingType()) {
            case "none" -> {
                if (positive(financing.loanOutstanding())) {
                    evaluation.warnings.add("NO_FINANCING_DECLARED_WITH_OUTSTANDING_LOAN");
                }
                yield FinancingComplianceStatus.NO_FINANCING_DECLARED;
            }
            case "islamic" -> FinancingComplianceStatus.USER_DECLARED_ISLAMIC;
            case "conventional" -> {
                evaluation.warnings.add("CONVENTIONAL_FINANCING_MAY_INVOLVE_RIBA");
                yield FinancingComplianceStatus.CONVENTIONAL_REQUIRES_REVIEW;
            }
            default -> throw new IllegalArgumentException("financingType must be one of: none, conventional, islamic");
        };
    }

    private BigDecimal evaluateAssets(Assets assets, Evaluation evaluation) {
        if (assets == null) {
            evaluation.missing.add("assets");
            return null;
        }
        nonNegative("assets.cashAndBankBalances", assets.cashAndBankBalances());
        evaluation.require("assets.cashAndBankBalances", assets.cashAndBankBalances());
        evaluation.assets.add(new Breakdown("CASH_AND_BANK", "business-cash", assets.cashAndBankBalances(),
                assets.cashAndBankBalances(), assets.cashAndBankBalances() == null ? "UNKNOWN" : "INCLUDED"));
        BigDecimal total = assets.cashAndBankBalances();
        Set<String> references = new HashSet<>();
        if (assets.inventory() == null) {
            evaluation.missing.add("assets.inventory");
            total = null;
        } else {
            for (int index = 0; index < assets.inventory().size(); index++) {
                InventoryItem item = assets.inventory().get(index);
                String path = "assets.inventory[" + index + "]";
                requireItem(item, path);
                uniqueReference(item.reference(), references, path + ".reference");
                nonNegative(path + ".amount", item.amount());
                evaluation.require(path + ".amount", item.amount());
                BigDecimal included = null;
                String treatment = "UNKNOWN";
                if (item.type() == InventoryType.FIXED_ASSET) {
                    included = ZERO;
                    treatment = "EXCLUDED_FIXED_ASSET";
                } else if (item.type() == InventoryType.RAW_MATERIAL || item.type() == InventoryType.WORK_IN_PROGRESS) {
                    evaluation.manualReview = true;
                    evaluation.warnings.add("INVENTORY_REQUIRES_MANUAL_REVIEW:" + item.reference());
                    treatment = "MANUAL_REVIEW_REQUIRED";
                } else if (item.type() == null || item.type() == InventoryType.UNKNOWN) {
                    evaluation.missing.add(path + ".type");
                } else {
                    evaluation.require(path + ".amount", item.amount());
                    if (item.valuation() != InventoryValuation.CURRENT_SELLING_VALUE) {
                        evaluation.missing.add(path + ".valuation");
                        evaluation.warnings.add("CURRENT_SELLING_VALUATION_REQUIRED:" + item.reference());
                    } else {
                        included = item.amount();
                        treatment = included == null ? "UNKNOWN" : "INCLUDED_RESALE_INVENTORY";
                    }
                }
                evaluation.assets.add(new Breakdown("INVENTORY", item.reference(), item.amount(), included, treatment));
                total = addKnown(total, included);
            }
        }
        if (assets.receivables() == null) {
            evaluation.missing.add("assets.receivables");
            total = null;
        } else {
            for (int index = 0; index < assets.receivables().size(); index++) {
                Receivable item = assets.receivables().get(index);
                String path = "assets.receivables[" + index + "]";
                requireItem(item, path);
                uniqueReference(item.reference(), references, path + ".reference");
                nonNegative(path + ".amount", item.amount());
                evaluation.require(path + ".amount", item.amount());
                BigDecimal included = null;
                String treatment = "UNKNOWN";
                if (item.classification() == null || item.classification() == ReceivableClassification.UNKNOWN) {
                    evaluation.missing.add(path + ".classification");
                } else {
                    switch (item.classification()) {
                        case GOOD, COLLECTIBLE -> {
                            evaluation.require(path + ".amount", item.amount());
                            included = item.amount();
                            treatment = included == null ? "UNKNOWN" : "INCLUDED_COLLECTIBLE";
                        }
                        case DOUBTFUL -> {
                            included = ZERO;
                            treatment = "EXCLUDED_DOUBTFUL";
                            evaluation.warnings.add("DOUBTFUL_RECEIVABLE_EXCLUDED:" + item.reference());
                        }
                        case BAD, UNRECOVERABLE -> {
                            included = ZERO;
                            treatment = "EXCLUDED_UNRECOVERABLE";
                        }
                        default -> throw new IllegalStateException("Unhandled receivable classification");
                    }
                }
                evaluation.assets.add(new Breakdown("RECEIVABLE", item.reference(), item.amount(), included, treatment));
                total = addKnown(total, included);
            }
        }
        if (assets.unsupportedCategories() != null) {
            for (String category : assets.unsupportedCategories()) {
                if (category == null || category.isBlank()) {
                    throw new IllegalArgumentException("unsupportedCategories must contain non-blank category names");
                }
                evaluation.unsupported = true;
                evaluation.warnings.add("UNSUPPORTED_ASSET_CATEGORY:" + category);
                evaluation.assets.add(new Breakdown(category, null, null, null, "UNSUPPORTED"));
            }
        }
        return total;
    }

    private BigDecimal evaluateLiabilities(Liabilities liabilities, Financing financing, Evaluation evaluation) {
        if (liabilities == null) {
            evaluation.missing.add("liabilities");
            return null;
        }
        nonNegative("liabilities.accountsPayable", liabilities.accountsPayable());
        evaluation.require("liabilities.accountsPayable", liabilities.accountsPayable());
        evaluation.require("financing.loanOutstanding", financing.loanOutstanding());
        Set<String> obligationIds = new HashSet<>();
        BigDecimal payables = evaluateDeductions(liabilities.currentPayables(), liabilities.accountsPayable(),
                "liabilities.currentPayables", "CURRENT_BUSINESS_PAYABLE", obligationIds, evaluation);
        BigDecimal principal = evaluateDeductions(liabilities.principalDueWithin12LunarMonths(), financing.loanOutstanding(),
                "liabilities.principalDueWithin12LunarMonths", "PRINCIPAL_DUE_WITHIN_12_LUNAR_MONTHS", obligationIds, evaluation);
        if (positive(financing.loanOutstanding()) && liabilities.principalDueWithin12LunarMonths() == null) {
            evaluation.warnings.add("LOAN_MATURITY_INPUT_REQUIRED_TOTAL_BALANCE_NOT_DEDUCTED");
        }
        if (positive(payables) && positive(principal)) {
            if (Boolean.FALSE.equals(liabilities.principalExcludedFromPayables())) {
                throw new IllegalArgumentException("Principal already included in payables must not be deducted again");
            }
            if (liabilities.principalExcludedFromPayables() == null) {
                evaluation.missing.add("liabilities.principalExcludedFromPayables");
                evaluation.warnings.add("LIABILITY_OVERLAP_CONFIRMATION_REQUIRED");
                return null;
            }
        }
        return addKnown(payables, principal);
    }

    private BigDecimal evaluateDeductions(List<LiabilityItem> items, BigDecimal outstanding,
                                          String path, String category, Set<String> ids, Evaluation evaluation) {
        if (items == null) {
            if (outstanding != null && outstanding.signum() == 0) {
                return ZERO;
            }
            evaluation.missing.add(path);
            return null;
        }
        BigDecimal total = ZERO;
        for (int index = 0; index < items.size(); index++) {
            LiabilityItem item = items.get(index);
            String itemPath = path + "[" + index + "]";
            requireItem(item, itemPath);
            uniqueReference(item.obligationId(), ids, itemPath + ".obligationId");
            nonNegative(itemPath + ".amount", item.amount());
            evaluation.require(itemPath + ".amount", item.amount());
            total = addKnown(total, item.amount());
            evaluation.liabilities.add(new Breakdown(category, item.obligationId(), item.amount(), item.amount(),
                    item.amount() == null ? "UNKNOWN" : "DEDUCTIBLE_CALLER_CONFIRMED"));
        }
        if (total != null && outstanding != null && total.compareTo(outstanding) > 0) {
            throw new IllegalArgumentException(path + " cannot exceed the corresponding outstanding balance");
        }
        return outstanding == null ? null : total;
    }

    private CalculationStatus determineStatus(Evaluation evaluation, HaulStatus haul, Boolean nisabMet) {
        if (evaluation.unsupported) {
            return CalculationStatus.UNSUPPORTED;
        }
        if (evaluation.manualReview) {
            return CalculationStatus.MANUAL_REVIEW_REQUIRED;
        }
        if (evaluation.missing.stream().anyMatch(field -> !field.equals("assessment.haulStatus"))) {
            return CalculationStatus.INCOMPLETE;
        }
        if (haul == HaulStatus.UNKNOWN) {
            return CalculationStatus.INCOMPLETE_HAUL_CONFIRMATION_REQUIRED;
        }
        if (haul == HaulStatus.NOT_COMPLETED) {
            return CalculationStatus.NOT_DUE_HAUL_NOT_COMPLETED;
        }
        return Boolean.TRUE.equals(nisabMet) ? CalculationStatus.CALCULATED : CalculationStatus.BELOW_NISAB;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static void nonNegative(String field, BigDecimal value) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }

    private static BigDecimal addKnown(BigDecimal left, BigDecimal right) {
        return left == null || right == null ? null : left.add(right);
    }

    private static void requireItem(Object item, String field) {
        if (item == null) {
            throw new IllegalArgumentException(field + " cannot be null");
        }
    }

    private static void uniqueReference(String reference, Set<String> references, String field) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (!reference.equals(reference.strip()) || !references.add(reference)) {
            throw new IllegalArgumentException(field + " must be unique and have no surrounding whitespace");
        }
    }

    private static final class Evaluation {
        private final Set<String> missing = new LinkedHashSet<>();
        private final Set<String> warnings = new LinkedHashSet<>();
        private final List<Breakdown> assets = new ArrayList<>();
        private final List<Breakdown> liabilities = new ArrayList<>();
        private boolean unsupported;
        private boolean manualReview;

        private void require(String field, Object value) {
            if (value == null || value instanceof String text && text.isBlank()) {
                missing.add(field);
            }
        }
    }
}
