package com.app.sme_health_backend.scoring.calculator;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class RepaymentCalculator {

    public BigDecimal calculate(BusinessProfile profile) {
        if (profile == null || profile.getPaymentBehavior() == null) {
            return null;
        }

        String behavior = profile.getPaymentBehavior().trim().toLowerCase();

        return switch (behavior) {
            case "immediate" -> new BigDecimal("100.00");
            case "2weeks" -> new BigDecimal("80.00");
            case "1month_plus" -> new BigDecimal("50.00");
            case "irregular" -> new BigDecimal("20.00");
            default -> null;
        };
    }
}
