package com.app.sme_health_backend.commercial;

import com.app.sme_health_backend.commercial.controller.CommercialDocumentRequestReader;
import com.app.sme_health_backend.commercial.service.CommercialValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class CommercialDocumentRequestReaderTests {
    private static byte[] json(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, String> rejected(Function<byte[], ?> reader, String body) {
        return assertThrows(CommercialValidationException.class, () -> reader.apply(json(body))).errors();
    }

    @Test
    void createReadsOnlyClientOwnedFieldsAndKeepsDecimalsAsStrings() {
        var request = CommercialDocumentRequestReader.readCreate(json("""
                {"counterpartyName":"Acme","documentDate":"2026-10-28","dueDate":null,"notes":"n",
                 "lines":[{"description":"Work","quantity":"1.5","unit":null,"unitPrice":"200.00"}]}
                """));
        assertEquals("Acme", request.counterpartyName());
        assertNull(request.dueDate());
        assertNull(request.expectedVersion());
        assertEquals("1.5", request.lines().getFirst().quantity());
        assertEquals("200.00", request.lines().getFirst().unitPrice());
        assertTrue(CommercialDocumentRequestReader.readCreate(json("{\"lines\":[]}")).lines().isEmpty());
    }

    @Test
    void serverOwnedAndAuthoritativeFieldsAreRejected() {
        for (String field : List.of("businessId", "actorUserId", "createdBy", "updatedBy", "finalizedBy", "voidedBy",
                "status", "documentNumber", "subtotal", "total", "financialEffect", "kind", "baseCurrency",
                "amountPaid", "amountDue", "outstandingBalance", "paymentStatus", "id", "version")) {
            String body = "{\"lines\":[],\"" + field + "\":\"x\"}";
            assertEquals(Map.of(field, "unknown_field"), rejected(CommercialDocumentRequestReader::readCreate, body), field);
            assertEquals(Map.of(field, "unknown_field"),
                    rejected(CommercialDocumentRequestReader::readUpdate, "{\"expectedVersion\":0,\"lines\":[],\"" + field + "\":1}"), field);
        }
        assertEquals(Map.of("lines[0].lineAmount", "unknown_field"), rejected(CommercialDocumentRequestReader::readPreview,
                "{\"lines\":[{\"description\":\"a\",\"quantity\":\"1\",\"unitPrice\":\"1\",\"lineAmount\":\"1.00\"}]}"));
        assertEquals(Map.of("status", "unknown_field"),
                rejected(CommercialDocumentRequestReader::readFinalize, "{\"expectedVersion\":0,\"status\":\"FINALIZED\"}"));
        assertEquals(Map.of("documentNumber", "unknown_field"),
                rejected(CommercialDocumentRequestReader::readFinalize, "{\"expectedVersion\":0,\"documentNumber\":\"INV-000009\"}"));
        assertEquals(Map.of("voidedBy", "unknown_field"),
                rejected(CommercialDocumentRequestReader::readVoid, "{\"expectedVersion\":1,\"reason\":\"r\",\"voidedBy\":\"x\"}"));
    }

    @Test
    void numericCoercionMalformedJsonDuplicatesAndTrailingTokensAreRejected() {
        assertEquals(Map.of("lines[0].quantity", "string_required"), rejected(CommercialDocumentRequestReader::readPreview,
                "{\"lines\":[{\"description\":\"a\",\"quantity\":1,\"unitPrice\":\"1\"}]}"));
        assertEquals(Map.of("lines[0].unitPrice", "string_required"), rejected(CommercialDocumentRequestReader::readPreview,
                "{\"lines\":[{\"description\":\"a\",\"quantity\":\"1\",\"unitPrice\":1.5}]}"));
        assertEquals(Map.of("request", "json_invalid"), rejected(CommercialDocumentRequestReader::readCreate, "{\"lines\":[],\"lines\":[]}"));
        assertEquals(Map.of("request", "json_invalid"), rejected(CommercialDocumentRequestReader::readCreate, "{\"lines\":[]} {}"));
        assertEquals(Map.of("request", "json_invalid"), rejected(CommercialDocumentRequestReader::readCreate, "{"));
        assertEquals(Map.of("request", "json_invalid"), rejected(CommercialDocumentRequestReader::readCreate, ""));
        assertEquals(Map.of("request", "object_required"), rejected(CommercialDocumentRequestReader::readCreate, "[]"));
        assertEquals(Map.of("lines", "required"), rejected(CommercialDocumentRequestReader::readCreate, "{}"));
        assertEquals(Map.of("lines", "line_count_invalid"), rejected(CommercialDocumentRequestReader::readCreate, "{\"lines\":{}}"));
        assertEquals(Map.of("lines[0]", "object_required"), rejected(CommercialDocumentRequestReader::readCreate, "{\"lines\":[\"x\"]}"));
        assertEquals(Map.of("request", "request_too_large"), assertThrows(CommercialValidationException.class,
                () -> CommercialDocumentRequestReader.readCreate(new byte[CommercialDocumentRequestReader.MAX_REQUEST_BYTES + 1])).errors());
    }

    @Test
    void versionsAndVoidReasonsAreRequiredWithStrictTypes() {
        assertEquals(Map.of("expectedVersion", "required"), rejected(CommercialDocumentRequestReader::readUpdate, "{\"lines\":[]}"));
        assertEquals(Map.of("expectedVersion", "version_invalid"), rejected(CommercialDocumentRequestReader::readFinalize, "{\"expectedVersion\":\"0\"}"));
        assertEquals(Map.of("expectedVersion", "version_invalid"), rejected(CommercialDocumentRequestReader::readFinalize, "{\"expectedVersion\":-1}"));
        assertEquals(Map.of("expectedVersion", "version_invalid"), rejected(CommercialDocumentRequestReader::readFinalize, "{\"expectedVersion\":1.5}"));
        assertEquals(Map.of("reason", "required"), rejected(CommercialDocumentRequestReader::readVoid, "{\"expectedVersion\":1}"));
        assertEquals(Map.of("reason", "string_required"), rejected(CommercialDocumentRequestReader::readVoid, "{\"expectedVersion\":1,\"reason\":5}"));
        assertEquals(3L, CommercialDocumentRequestReader.readFinalize(json("{\"expectedVersion\":3}")).expectedVersion());
        var voidRequest = CommercialDocumentRequestReader.readVoid(json("{\"expectedVersion\":2,\"reason\":\"Duplicate entry\"}"));
        assertEquals("Duplicate entry", voidRequest.reason());
        assertEquals(7L, CommercialDocumentRequestReader.readUpdate(json("{\"expectedVersion\":7,\"lines\":[]}")).expectedVersion());
    }
}
