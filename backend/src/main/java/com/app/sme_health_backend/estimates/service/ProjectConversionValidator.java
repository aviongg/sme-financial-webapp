package com.app.sme_health_backend.estimates.service;

import com.app.sme_health_backend.estimates.dto.PricingPreviewRequest.PricingMode;
import com.app.sme_health_backend.estimates.model.EstimateRevisionRecord;
import com.app.sme_health_backend.estimates.service.EstimateCostCalculator.RawLine;
import com.app.sme_health_backend.projects.dto.ProjectBudgetLineRequest;
import com.app.sme_health_backend.projects.dto.ProjectBudgetRequest;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure exact-fit gate from a persisted f06-preview-v1 revision to the existing F05 V33 planning budget.
 * A value converts only when F05 can hold the identical number: there is no rounding, truncation or
 * adjustment of any kind (P10 remains open). Trailing zeros are not precision, so 1.500000000000 is the
 * value 1.5; every other digit must survive. Capacities mirror V33 and ProjectDecimals exactly.
 */
public final class ProjectConversionValidator {
    private ProjectConversionValidator() { }
    public static final String CODE = "conversion_precision_unrepresentable";

    public record Target(String column, int precision, int scale, int maxIntegerDigits) { }
    public record Issue(Integer lineNumber, String field, String sourceValue, String reason, Target target) { }
    public record Conversion(String plannedRevenue, List<ProjectBudgetLineRequest> lines) {
        public ProjectBudgetRequest request(long expectedProjectVersion) {
            return new ProjectBudgetRequest(expectedProjectVersion, plannedRevenue, lines);
        }
    }

    static final Target QUANTITY = new Target("project_budget_lines.quantity", 15, 6, 9);
    static final Target UNIT_COST = new Target("project_budget_lines.unit_cost", 18, 6, 12);
    static final Target LINE_COST = new Target("project_budget_lines.planned_line_cost", 33, 12, 21);
    static final Target PLANNED_REVENUE = new Target("project_budget_versions.planned_revenue", 16, 4, 12);
    static final Target PLANNED_COST = new Target("project_budget_versions.planned_cost", 35, 12, 23);
    static final Target PLANNED_MARGIN = new Target("project_budget_versions.planned_margin", 35, 12, 23);

    public static List<Issue> validate(EstimateRevisionRecord revision) {
        List<Issue> issues = new ArrayList<>();
        List<RawLine> lines = revision.lines();
        for (int index = 0; index < lines.size(); index++) {
            RawLine line = lines.get(index);
            String path = "lines[" + index + "]";
            check(issues, index + 1, path + ".quantity", line.quantity(), QUANTITY);
            check(issues, index + 1, path + ".unitCost", line.unitCost(), UNIT_COST);
            check(issues, index + 1, path + ".plannedCost", line.plannedCost(), LINE_COST);
        }
        BigDecimal price = revision.plannedBatchPrice();
        // A target-margin price is a scale-24 quotient. It is the exact price only if it reproduces the cost.
        if (revision.pricingMode() == PricingMode.TARGET_MARGIN
                && price.multiply(BigDecimal.ONE.subtract(revision.pricingValue())).compareTo(revision.totalPlannedCost()) != 0) {
            issues.add(new Issue(null, "plannedBatchPrice", EstimateDecimals.decimal(price), "inexact_division", PLANNED_REVENUE));
        } else {
            check(issues, null, "plannedBatchPrice", price, PLANNED_REVENUE);
        }
        check(issues, null, "totalPlannedCost", revision.totalPlannedCost(), PLANNED_COST);
        // F05 derives its own margin (revenue - cost); it is only reported when the inputs above already fit,
        // so one unrepresentable input is not echoed as a second issue.
        if (issues.isEmpty()) check(issues, null, "plannedContribution", revision.plannedContribution(), PLANNED_MARGIN);
        return List.copyOf(issues);
    }

    /** The F05 budget inputs as exact plain decimal strings; only callable when {@link #validate} is empty. */
    public static Conversion convert(EstimateRevisionRecord revision) {
        List<Issue> issues = validate(revision);
        if (!issues.isEmpty()) throw new ConversionPrecisionException(issues);
        List<ProjectBudgetLineRequest> lines = revision.lines().stream().map(line -> new ProjectBudgetLineRequest(
                ProjectCostCategory.valueOf(line.costGroup().name()), line.description(), plain(line.quantity()), line.unit(),
                plain(line.unitCost()))).toList();
        return new Conversion(plain(revision.plannedBatchPrice()), lines);
    }

    private static void check(List<Issue> issues, Integer lineNumber, String field, BigDecimal value, Target target) {
        BigDecimal stripped = value.stripTrailingZeros();
        int scale = Math.max(stripped.scale(), 0);
        int integerDigits = Math.max(stripped.precision() - stripped.scale(), 1);
        if (scale > target.scale())
            issues.add(new Issue(lineNumber, field, EstimateDecimals.decimal(value), "scale_exceeded", target));
        else if (integerDigits > target.maxIntegerDigits())
            issues.add(new Issue(lineNumber, field, EstimateDecimals.decimal(value), "integer_digits_exceeded", target));
    }
    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
}
