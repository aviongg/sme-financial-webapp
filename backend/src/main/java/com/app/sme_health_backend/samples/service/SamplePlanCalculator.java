package com.app.sme_health_backend.samples.service;

import com.app.sme_health_backend.samples.dto.SamplePlanCalculationResponse;
import com.app.sme_health_backend.samples.dto.SamplePlanCalculationResponse.Calculation;
import com.app.sme_health_backend.samples.dto.SamplePlanCalculationResponse.Line;
import com.app.sme_health_backend.samples.dto.SamplePlanCalculationResponse.RawTotals;
import com.app.sme_health_backend.samples.dto.SamplePlanCalculationResponse.UnavailableReasons;
import com.app.sme_health_backend.samples.dto.SamplePlanLineRequest;
import com.app.sme_health_backend.samples.dto.SampleCreateRequest;
import com.app.sme_health_backend.samples.dto.SampleTransitionRequest;
import com.app.sme_health_backend.samples.dto.SampleUpdateRequest;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
import com.app.sme_health_backend.samples.model.SampleKind;
import com.app.sme_health_backend.samples.model.RecoveryIntent;
import com.app.sme_health_backend.samples.model.SampleStatus;
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
 * Authoritative stateless planning calculator and validator for F09 Phase A.
 * Computes plannedLineCost, plannedCost, and plannedContribution using exact BigDecimal arithmetic.
 * Never reads or mutates MonthlyRecord, ScoringService, F06 estimates, or ledger state.
 */
