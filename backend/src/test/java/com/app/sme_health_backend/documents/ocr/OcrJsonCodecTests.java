package com.app.sme_health_backend.documents.ocr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class OcrJsonCodecTests {
    static final String PARTIAL = """
            {"date":"2026-09-01","amount":1234.56,"vendor_or_party":null,
             "category":"unknown","confidence":"low","document_type_detected":"invoice"}
            """;
    private final OcrJsonCodec codec = new OcrJsonCodec();

    @Test
    void requestUsesOnlyTheTwoSharedFieldNames() {
        byte[] body = codec.encode(new OcrRequest("https://example.com/invoice.pdf", OcrExtraction.DocumentType.invoice));
        JsonNode json = new JsonMapper().readTree(body);
        assertEquals(2, json.size());
        assertEquals("https://example.com/invoice.pdf", json.get("image_url").stringValue());
        assertEquals("invoice", json.get("document_type_hint").stringValue());
    }

    @Test
    void preservesRecognizedValuesAndExplicitBlankFields() {
        OcrExtraction extraction = decode(PARTIAL);
        assertEquals(LocalDate.of(2026, 9, 1), extraction.date());
        assertEquals(new BigDecimal("1234.56"), extraction.amount());
        assertNull(extraction.vendorOrParty());
        assertEquals(OcrExtraction.Category.unknown, extraction.category());
        assertFalse(extraction.isCompleteAndHighConfidence());

        JsonNode roundtrip = new JsonMapper().readTree(new JsonMapper().writeValueAsBytes(extraction));
        assertEquals(6, roundtrip.size(), "Derived business logic must not add a contract field");
        assertTrue(roundtrip.has("vendor_or_party"));
        assertTrue(roundtrip.get("vendor_or_party").isNull());
        assertEquals("2026-09-01", roundtrip.get("date").stringValue());
        assertTrue(roundtrip.get("amount").isNumber());
    }

    @Test
    void acceptsAllUnknownResultWithoutInventingData() {
        OcrExtraction extraction = decode("""
                {"date":null,"amount":null,"vendor_or_party":null,"category":"unknown",
                 "confidence":"low","document_type_detected":"unknown"}
                """);
        assertNull(extraction.date());
        assertNull(extraction.amount());
        assertNull(extraction.vendorOrParty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "[]", "null", "{broken", "{\"date\":null}",
            "{\"date\":null,\"amount\":null,\"vendor_or_party\":null,\"category\":\"unknown\",\"confidence\":\"low\"}"
    })
    void rejectsMissingFieldsAndNonObjects(String json) {
        assertInvalid(json);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"1234.56\"", "true", "{}", "[]", "NaN", "Infinity"})
    void rejectsWrongAmountTypesAndNonfiniteNumbers(String amount) {
        assertInvalid(PARTIAL.replace("1234.56", amount));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "01/09/2026", "2026-9-01", "2026-09-01T00:00:00", "0000-01-01", "+10000-01-01", ""})
    void rejectsAmbiguousOrInvalidDates(String date) {
        assertInvalid(PARTIAL.replace("2026-09-01", date));
    }

    @Test
    void rejectsMalformedEnumsTypesExtraFieldsDuplicatesAndTrailingJson() {
        assertInvalid(PARTIAL.replace("\"unknown\"", "null"));
        assertInvalid(PARTIAL.replace("\"unknown\"", "\"Expense\""));
        assertInvalid(PARTIAL.replace("\"low\"", "0.9"));
        assertInvalid(PARTIAL.replace("\"invoice\"", "\"statement\""));
        assertInvalid(PARTIAL.replace("\"vendor_or_party\":null", "\"vendor_or_party\":42"));
        assertInvalid(PARTIAL.replace("\"date\":", "\"date\":null,\"date\":"));
        assertInvalid(PARTIAL.replace("{", "{\"customer\":null,"));
        assertInvalid(PARTIAL + "{}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "relative.png", "file:///tmp/a.png", "https://", "https://user:secret@example.com/a", "https://example.com/a#fragment"})
    void rejectsInvalidRequestBeforeCallingService(String url) {
        assertThrows(IllegalArgumentException.class, () -> new OcrRequest(url, OcrExtraction.DocumentType.unknown));
    }

    private OcrExtraction decode(String json) {
        return codec.decode(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertInvalid(String json) {
        assertEquals(OcrClientException.Reason.invalid_response,
                assertThrows(OcrClientException.class, () -> decode(json)).reason());
    }
}
