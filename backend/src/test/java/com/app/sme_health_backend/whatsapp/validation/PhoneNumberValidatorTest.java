package com.app.sme_health_backend.whatsapp.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberValidatorTest {

    @Test
    void shouldNormalizeStandardE164() {
        assertEquals("+923001234567", PhoneNumberValidator.normalizeAndValidate("+923001234567"));
        assertEquals("+14155552671", PhoneNumberValidator.normalizeAndValidate("+14155552671"));
    }

    @Test
    void shouldNormalizePakistaniLocalFormat() {
        assertEquals("+923001234567", PhoneNumberValidator.normalizeAndValidate("03001234567"));
        assertEquals("+923219876543", PhoneNumberValidator.normalizeAndValidate("03219876543"));
    }

    @Test
    void shouldNormalizeWithSpacesDashesParentheses() {
        assertEquals("+923001234567", PhoneNumberValidator.normalizeAndValidate("+92 300 123-4567"));
        assertEquals("+923001234567", PhoneNumberValidator.normalizeAndValidate("0300-1234567"));
        assertEquals("+923001234567", PhoneNumberValidator.normalizeAndValidate("(0300) 1234567"));
    }

    @Test
    void shouldNormalizeLeading00() {
        assertEquals("+923001234567", PhoneNumberValidator.normalizeAndValidate("00923001234567"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "abc",
            "+92300abc4567",
            "12345",
            "+1234",
            "+123456789012345678",
            "923001234567" // Missing + or 00 or 03
    })
    void shouldRejectInvalidPhoneNumbers(String invalid) {
        assertThrows(IllegalArgumentException.class, () -> PhoneNumberValidator.normalizeAndValidate(invalid));
    }

    @Test
    void shouldRejectNull() {
        assertThrows(IllegalArgumentException.class, () -> PhoneNumberValidator.normalizeAndValidate(null));
    }

    @Test
    void shouldMaskPhoneNumberForLogs() {
        assertEquals("+92300***4567", PhoneNumberValidator.mask("+923001234567"));
        assertEquals("***", PhoneNumberValidator.mask("+1234"));
        assertEquals("[empty]", PhoneNumberValidator.mask(null));
        assertEquals("[empty]", PhoneNumberValidator.mask("  "));
    }
}