@Service
public class SamplePlanCalculator {
    public static final int MAX_LINES = 100;
    public static final int MAX_CODE_LENGTH = 40;
    public static final int MAX_NAME_LENGTH = 160;
    public static final int MAX_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_LINE_DESCRIPTION_LENGTH = 200;
    public static final int MAX_UNIT_LENGTH = 40;
    public static final int MAX_REASON_LENGTH = 500;
    public static final String BASE_CURRENCY = "PKR";
    public static final String CURRENCY_BASIS = "APPLICATION_FIXED_PKR";
    public static final String CALCULATION_VERSION = "f09-plan-v1";
    public static final String PLANNED_RECOVERY_UNKNOWN = "PLANNED_RECOVERY_UNKNOWN";

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9._/-]{0,39}");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    public record ValidatedMetadata(
            String code,
            String name,
            SampleKind kind,
            String description,
            RecoveryIntent recoveryIntent,
            UUID projectId,
            LocalDate startDate,
            LocalDate targetEndDate
    ) { }

    public record ValidatedTransition(
            SampleStatus targetStatus,
            String reason,
            long expectedVersion
    ) { }

    public ValidatedTransition validateTransition(SampleTransitionRequest request) {
        if (request == null) {
            throw new SampleValidationException("request", "required");
        }
        if (request.targetStatus() == null) {
            throw new SampleValidationException("targetStatus", "required");
        }
        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new SampleValidationException("expectedVersion", "version_invalid");
        }
        String reason = optionalText(request.reason(), "reason", MAX_REASON_LENGTH);
        return new ValidatedTransition(request.targetStatus(), reason, request.expectedVersion());
    }

    public ValidatedMetadata validateCreateMetadata(SampleCreateRequest request) {
        if (request == null) {
            throw new SampleValidationException("request", "required");
        }
        return validateMetadata(
                request.code(),
                request.name(),
                request.kind(),
                request.description(),
                request.recoveryIntent(),
                request.projectId(),
                request.startDate(),
                request.targetEndDate()
        );
    }

    public ValidatedMetadata validateUpdateMetadata(SampleUpdateRequest request) {
        if (request == null) {
            throw new SampleValidationException("request", "required");
        }
        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new SampleValidationException("expectedVersion", "version_invalid");
        }
        return validateMetadata(
                request.code(),
                request.name(),
                request.kind(),
                request.description(),
                request.recoveryIntent(),
                request.projectId(),
                request.startDate(),
                request.targetEndDate()
        );
    }

    public ValidatedMetadata validateMetadata(
            String rawCode,
            String rawName,
            SampleKind kind,
            String rawDescription,
            RecoveryIntent recoveryIntent,
            UUID projectId,
            String rawStartDate,
            String rawTargetEndDate
    ) {
        String code = normalizeCode(rawCode);
        String name = requiredText(rawName, "name", MAX_NAME_LENGTH);
        if (kind == null) {
            throw new SampleValidationException("kind", "required");
        }
        if (recoveryIntent == null) throw new SampleValidationException("recoveryIntent", "required");
        String description = optionalText(rawDescription, "description", MAX_DESCRIPTION_LENGTH);
        LocalDate startDate = parseOptionalDate(rawStartDate, "startDate");
        LocalDate targetEndDate = parseOptionalDate(rawTargetEndDate, "targetEndDate");
        if (startDate != null && targetEndDate != null && targetEndDate.isBefore(startDate)) {
            throw new SampleValidationException("targetEndDate", "date_order_invalid");
        }
        return new ValidatedMetadata(code, name, kind, description, recoveryIntent, projectId, startDate, targetEndDate);
    }

    public SamplePlanCalculationResponse calculate(
            UUID businessId,
            RecoveryIntent recoveryIntent,
            String plannedRecoveryInput,
            List<SamplePlanLineRequest> linesInput
    ) {
        if (businessId == null) {
            throw new IllegalArgumentException("Server business context is required");
        }
        if (recoveryIntent == null) throw new SampleValidationException("recoveryIntent", "required");
        BigDecimal plannedRecovery = plannedRecoveryInput == null ? null : SampleDecimals.parseRecovery(plannedRecoveryInput, "plannedRecovery");
        if (recoveryIntent == RecoveryIntent.FREE) {
            if (plannedRecovery != null && plannedRecovery.signum() != 0) {
                throw new SampleValidationException("plannedRecovery", "free_recovery_must_be_zero");
            }
            plannedRecovery = BigDecimal.ZERO;
        }
        if (linesInput == null || linesInput.isEmpty() || linesInput.size() > MAX_LINES) {
            throw new SampleValidationException("lines", "line_count_invalid");
        }

        List<Line> calculatedLines = new ArrayList<>(linesInput.size());
        Map<ProjectCostCategory, BigDecimal> subtotals = new EnumMap<>(ProjectCostCategory.class);
        for (ProjectCostCategory category : ProjectCostCategory.values()) {
            subtotals.put(category, BigDecimal.ZERO);
        }

        BigDecimal plannedCost = BigDecimal.ZERO;
        for (int index = 0; index < linesInput.size(); index++) {
            String path = "lines[" + index + "]";
            SamplePlanLineRequest line = linesInput.get(index);
            if (line == null) {
                throw new SampleValidationException(path, "required");
            }
            if (line.category() == null) {
                throw new SampleValidationException(path + ".category", "required");
            }
            String description = requiredText(line.description(), path + ".description", MAX_LINE_DESCRIPTION_LENGTH);
            String unit = requiredText(line.unit(), path + ".unit", MAX_UNIT_LENGTH);
            BigDecimal quantity = SampleDecimals.parseQuantity(line.quantity(), path + ".quantity");
            BigDecimal unitCost = SampleDecimals.parseUnitCost(line.unitCost(), path + ".unitCost");

            // Exact multiplication: no intermediate MathContext or minor-unit rounding.
            BigDecimal plannedLineCost = quantity.multiply(unitCost);
            plannedCost = plannedCost.add(plannedLineCost);
            subtotals.merge(line.category(), plannedLineCost, BigDecimal::add);

            calculatedLines.add(new Line(
                    index + 1,
                    line.category(),
                    description,
                    SampleDecimals.decimal(quantity),
                    unit,
                    SampleDecimals.amount(unitCost),
                    SampleDecimals.amount(plannedLineCost),
                    quantity,
                    unitCost,
                    plannedLineCost
            ));
        }

        BigDecimal plannedContribution = plannedRecovery == null ? null : plannedRecovery.subtract(plannedCost);
        String contributionUnavailableReason = plannedRecovery == null ? PLANNED_RECOVERY_UNKNOWN : null;

        Map<ProjectCostCategory, String> serializedSubtotals = new EnumMap<>(ProjectCostCategory.class);
        subtotals.forEach((category, amount) -> serializedSubtotals.put(category, SampleDecimals.amount(amount)));

        return new SamplePlanCalculationResponse(
                businessId,
                BASE_CURRENCY,
                CURRENCY_BASIS,
                true,
                SampleDecimals.amount(plannedRecovery),
                SampleDecimals.amount(plannedCost),
                SampleDecimals.amount(plannedContribution),
                new UnavailableReasons(contributionUnavailableReason),
                List.copyOf(calculatedLines),
                Collections.unmodifiableMap(serializedSubtotals),
                new Calculation(
                        CALCULATION_VERSION,
                        null
                ),
                new RawTotals(plannedRecovery, plannedCost, plannedContribution)
        );
        }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new SampleValidationException("code", "required");
        }
        String stripped = rawCode.strip().toUpperCase(Locale.ROOT);
        if (stripped.length() > MAX_CODE_LENGTH || !CODE_PATTERN.matcher(stripped).matches()) {
            throw new SampleValidationException("code", "code_invalid");
        }
        return stripped;
    }

    public static String requiredText(String value, String path, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new SampleValidationException(path, "required");
        }
        if (value.length() > maxLength || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new SampleValidationException(path, "text_invalid");
        }
        return value.strip();
    }

    public static String optionalText(String value, String path, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new SampleValidationException(path, "text_invalid");
        }
        return value.strip();
    }

    public static LocalDate parseOptionalDate(String value, String path) {
        if (value == null) {
            return null;
        }
        if (!ISO_DATE_PATTERN.matcher(value).matches()) {
            throw new SampleValidationException(path, "date_invalid");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new SampleValidationException(path, "date_invalid");
        }
    }
}
