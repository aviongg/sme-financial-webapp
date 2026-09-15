package com.app.sme_health_backend.scoring;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.scoring.calculator.RepaymentCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class RepaymentCalculatorTests {

    private RepaymentCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RepaymentCalculator();
    }

    @Test
    void shouldReturnNullWhenProfileIsNull() {
        assertNull(calculator.calculate(null));
    }

    @Test
    void shouldReturnNullWhenPaymentBehaviorIsNull() {
        BusinessProfile profile = new BusinessProfile();
        assertNull(calculator.calculate(profile));
    }

    @Test
    void shouldScoreImmediateAsHundred() {
        BusinessProfile profile = new BusinessProfile();
        profile.setPaymentBehavior("immediate");
        assertEquals(new BigDecimal("100.00"), calculator.calculate(profile));
    }

    @Test
    void shouldScoreTwoWeeksAsEighty() {
        BusinessProfile profile = new BusinessProfile();
        profile.setPaymentBehavior("2weeks");
        assertEquals(new BigDecimal("80.00"), calculator.calculate(profile));
    }

    @Test
    void shouldScoreOneMonthPlusAsFifty() {
        BusinessProfile profile = new BusinessProfile();
        profile.setPaymentBehavior("1month_plus");
        assertEquals(new BigDecimal("50.00"), calculator.calculate(profile));
    }

    @Test
    void shouldScoreIrregularAsTwenty() {
        BusinessProfile profile = new BusinessProfile();
        profile.setPaymentBehavior("irregular");
        assertEquals(new BigDecimal("20.00"), calculator.calculate(profile));
    }

    @Test
    void shouldReturnNullForUnrecognizedBehavior() {
        BusinessProfile profile = new BusinessProfile();
        profile.setPaymentBehavior("other");
        assertNull(calculator.calculate(profile));
    }
}
