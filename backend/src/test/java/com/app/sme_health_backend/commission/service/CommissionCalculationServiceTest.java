package com.app.sme_health_backend.commission.service;

import com.app.sme_health_backend.commission.entity.CommissionRule;
import com.app.sme_health_backend.commission.exception.CommissionEarningValidationException;
import com.app.sme_health_backend.commission.model.CalculationType;
import com.app.sme_health_backend.commission.model.EarningBasis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommissionCalculationServiceTest {

    private CommissionCalculationService service;
    private UUID businessId;

    @BeforeEach
    void setUp() {
        service = new CommissionCalculationService();
        businessId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Calculates exact percentage commission on round collection amount")
    void calculatePercentage_Exact() {
        CommissionRule rule = new CommissionRule(
                businessId, null, "Standard 5%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.0000"), null, "PKR", LocalDate.of(2026, 1, 1), null
        );

        BigDecimal commission = service.calculateCommission(rule, new BigDecimal("100000.00"));
        assertThat(commission).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("Calculates fractional percentage commission with HALF_UP rounding")
    void calculatePercentage_FractionalRounding() {
        // 1,234.56 * 3.75% = 46.296 -> 46.30
        CommissionRule rule = new CommissionRule(
                businessId, null, "3.75% Rule", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("3.7500"), null, "PKR", LocalDate.of(2026, 1, 1), null
        );

        BigDecimal commission = service.calculateCommission(rule, new BigDecimal("1234.56"));
        assertThat(commission).isEqualByComparingTo("46.30");
    }

    @Test
    @DisplayName("Calculates fixed amount commission")
    void calculateFixed_Normal() {
        CommissionRule rule = new CommissionRule(
                businessId, null, "Fixed 2000", CalculationType.FIXED, EarningBasis.COLLECTION_BASED,
                null, new BigDecimal("2000.00"), "PKR", LocalDate.of(2026, 1, 1), null
        );

        BigDecimal commission = service.calculateCommission(rule, new BigDecimal("50000.00"));
        assertThat(commission).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("Caps fixed commission at collection amount so commission does not exceed 100%")
    void calculateFixed_CappedAtSaleAmount() {
        CommissionRule rule = new CommissionRule(
                businessId, null, "Fixed 5000", CalculationType.FIXED, EarningBasis.COLLECTION_BASED,
                null, new BigDecimal("5000.00"), "PKR", LocalDate.of(2026, 1, 1), null
        );

        BigDecimal commission = service.calculateCommission(rule, new BigDecimal("3000.00"));
        assertThat(commission).isEqualByComparingTo("3000.00");
    }

    @Test
    @DisplayName("Throws validation exception if collection amount is zero or negative")
    void calculate_ZeroOrNegativeAmount() {
        CommissionRule rule = new CommissionRule(
                businessId, null, "Standard 5%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.0000"), null, "PKR", LocalDate.of(2026, 1, 1), null
        );

        assertThatThrownBy(() -> service.calculateCommission(rule, BigDecimal.ZERO))
                .isInstanceOf(CommissionEarningValidationException.class)
                .hasMessageContaining("positive");

        assertThatThrownBy(() -> service.calculateCommission(rule, new BigDecimal("-100.00")))
                .isInstanceOf(CommissionEarningValidationException.class)
                .hasMessageContaining("positive");
    }
}
