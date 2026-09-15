package com.app.sme_health_backend.scoring.calculator;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ComplianceCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal FIFTY = new BigDecimal("50.00");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public BigDecimal calculate(BusinessProfile profile) {
        if (profile == null) {
            return null;
        }

        Boolean ntn = profile.getNtnRegistered();
        Boolean biz = profile.getBusinessRegistered();

        if (ntn == null && biz == null) {
            return null;
        }

        if (ntn != null && biz != null) {
            if (ntn && biz) {
                return HUNDRED;
            } else if (ntn || biz) {
                return FIFTY;
            } else {
                return ZERO;
            }
        }

        // Only one is known -> re-normalize using available evidence
        boolean knownVal = (ntn != null) ? ntn : biz;
        return knownVal ? HUNDRED : ZERO;
    }
}
