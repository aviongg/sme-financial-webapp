package com.app.sme_health_backend.scoring;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.scoring.calculator.ComplianceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ComplianceCalculatorTests {

    private ComplianceCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ComplianceCalculator();
    }

    @Test
    void shouldReturnNullWhenProfileIsNull() {
        assertNull(calculator.calculate(null));
    }

    @Test
    void shouldReturnNullWhenBothFieldsAreNull() {
        BusinessProfile profile = new BusinessProfile();
        assertNull(calculator.calculate(profile));
    }

    @Test
    void shouldScoreHundredWhenBothAreTrue() {
        BusinessProfile profile = new BusinessProfile();
        profile.setNtnRegistered(true);
        profile.setBusinessRegistered(true);

        assertEquals(new BigDecimal("100.00"), calculator.calculate(profile));
    }

    @Test
    void shouldScoreFiftyWhenOneIsTrueAndOneIsFalse() {
        BusinessProfile p1 = new BusinessProfile();
        p1.setNtnRegistered(true);
        p1.setBusinessRegistered(false);
        assertEquals(new BigDecimal("50.00"), calculator.calculate(p1));

        BusinessProfile p2 = new BusinessProfile();
        p2.setNtnRegistered(false);
        p2.setBusinessRegistered(true);
        assertEquals(new BigDecimal("50.00"), calculator.calculate(p2));
    }

    @Test
    void shouldScoreZeroWhenBothAreFalse() {
        BusinessProfile profile = new BusinessProfile();
        profile.setNtnRegistered(false);
        profile.setBusinessRegistered(false);

        assertEquals(new BigDecimal("0.00"), calculator.calculate(profile));
    }

    @Test
    void shouldReNormalizeWhenOnlyOneFieldIsKnown() {
        // Only NTN known as true -> 100.00
        BusinessProfile p1 = new BusinessProfile();
        p1.setNtnRegistered(true);
        p1.setBusinessRegistered(null);
        assertEquals(new BigDecimal("100.00"), calculator.calculate(p1));

        // Only NTN known as false -> 0.00
        BusinessProfile p2 = new BusinessProfile();
        p2.setNtnRegistered(false);
        p2.setBusinessRegistered(null);
        assertEquals(new BigDecimal("0.00"), calculator.calculate(p2));

        // Only Business registered known as true -> 100.00
        BusinessProfile p3 = new BusinessProfile();
        p3.setNtnRegistered(null);
        p3.setBusinessRegistered(true);
        assertEquals(new BigDecimal("100.00"), calculator.calculate(p3));

        // Only Business registered known as false -> 0.00
        BusinessProfile p4 = new BusinessProfile();
        p4.setNtnRegistered(null);
        p4.setBusinessRegistered(false);
        assertEquals(new BigDecimal("0.00"), calculator.calculate(p4));
    }
}
