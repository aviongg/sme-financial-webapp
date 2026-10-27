package com.app.sme_health_backend.projects.service;

import com.app.sme_health_backend.projects.dto.ProjectBudgetCalculationResponse;
import com.app.sme_health_backend.projects.dto.ProjectBudgetCalculationResponse.Calculation;
import com.app.sme_health_backend.projects.dto.ProjectBudgetCalculationResponse.Line;
import com.app.sme_health_backend.projects.dto.ProjectBudgetCalculationResponse.RawTotals;
import com.app.sme_health_backend.projects.dto.ProjectBudgetCalculationResponse.UnavailableReasons;
import com.app.sme_health_backend.projects.dto.ProjectBudgetLineRequest;
import com.app.sme_health_backend.projects.dto.ProjectCreateRequest;
import com.app.sme_health_backend.projects.dto.ProjectTransitionRequest;
import com.app.sme_health_backend.projects.dto.ProjectUpdateRequest;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
import com.app.sme_health_backend.projects.model.ProjectKind;
import com.app.sme_health_backend.projects.model.ProjectStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Authoritative stateless planning calculator and validator for F05 Phase A.
 * Computes plannedLineCost, plannedCost, plannedMargin, and plannedMarginPct using exact BigDecimal arithmetic.
 * Never reads or mutates MonthlyRecord, ScoringService, F06 estimates, or ledger state.
 */
