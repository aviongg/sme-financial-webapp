package com.app.sme_health_backend.commission.service;

import com.app.sme_health_backend.commission.entity.CommissionRule;
import com.app.sme_health_backend.commission.exception.CommissionEarningValidationException;
import com.app.sme_health_backend.commission.model.CalculationType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Service
public class CommissionCalculationService {

    private static final int DEFAULT_CURRENCY_SCALE = 2;
    private static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;

    public BigDecimal calculateCommission(CommissionRule rule, BigDecimal saleAmount) {
        Objects.requireNonNull(rule, "rule must not be null");
        Objects.requireNonNull(saleAmount, "saleAmount must not be null");

        if (saleAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CommissionEarningValidationException("saleAmount", "Sale/collection amount must be positive");
        }

        if (rule.getCalculationType() == CalculationType.PERCENTAGE) {
            BigDecimal rate = rule.getRatePercentage();
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CommissionEarningValidationException("ratePercentage", "Rule percentage rate is invalid");
            }
            // calculation: saleAmount * (rate / 100)
            return saleAmount
                    .multiply(rate)
                    .divide(BigDecimal.valueOf(100), DEFAULT_CURRENCY_SCALE, DEFAULT_ROUNDING_MODE);
        } else if (rule.getCalculationType() == CalculationType.FIXED) {
            BigDecimal fixed = rule.getFixedAmount();
            if (fixed == null || fixed.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CommissionEarningValidationException("fixedAmount", "Rule fixed amount is invalid");
            }
            BigDecimal normalizedFixed = fixed.setScale(DEFAULT_CURRENCY_SCALE, DEFAULT_ROUNDING_MODE);
            // Capped at saleAmount: a fixed commission cannot exceed 100% of the collection value
            if (normalizedFixed.compareTo(saleAmount) > 0) {
                return saleAmount.setScale(DEFAULT_CURRENCY_SCALE, DEFAULT_ROUNDING_MODE);
            }
            return normalizedFixed;
        } else {
            throw new CommissionEarningValidationException("calculationType", "Unsupported calculation type: " + rule.getCalculationType());
        }
    }
}
