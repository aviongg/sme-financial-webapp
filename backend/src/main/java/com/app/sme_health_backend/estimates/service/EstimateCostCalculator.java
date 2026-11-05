package com.app.sme_health_backend.estimates.service;

import com.app.sme_health_backend.estimates.dto.EstimateLineRequest;
import com.app.sme_health_backend.estimates.dto.PricingPreviewResponse.Line;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;
import static com.app.sme_health_backend.estimates.dto.EstimateLineRequest.CostGroup;

@Service
public class EstimateCostCalculator {
    public static final int MAX_LINES = 100;
    /** Exact unserialized line values for persistence; {@code lines} remains the Phase A presentation. */
    public record RawLine(CostGroup costGroup, String description, BigDecimal quantity, String unit,
                          BigDecimal unitCost, BigDecimal plannedCost) { }
    public record Costs(List<Line> lines, Map<CostGroup, String> groupSubtotals, BigDecimal total, List<RawLine> rawLines) { }

    public Costs calculate(List<EstimateLineRequest> input) {
        if (input == null || input.isEmpty() || input.size() > MAX_LINES)
            throw new EstimateValidationException("lines", "line_count_invalid");
        List<RawLine> raw = new ArrayList<>();
        for (int index = 0; index < input.size(); index++) {
            String path = "lines[" + index + "]";
            EstimateLineRequest line = input.get(index);
            if (line == null) throw new EstimateValidationException(path, "required");
            if (line.costGroup() == null) throw new EstimateValidationException(path + ".costGroup", "required");
            String description = text(line.description(), path + ".description", 200);
            String unit = text(line.unit(), path + ".unit", 40);
            BigDecimal quantity = EstimateDecimals.parse(line.quantity(), path + ".quantity");
            BigDecimal unitCost = EstimateDecimals.parse(line.unitCost(), path + ".unitCost");
            BigDecimal plannedCost = quantity.multiply(unitCost); // Exact: no MathContext or currency rounding.
            raw.add(new RawLine(line.costGroup(), description, quantity, unit, unitCost, plannedCost));
        }
        return assemble(raw);
    }

    /** Presentation and exact totals for already-validated lines, identical for preview and stored revisions. */
    public static Costs assemble(List<RawLine> raw) {
        List<Line> lines = new ArrayList<>();
        Map<CostGroup, BigDecimal> subtotals = new EnumMap<>(CostGroup.class);
        for (CostGroup group : CostGroup.values()) subtotals.put(group, BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < raw.size(); index++) {
            RawLine line = raw.get(index);
            total = total.add(line.plannedCost());
            subtotals.merge(line.costGroup(), line.plannedCost(), BigDecimal::add);
            lines.add(new Line(index + 1, line.costGroup(), line.description(), EstimateDecimals.decimal(line.quantity()), line.unit(),
                    EstimateDecimals.amount(line.unitCost()), EstimateDecimals.amount(line.plannedCost())));
        }
        Map<CostGroup, String> serialized = new EnumMap<>(CostGroup.class);
        subtotals.forEach((group, value) -> serialized.put(group, EstimateDecimals.amount(value)));
        return new Costs(List.copyOf(lines), Collections.unmodifiableMap(serialized), total, List.copyOf(raw));
    }
    private static String text(String value, String path, int maximum) {
        if (value == null || value.isBlank()) throw new EstimateValidationException(path, "required");
        if (value.length() > maximum || value.codePoints().anyMatch(Character::isISOControl))
            throw new EstimateValidationException(path, "text_invalid");
        return value.strip();
    }
}