@Service
public class ProjectBudgetCalculator {
    public static final int MAX_LINES = 100;
    public static final int MAX_CODE_LENGTH = 40;
    public static final int MAX_NAME_LENGTH = 160;
    public static final int MAX_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_LINE_DESCRIPTION_LENGTH = 200;
    public static final int MAX_UNIT_LENGTH = 40;
    public static final int MAX_REASON_LENGTH = 500;
    public static final String BASE_CURRENCY = "PKR";
    public static final String CURRENCY_BASIS = "APPLICATION_FIXED_PKR";
    public static final String CALCULATION_VERSION = "f05-plan-v1";
    public static final String ZERO_PLANNED_REVENUE = "ZERO_PLANNED_REVENUE";

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9._/-]{0,39}");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    public record ValidatedMetadata(
            String code,
            String name,
            ProjectKind kind,
            String description,
            LocalDate startDate,
            LocalDate targetEndDate
    ) { }

    public record ValidatedTransition(
            ProjectStatus targetStatus,
            String reason,
            long expectedVersion
    ) { }

    public ValidatedTransition validateTransition(ProjectTransitionRequest request) {
        if (request == null) {
            throw new ProjectValidationException("request", "required");
        }
        if (request.targetStatus() == null) {
            throw new ProjectValidationException("targetStatus", "required");
        }
        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new ProjectValidationException("expectedVersion", "version_invalid");
        }
        String reason = optionalText(request.reason(), "reason", MAX_REASON_LENGTH);
        return new ValidatedTransition(request.targetStatus(), reason, request.expectedVersion());
    }

    public ValidatedMetadata validateCreateMetadata(ProjectCreateRequest request) {
        if (request == null) {
            throw new ProjectValidationException("request", "required");
        }
        return validateMetadata(
                request.code(),
                request.name(),
                request.kind(),
                request.description(),
                request.startDate(),
                request.targetEndDate()
        );
    }

    public ValidatedMetadata validateUpdateMetadata(ProjectUpdateRequest request) {
        if (request == null) {
            throw new ProjectValidationException("request", "required");
        }
        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new ProjectValidationException("expectedVersion", "version_invalid");
        }
        return validateMetadata(
                request.code(),
                request.name(),
                request.kind(),
                request.description(),
                request.startDate(),
                request.targetEndDate()
        );
    }

    public ValidatedMetadata validateMetadata(
            String rawCode,
            String rawName,
            ProjectKind kind,
            String rawDescription,
            String rawStartDate,
            String rawTargetEndDate
    ) {
        String code = normalizeCode(rawCode);
        String name = requiredText(rawName, "name", MAX_NAME_LENGTH);
        if (kind == null) {
            throw new ProjectValidationException("kind", "required");
        }
        String description = optionalText(rawDescription, "description", MAX_DESCRIPTION_LENGTH);
        LocalDate startDate = parseOptionalDate(rawStartDate, "startDate");
        LocalDate targetEndDate = parseOptionalDate(rawTargetEndDate, "targetEndDate");
        if (startDate != null && targetEndDate != null && targetEndDate.isBefore(startDate)) {
            throw new ProjectValidationException("targetEndDate", "date_order_invalid");
        }
        return new ValidatedMetadata(code, name, kind, description, startDate, targetEndDate);
    }

    public ProjectBudgetCalculationResponse calculate(
            UUID businessId,
            String plannedRevenueInput,
            List<ProjectBudgetLineRequest> linesInput
    ) {
        if (businessId == null) {
            throw new IllegalArgumentException("Server business context is required");
        }
        BigDecimal plannedRevenue = ProjectDecimals.parseRevenue(plannedRevenueInput, "plannedRevenue");
        if (linesInput == null || linesInput.isEmpty() || linesInput.size() > MAX_LINES) {
            throw new ProjectValidationException("lines", "line_count_invalid");
        }

        List<Line> calculatedLines = new ArrayList<>(linesInput.size());
        Map<ProjectCostCategory, BigDecimal> subtotals = new EnumMap<>(ProjectCostCategory.class);
        for (ProjectCostCategory category : ProjectCostCategory.values()) {
            subtotals.put(category, BigDecimal.ZERO);
        }

        BigDecimal plannedCost = BigDecimal.ZERO;
        for (int index = 0; index < linesInput.size(); index++) {
            String path = "lines[" + index + "]";
            ProjectBudgetLineRequest line = linesInput.get(index);
            if (line == null) {
                throw new ProjectValidationException(path, "required");
            }
            if (line.category() == null) {
                throw new ProjectValidationException(path + ".category", "required");
            }
            String description = requiredText(line.description(), path + ".description", MAX_LINE_DESCRIPTION_LENGTH);
            String unit = requiredText(line.unit(), path + ".unit", MAX_UNIT_LENGTH);
            BigDecimal quantity = ProjectDecimals.parseQuantity(line.quantity(), path + ".quantity");
            BigDecimal unitCost = ProjectDecimals.parseUnitCost(line.unitCost(), path + ".unitCost");

            // Exact multiplication: no intermediate MathContext or minor-unit rounding.
            BigDecimal plannedLineCost = quantity.multiply(unitCost);
            plannedCost = plannedCost.add(plannedLineCost);
            subtotals.merge(line.category(), plannedLineCost, BigDecimal::add);

            calculatedLines.add(new Line(
                    index + 1,
                    line.category(),
                    description,
                    ProjectDecimals.decimal(quantity),
                    unit,
                    ProjectDecimals.amount(unitCost),
                    ProjectDecimals.amount(plannedLineCost),
                    quantity,
                    unitCost,
                    plannedLineCost
            ));
        }

        BigDecimal plannedMargin = plannedRevenue.subtract(plannedCost);
        BigDecimal plannedMarginPct = plannedRevenue.signum() > 0
                ? ProjectDecimals.divide(plannedMargin, plannedRevenue)
                : null;
        String marginUnavailableReason = plannedRevenue.signum() == 0 ? ZERO_PLANNED_REVENUE : null;

        Map<ProjectCostCategory, String> serializedSubtotals = new EnumMap<>(ProjectCostCategory.class);
        subtotals.forEach((category, amount) -> serializedSubtotals.put(category, ProjectDecimals.amount(amount)));

        return new ProjectBudgetCalculationResponse(
                businessId,
                BASE_CURRENCY,
                CURRENCY_BASIS,
                true,
                ProjectDecimals.amount(plannedRevenue),
                ProjectDecimals.amount(plannedCost),
                ProjectDecimals.amount(plannedMargin),
                ProjectDecimals.decimal(plannedMarginPct),
                new UnavailableReasons(marginUnavailableReason),
                List.copyOf(calculatedLines),
                Collections.unmodifiableMap(serializedSubtotals),
                new Calculation(
                        CALCULATION_VERSION,
                        ProjectDecimals.DIVISION_SCALE,
                        ProjectDecimals.DIVISION_ROUNDING.name(),
                        null
                ),
                new RawTotals(plannedRevenue, plannedCost, plannedMargin, plannedMarginPct)
        );
        }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new ProjectValidationException("code", "required");
        }
        String stripped = rawCode.strip().toUpperCase(Locale.ROOT);
        if (stripped.length() > MAX_CODE_LENGTH || !CODE_PATTERN.matcher(stripped).matches()) {
            throw new ProjectValidationException("code", "code_invalid");
        }
        return stripped;
    }

    public static String requiredText(String value, String path, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ProjectValidationException(path, "required");
        }
        if (value.length() > maxLength || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new ProjectValidationException(path, "text_invalid");
        }
        return value.strip();
    }

    public static String optionalText(String value, String path, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new ProjectValidationException(path, "text_invalid");
        }
        return value.strip();
    }

    public static LocalDate parseOptionalDate(String value, String path) {
        if (value == null) {
            return null;
        }
        if (!ISO_DATE_PATTERN.matcher(value).matches()) {
            throw new ProjectValidationException(path, "date_invalid");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ProjectValidationException(path, "date_invalid");
        }
    }
}
