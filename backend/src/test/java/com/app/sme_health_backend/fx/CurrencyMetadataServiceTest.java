package com.app.sme_health_backend.fx;

import com.app.sme_health_backend.fx.exception.FxValidationException;
import com.app.sme_health_backend.fx.model.CurrencyMetadata;
import com.app.sme_health_backend.fx.service.CurrencyMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyMetadataServiceTest {

    private CurrencyMetadataService currencyService;

    @BeforeEach
    void setUp() {
        currencyService = new CurrencyMetadataService();
    }

    @Test
    @DisplayName("Registry contains all required 10 currencies")
    void registryContainsRequiredCurrencies() {
        List<CurrencyMetadata> all = currencyService.getAllCurrencies();
        assertThat(all).hasSize(10);

        List<String> codes = all.stream().map(CurrencyMetadata::code).toList();
        assertThat(codes).containsExactlyInAnyOrder(
                "PKR", "USD", "EUR", "GBP", "AED", "SAR", "JPY", "KWD", "BHD", "OMR"
        );
    }

    @ParameterizedTest
    @CsvSource({
            "PKR, 2",
            "USD, 2",
            "EUR, 2",
            "GBP, 2",
            "AED, 2",
            "SAR, 2",
            "JPY, 0",
            "KWD, 3",
            "BHD, 3",
            "OMR, 3"
    })
    @DisplayName("Each currency has authoritative ISO 4217 minor unit precision")
    void minorUnitPrecision(String code, int expectedMinorUnit) {
        CurrencyMetadata meta = currencyService.requireCurrency(code);
        assertThat(meta.minorUnit()).isEqualTo(expectedMinorUnit);
        assertThat(meta.active()).isTrue();
        assertThat(meta.roundingMode()).isEqualTo(RoundingMode.HALF_UP);
    }

    @Test
    @DisplayName("Lookup is case-insensitive")
    void caseInsensitiveLookup() {
        Optional<CurrencyMetadata> usdLower = currencyService.getCurrency("usd");
        assertThat(usdLower).isPresent();
        assertThat(usdLower.get().code()).isEqualTo("USD");

        Optional<CurrencyMetadata> pkrPadded = currencyService.getCurrency("  pkR  ");
        assertThat(pkrPadded).isPresent();
        assertThat(pkrPadded.get().code()).isEqualTo("PKR");
    }

    @Test
    @DisplayName("Unknown currency throws FxValidationException")
    void unknownCurrencyThrowsException() {
        assertThatThrownBy(() -> currencyService.requireCurrency("XYZ"))
                .isInstanceOf(FxValidationException.class)
                .hasMessageContaining("Unsupported currency: 'XYZ'");
    }

    @Test
    @DisplayName("JPY rounds to 0 decimal places")
    void jpyRounding() {
        BigDecimal rounded = currencyService.round("JPY", new BigDecimal("155.678"));
        assertThat(rounded).isEqualByComparingTo(new BigDecimal("156"));
        assertThat(rounded.scale()).isEqualTo(0);
    }

    @Test
    @DisplayName("PKR rounds to 2 decimal places")
    void pkrRounding() {
        BigDecimal rounded = currencyService.round("PKR", new BigDecimal("278.506"));
        assertThat(rounded).isEqualByComparingTo(new BigDecimal("278.51"));
        assertThat(rounded.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("KWD rounds to 3 decimal places")
    void kwdRounding() {
        BigDecimal rounded = currencyService.round("KWD", new BigDecimal("0.307255"));
        assertThat(rounded).isEqualByComparingTo(new BigDecimal("0.307"));
        assertThat(rounded.scale()).isEqualTo(3);
    }
}

