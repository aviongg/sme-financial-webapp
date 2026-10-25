package com.app.sme_health_backend.reports.controller;

import com.app.sme_health_backend.reports.dto.ReportRequest;
import com.app.sme_health_backend.reports.service.ReportValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ReportRequestReaderTests {
    static ReportRequest read(String json) {
        return ReportRequestReader.read(json.getBytes(StandardCharsets.UTF_8));
    }

    static void rejects(String json, String code) {
        var exception = assertThrows(ReportValidationException.class, () -> read(json), json);
        assertEquals(code, exception.errors().values().iterator().next(), json);
    }

    @Test void acceptsTheClosedSchemaAndLanguageIsOptional() {
        assertEquals(new ReportRequest("2026-01", "2026-03", "ur"), read("{\"fromMonth\":\"2026-01\",\"toMonth\":\"2026-03\",\"language\":\"ur\"}"));
        assertEquals(new ReportRequest("2026-01", "2026-03", null), read("{\"fromMonth\":\"2026-01\",\"toMonth\":\"2026-03\"}"));
        assertEquals(new ReportRequest("2026-01", "2026-03", null), read("{\"fromMonth\":\"2026-01\",\"toMonth\":\"2026-03\",\"language\":null}"));
    }

    @Test void rejectsTenantUserSqlAndMetricInjectionAndEveryUnknownField() {
        for (String field : new String[]{"businessId", "userId", "actorUserId", "sql", "metrics", "expression", "currency", "tenant"}) {
            rejects("{\"fromMonth\":\"2026-01\",\"toMonth\":\"2026-03\",\"" + field + "\":\"x\"}", "unknown_field");
        }
    }

    @Test void rejectsMalformedWrongTypedDuplicateAndTrailingJson() {
        rejects("", "object_required");
        rejects("{", "json_invalid");
        rejects("{\"fromMonth\":\"2026-01\",\"toMonth\":\"2026-03\"} {}", "json_invalid");
        rejects("{\"fromMonth\":\"2026-01\",\"fromMonth\":\"2026-02\",\"toMonth\":\"2026-03\"}", "json_invalid");
        rejects("null", "object_required");
        rejects("[]", "object_required");
        rejects("\"2026-01\"", "object_required");
        rejects("{\"toMonth\":\"2026-03\"}", "required");
        rejects("{\"fromMonth\":null,\"toMonth\":\"2026-03\"}", "required");
        rejects("{\"fromMonth\":202601,\"toMonth\":\"2026-03\"}", "string_required");
        rejects("{\"fromMonth\":\"2026-01\",\"toMonth\":true}", "string_required");
        rejects("{\"fromMonth\":\"2026-01\",\"toMonth\":\"2026-03\",\"language\":1}", "string_required");
        rejects("{\"fromMonth\":\"" + "x".repeat(65) + "\",\"toMonth\":\"2026-03\"}", "json_invalid");
        rejects("{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":1}}}}}", "json_invalid");
    }
}
