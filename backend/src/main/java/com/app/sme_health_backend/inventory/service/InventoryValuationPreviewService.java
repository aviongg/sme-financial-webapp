package com.app.sme_health_backend.inventory.service;

import com.app.sme_health_backend.inventory.dto.ValuationPreviewRequest;
import com.app.sme_health_backend.inventory.dto.ValuationPreviewResponse;
import com.app.sme_health_backend.inventory.service.WeightedAverageCostCalculator.Result;
import com.app.sme_health_backend.inventory.service.WeightedAverageCostCalculator.State;
import com.app.sme_health_backend.inventory.service.WeightedAverageCostCalculator.Step;
import com.app.sme_health_backend.inventory.service.WeightedAverageCostCalculator.StepType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stateless PLANNING / VALUATION PREVIEW. Parses the request exactly, runs the pure calculator and maps the
 * result. It has no repository and no transaction: nothing is read from or written to the database.
 */
@Service
public class InventoryValuationPreviewService {
    public static final String BASE_CURRENCY = "PKR";
    public static final String POLICY_STATUS_DRAFT = "DRAFT_NOT_APPROVED_ACCOUNTING_POLICY";

    private final WeightedAverageCostCalculator calculator;

    public InventoryValuationPreviewService(WeightedAverageCostCalculator calculator) {
        this.calculator = calculator;
    }

    public ValuationPreviewResponse preview(UUID businessId, ValuationPreviewRequest request) {
        if (businessId == null) {
            throw new IllegalArgumentException("Server business context is required");
        }
        if (request == null || request.steps() == null) {
            throw new InventoryValidationException("request", "required");
        }
        WacPolicy policy = calculator.policy();
        BigDecimal openingQuantity = InventoryDecimals.parseQuantity(request.openingQuantity(), "openingQuantity");
        BigDecimal openingValue = InventoryDecimals.parseValue(request.openingValue(), "openingValue", policy.valueScale());
        List<Step> steps = new ArrayList<>(request.steps().size());
        for (int index = 0; index < request.steps().size(); index++) {
            String path = "steps[" + index + "]";
            ValuationPreviewRequest.Step step = request.steps().get(index);
            StepType type = parseType(step.type(), path + ".type");
            BigDecimal quantity = InventoryDecimals.parseQuantity(step.quantity(), path + ".quantity");
            BigDecimal value = null;
            if (type == StepType.RECEIPT) {
                value = InventoryDecimals.parseValue(step.value(), path + ".value", policy.valueScale());
            } else if (step.value() != null) {
                throw new InventoryValidationException(path + ".value", "value_not_allowed");
            }
            steps.add(new Step(type, quantity, value));
        }

        Result result = calculator.calculate(openingQuantity, openingValue, steps);
        int valueScale = policy.valueScale();
        List<ValuationPreviewResponse.Step> mapped = result.steps().stream().map(step -> new ValuationPreviewResponse.Step(
                step.index(),
                step.type().name(),
                InventoryDecimals.decimal(step.quantity()),
                InventoryDecimals.fixed(step.receiptValue(), valueScale),
                InventoryDecimals.fixed(step.issueValue(), valueScale),
                step.fullDepletion(),
                state(step.after(), policy)
        )).toList();
        boolean conserved = result.opening().value().add(result.totalReceivedValue()).subtract(result.totalIssuedValue())
                .compareTo(result.closing().value()) == 0;
        return new ValuationPreviewResponse(
                businessId,
                ValuationPreviewResponse.MODE,
                false,
                false,
                ValuationPreviewResponse.FINANCIAL_EFFECT,
                BASE_CURRENCY,
                new ValuationPreviewResponse.Policy(
                        policy.version(),
                        policy.approved() ? "APPROVED" : POLICY_STATUS_DRAFT,
                        policy.valueScale(),
                        policy.valueRounding().name(),
                        policy.averageDisplayScale(),
                        policy.valueRounding().name(),
                        "ISSUE_OF_ALL_STOCK_TAKES_ALL_VALUE"
                ),
                state(result.opening(), policy),
                mapped,
                state(result.closing(), policy),
                new ValuationPreviewResponse.Totals(
                        InventoryDecimals.decimal(result.totalReceivedQuantity()),
                        InventoryDecimals.fixed(result.totalReceivedValue(), valueScale),
                        InventoryDecimals.decimal(result.totalIssuedQuantity()),
                        InventoryDecimals.fixed(result.totalIssuedValue(), valueScale),
                        conserved
                )
        );
    }

    private static ValuationPreviewResponse.State state(State state, WacPolicy policy) {
        return new ValuationPreviewResponse.State(
                InventoryDecimals.decimal(state.quantity()),
                InventoryDecimals.fixed(state.value(), policy.valueScale()),
                InventoryDecimals.fixed(state.averageUnitCost(), policy.averageDisplayScale())
        );
    }

    private static StepType parseType(String value, String path) {
        if (value == null) {
            throw new InventoryValidationException(path, "required");
        }
        try {
            return StepType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new InventoryValidationException(path, "enum_invalid");
        }
    }
}
