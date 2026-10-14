package com.app.sme_health_backend.accounting;

import com.app.sme_health_backend.accounting.model.BalanceStatus;
import com.app.sme_health_backend.accounting.model.CurrencyRegistry;
import com.app.sme_health_backend.accounting.model.CurrencyRegistry.BalanceEvaluation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyRegistryTest {

    @ParameterizedTest
    @CsvSource({
            "PKR, 2",
            "USD, 2",
            "EUR, 2",
            "GBP, 2",
            "AED, 2",
            "SAR, 2",
            "JPY, 0",
            "BHD, 3",
            "KWD, 3",
            "OMR, 3"
    })
    @DisplayName("Supported currencies have expected scale mappings")
    void supportedCurrenciesScaleMapping(String currency, int expectedScale) {
        assertTrue(CurrencyRegistry.isSupported(currency));
        assertEquals(expectedScale, CurrencyRegistry.getScale(currency));
    }

    @Test
    @DisplayName("Unsupported currency returns false and throws on getScale")
    void unsupportedCurrency() {
        assertFalse(CurrencyRegistry.isSupported("XYZ"));
        assertFalse(CurrencyRegistry.isSupported(null));
        assertThrows(IllegalArgumentException.class, () -> CurrencyRegistry.getScale("XYZ"));
    }

    @ParameterizedTest
    @CsvSource({
            "PKR, 2, true",
            "USD, 2, true",
            "EUR, 2, true",
            "GBP, 2, true",
            "AED, 2, true",
            "SAR, 2, true",
            "JPY, 0, true",
            "BHD, 3, true",
            "KWD, 3, true",
            "OMR, 3, true",
            "PKR, 3, false",
            "PKR, 0, false",
            "JPY, 2, false",
            "BHD, 2, false",
            "XYZ, 2, false"
    })
    @DisplayName("isSupported validates currency code and currencyDecimals together")
    void isSupportedWithDecimals(String currency, Integer decimals, boolean expected) {
        assertEquals(expected, CurrencyRegistry.isSupported(currency, decimals));
    }

    @Test
    @DisplayName("isSupported handles null currency or null decimals safely")
    void isSupportedNullSafety() {
        assertFalse(CurrencyRegistry.isSupported(null, 2));
        assertFalse(CurrencyRegistry.isSupported("PKR", null));
        assertFalse(CurrencyRegistry.isSupported(null, null));
    }

    @Test
    @DisplayName("formatCanonicalDecimal formats amounts without exponents or negative zero")
    void formatCanonicalDecimal() {
        assertNull(CurrencyRegistry.formatCanonicalDecimal(null));
        assertEquals("100.00", CurrencyRegistry.formatCanonicalDecimal(new BigDecimal("100.00")));
        assertEquals("1.0001", CurrencyRegistry.formatCanonicalDecimal(new BigDecimal("1.0001")));
        assertEquals("-2500.50", CurrencyRegistry.formatCanonicalDecimal(new BigDecimal("-2500.50")));
        assertEquals("0.00", CurrencyRegistry.formatCanonicalDecimal(new BigDecimal("-0.00")));
        assertEquals("0", CurrencyRegistry.formatCanonicalDecimal(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Canonical zero formatting per scale")
    void canonicalZeros() {
        assertEquals("0", CurrencyRegistry.formatCanonicalZero("JPY"));
        assertEquals("0.00", CurrencyRegistry.formatCanonicalZero("PKR"));
        assertEquals("0.00", CurrencyRegistry.formatCanonicalZero("USD"));
        assertEquals("0.000", CurrencyRegistry.formatCanonicalZero("BHD"));
        assertEquals("0.000", CurrencyRegistry.formatCanonicalZero("KWD"));
        assertEquals("0.000", CurrencyRegistry.formatCanonicalZero("OMR"));
    }

    @Test
    @DisplayName("Scale 0 (JPY) formatted without decimal point")
    void jpyFormattingNoDecimalPoint() {
        BalanceEvaluation eval = CurrencyRegistry.evaluateBalance(new BigDecimal("500000"), "JPY");
        assertEquals(BalanceStatus.AVAILABLE, eval.status());
        assertEquals("500000", eval.formattedBalance());
        assertNull(eval.unavailableReason());
    }

    @Test
    @DisplayName("Maximum 12-integer-digit values formatted correctly")
    void maxIntegerDigits() {
        // JPY max 12 digits: 999999999999
        BalanceEvaluation jpyMax = CurrencyRegistry.evaluateBalance(new BigDecimal("999999999999"), "JPY");
        assertEquals(BalanceStatus.AVAILABLE, jpyMax.status());
        assertEquals("999999999999", jpyMax.formattedBalance());

        // PKR max 12 digits: 999999999999.99
        BalanceEvaluation pkrMax = CurrencyRegistry.evaluateBalance(new BigDecimal("999999999999.99"), "PKR");
        assertEquals(BalanceStatus.AVAILABLE, pkrMax.status());
        assertEquals("999999999999.99", pkrMax.formattedBalance());

        // Negative 12 digits
        BalanceEvaluation pkrMin = CurrencyRegistry.evaluateBalance(new BigDecimal("-999999999999.99"), "PKR");
        assertEquals(BalanceStatus.AVAILABLE, pkrMin.status());
        assertEquals("-999999999999.99", pkrMin.formattedBalance());
    }

    @Test
    @DisplayName("Capacity overflow triggers CAPACITY_OVERFLOW")
    void capacityOverflow() {
        // 13 integer digits: 1000000000000
        BalanceEvaluation eval = CurrencyRegistry.evaluateBalance(new BigDecimal("1000000000000.00"), "PKR");
        assertEquals(BalanceStatus.UNAVAILABLE, eval.status());
        assertNull(eval.formattedBalance());
        assertEquals("CAPACITY_OVERFLOW", eval.unavailableReason());

        // Negative 13 integer digits
        BalanceEvaluation negEval = CurrencyRegistry.evaluateBalance(new BigDecimal("-1000000000000"), "JPY");
        assertEquals(BalanceStatus.UNAVAILABLE, negEval.status());
        assertNull(negEval.formattedBalance());
        assertEquals("CAPACITY_OVERFLOW", negEval.unavailableReason());
    }

    @Test
    @DisplayName("Precision overflow triggers PRECISION_OVERFLOW")
    void precisionOverflow() {
        // PKR declared scale 2; 3 decimals provided
        BalanceEvaluation eval = CurrencyRegistry.evaluateBalance(new BigDecimal("100.005"), "PKR");
        assertEquals(BalanceStatus.UNAVAILABLE, eval.status());
        assertNull(eval.formattedBalance());
        assertEquals("PRECISION_OVERFLOW", eval.unavailableReason());

        // JPY declared scale 0; 1 decimal provided
        BalanceEvaluation jpyEval = CurrencyRegistry.evaluateBalance(new BigDecimal("500.5"), "JPY");
        assertEquals(BalanceStatus.UNAVAILABLE, jpyEval.status());
        assertNull(jpyEval.formattedBalance());
        assertEquals("PRECISION_OVERFLOW", jpyEval.unavailableReason());
    }

    @Test
    @DisplayName("Trailing zeros matching declared scale succeed")
    void trailingZerosWithinScaleSucceed() {
        // Database NUMERIC(19,4) might return 100.0000 for PKR
        BalanceEvaluation eval = CurrencyRegistry.evaluateBalance(new BigDecimal("100.0000"), "PKR");
        assertEquals(BalanceStatus.AVAILABLE, eval.status());
        assertEquals("100.00", eval.formattedBalance());
    }

    @Test
    @DisplayName("Zero values are never formatted as negative zero")
    void noNegativeZero() {
        BalanceEvaluation eval = CurrencyRegistry.evaluateBalance(new BigDecimal("-0.00"), "PKR");
        assertEquals(BalanceStatus.AVAILABLE, eval.status());
        assertEquals("0.00", eval.formattedBalance());
        assertFalse(eval.formattedBalance().startsWith("-"));
    }

    @Test
    @DisplayName("Unsupported currency returns UNSUPPORTED_CURRENCY")
    void unsupportedCurrencyEvaluation() {
        BalanceEvaluation eval = CurrencyRegistry.evaluateBalance(new BigDecimal("100.00"), "FOO");
        assertEquals(BalanceStatus.UNAVAILABLE, eval.status());
        assertNull(eval.formattedBalance());
        assertEquals("UNSUPPORTED_CURRENCY", eval.unavailableReason());
    }

    @Test
    @DisplayName("Stored currency scale mismatch returns UNSUPPORTED_CURRENCY")
    void storedCurrencyScaleMismatchEvaluation() {
        // Persisted PKR account declaring scale 3
        BalanceEvaluation eval = CurrencyRegistry.evaluateBalance(new BigDecimal("100.00"), "PKR", 3);
        assertEquals(BalanceStatus.UNAVAILABLE, eval.status());
        assertNull(eval.formattedBalance());
        assertEquals("UNSUPPORTED_CURRENCY", eval.unavailableReason());

        // Persisted JPY account declaring scale 2
        BalanceEvaluation jpyMismatch = CurrencyRegistry.evaluateBalance(new BigDecimal("500000"), "JPY", 2);
        assertEquals(BalanceStatus.UNAVAILABLE, jpyMismatch.status());
        assertEquals("UNSUPPORTED_CURRENCY", jpyMismatch.unavailableReason());
    }

    @Test
    @DisplayName("evaluateBalance with valid currency and scale succeeds for exact amounts")
    void evaluateBalanceWithValidDecimals() {
        // PKR scale 2
        BalanceEvaluation pkr = CurrencyRegistry.evaluateBalance(new BigDecimal("100.00"), "PKR", 2);
        assertEquals(BalanceStatus.AVAILABLE, pkr.status());
        assertEquals("100.00", pkr.formattedBalance());
        assertNull(pkr.unavailableReason());

        // BHD scale 3
        BalanceEvaluation bhd = CurrencyRegistry.evaluateBalance(new BigDecimal("1250.375"), "BHD", 3);
        assertEquals(BalanceStatus.AVAILABLE, bhd.status());
        assertEquals("1250.375", bhd.formattedBalance());

        // JPY scale 0
        BalanceEvaluation jpy = CurrencyRegistry.evaluateBalance(new BigDecimal("500000"), "JPY", 0);
        assertEquals(BalanceStatus.AVAILABLE, jpy.status());
        assertEquals("500000", jpy.formattedBalance());

        // Negative PKR
        BalanceEvaluation neg = CurrencyRegistry.evaluateBalance(new BigDecimal("-2500.50"), "PKR", 2);
        assertEquals(BalanceStatus.AVAILABLE, neg.status());
        assertEquals("-2500.50", neg.formattedBalance());
    }

    @Test
    @DisplayName("Masking logic never exposes raw identifier")
    void maskingLogic() {
        assertNull(CurrencyRegistry.maskIdentifier(null));
        assertNull(CurrencyRegistry.maskIdentifier("   "));
        assertEquals("****4821", CurrencyRegistry.maskIdentifier("4821"));
        assertEquals("****4821", CurrencyRegistry.maskIdentifier("****4821"));
        assertEquals("****4821", CurrencyRegistry.maskIdentifier("PK12MEZN0000123456784821"));
        assertEquals("****12", CurrencyRegistry.maskIdentifier("12"));
    }
}
