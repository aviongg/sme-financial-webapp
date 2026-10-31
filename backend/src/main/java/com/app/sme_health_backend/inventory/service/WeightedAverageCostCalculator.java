package com.app.sme_health_backend.inventory.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure, deterministic weighted-average-cost calculator for the F08 Phase A valuation PREVIEW.
 * It never reads or writes the database, and its RECEIPT/ISSUE steps are preview operations, not
 * persisted stock movements.
 *
 * <p>State is (Q = quantity, V = total value). The average V / Q is derived for display only and is
 * never used as an authority for the next step.
 * <ul>
 *   <li>RECEIPT q &gt; 0 with extended value c &ge; 0: Q' = Q + q, V' = V + c.</li>
 *   <li>ISSUE 0 &lt; q &lt; Q: issueValue = round(V &times; q / Q, valueScale, valueRounding); Q' = Q - q, V' = V - issueValue.</li>
 *   <li>ISSUE q = Q: issueValue = V and the state becomes exactly (0, 0), so no rounding residual survives.</li>
 *   <li>ISSUE q &gt; Q: rejected as insufficient_stock. Stock never goes negative.</li>
 * </ul>
 * Because V only changes by exact receipt values and by issue values, opening + received - issued = closing
 * holds exactly for every sequence.
 */
@Component
public class WeightedAverageCostCalculator {
    public static final int MAX_STEPS = 100;

    public enum StepType { RECEIPT, ISSUE }

    public record Step(StepType type, BigDecimal quantity, BigDecimal value) { }

    public record State(BigDecimal quantity, BigDecimal value, BigDecimal averageUnitCost) { }

    public record StepResult(
            int index,
            StepType type,
            BigDecimal quantity,
            BigDecimal receiptValue,
            BigDecimal issueValue,
            boolean fullDepletion,
            State after
    ) { }

    public record Result(
            WacPolicy policy,
            State opening,
            List<StepResult> steps,
            State closing,
            BigDecimal totalReceivedQuantity,
            BigDecimal totalReceivedValue,
            BigDecimal totalIssuedQuantity,
            BigDecimal totalIssuedValue
    ) { }

    private final WacPolicy policy;

    public WeightedAverageCostCalculator() {
        this(WacPolicy.DRAFT);
    }

    public WeightedAverageCostCalculator(WacPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public WacPolicy policy() {
        return policy;
    }

    public Result calculate(BigDecimal openingQuantity, BigDecimal openingValue, List<Step> steps) {
        requireQuantity(openingQuantity, "openingQuantity", true);
        requireValue(openingValue, "openingValue");
        if (openingQuantity.signum() == 0 && openingValue.signum() != 0) {
            throw new InventoryValidationException("openingValue", "value_without_quantity");
        }
        if (steps == null || steps.size() > MAX_STEPS) {
            throw new InventoryValidationException("steps", "step_count_invalid");
        }

        BigDecimal quantity = openingQuantity;
        BigDecimal value = openingValue;
        BigDecimal receivedQuantity = BigDecimal.ZERO, receivedValue = BigDecimal.ZERO;
        BigDecimal issuedQuantity = BigDecimal.ZERO, issuedValue = BigDecimal.ZERO;
        List<StepResult> results = new ArrayList<>(steps.size());

        for (int index = 0; index < steps.size(); index++) {
            String path = "steps[" + index + "]";
            Step step = steps.get(index);
            if (step == null || step.type() == null) {
                throw new InventoryValidationException(path + ".type", "required");
            }
            requireQuantity(step.quantity(), path + ".quantity", false);
            BigDecimal q = step.quantity();
            if (step.type() == StepType.RECEIPT) {
                requireValue(step.value(), path + ".value");
                quantity = quantity.add(q);
                value = value.add(step.value());
                receivedQuantity = receivedQuantity.add(q);
                receivedValue = receivedValue.add(step.value());
                results.add(new StepResult(index, StepType.RECEIPT, q, step.value(), null, false, state(quantity, value)));
                continue;
            }
            if (step.value() != null) {
                // An issue is always valued by the calculator; a caller-supplied cost is never accepted.
                throw new InventoryValidationException(path + ".value", "value_not_allowed");
            }
            int comparison = q.compareTo(quantity);
            if (comparison > 0) {
                throw new InventoryValidationException(path + ".quantity", "insufficient_stock");
            }
            boolean full = comparison == 0;
            BigDecimal issueValue = full
                    ? value
                    : value.multiply(q).divide(quantity, policy.valueScale(), policy.valueRounding());
            quantity = full ? BigDecimal.ZERO : quantity.subtract(q);
            value = full ? BigDecimal.ZERO : value.subtract(issueValue);
            issuedQuantity = issuedQuantity.add(q);
            issuedValue = issuedValue.add(issueValue);
            results.add(new StepResult(index, StepType.ISSUE, q, null, issueValue, full, state(quantity, value)));
        }

        // Defensive invariants: these follow from the rules above and must never fail.
        if (value.signum() < 0 || quantity.signum() < 0
                || openingValue.add(receivedValue).subtract(issuedValue).compareTo(value) != 0) {
            throw new IllegalStateException("Weighted-average-cost conservation violated");
        }
        return new Result(policy, state(openingQuantity, openingValue), List.copyOf(results), state(quantity, value),
                receivedQuantity, receivedValue, issuedQuantity, issuedValue);
    }

    /** Display-only average; null (not zero) when no stock remains. */
    public BigDecimal averageUnitCost(BigDecimal quantity, BigDecimal value) {
        if (quantity.signum() == 0) {
            return null;
        }
        return value.divide(quantity, policy.averageDisplayScale(), policy.valueRounding());
    }

    private State state(BigDecimal quantity, BigDecimal value) {
        return new State(quantity, value, averageUnitCost(quantity, value));
    }

    private static void requireQuantity(BigDecimal quantity, String path, boolean zeroAllowed) {
        if (quantity == null) {
            throw new InventoryValidationException(path, "required");
        }
        if (quantity.scale() > InventoryDecimals.QUANTITY_SCALE
                && quantity.stripTrailingZeros().scale() > InventoryDecimals.QUANTITY_SCALE) {
            throw new InventoryValidationException(path, "decimal_invalid");
        }
        if (quantity.signum() < 0 || (!zeroAllowed && quantity.signum() == 0)) {
            throw new InventoryValidationException(path, "quantity_invalid");
        }
    }

    private void requireValue(BigDecimal value, String path) {
        if (value == null) {
            throw new InventoryValidationException(path, "required");
        }
        if (value.scale() > policy.valueScale() && value.stripTrailingZeros().scale() > policy.valueScale()) {
            throw new InventoryValidationException(path, "decimal_invalid");
        }
        if (value.signum() < 0) {
            throw new InventoryValidationException(path, "value_invalid");
        }
    }
}
