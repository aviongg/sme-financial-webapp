package com.app.sme_health_backend.estimates.controller;

import com.app.sme_health_backend.estimates.service.EstimateValidationException;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class EstimateRequestReaderTests {
    private static final String CONTENT = """
            "lines":[{"costGroup":"MATERIAL","description":"Steel","quantity":"1.5","unit":"kg","unitCost":"800.00"}],
            "saleableQuantity":null,"pricingMode":"TARGET_MARGIN","pricingValue":"0.20"
            """;
    private static byte[] json(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static Map<String, String> rejected(Function<byte[], ?> reader, String body) {
        return assertThrows(EstimateValidationException.class, () -> reader.apply(json(body))).errors();
    }

    @Test void createAcceptsIdentityAndOptionalDraftWithDecimalStrings() {
        var request = EstimateRequestReader.readCreate(json("{\"code\":\"est-1\",\"name\":\"Order\",\"draft\":{" + CONTENT + "}}"));
        assertEquals("est-1", request.code());
        assertNull(request.description());
        assertEquals("1.5", request.draft().lines().getFirst().quantity());
        assertEquals("0.20", request.draft().pricingValue());
        assertNull(EstimateRequestReader.readCreate(json("{\"code\":\"A\",\"name\":\"B\",\"draft\":null}")).draft());
    }

    @Test void serverOwnedFieldsAreRejectedEverywhere() {
        for (String field : List.of("businessId", "userId", "actorUserId", "createdByUserId", "status", "revision", "version",
                "totalPlannedCost", "plannedBatchPrice", "calculationVersion", "baseCurrency", "currency", "approvedByUserId")) {
            assertEquals(Map.of("request", "unknown_field"), rejected(EstimateRequestReader::readCreate,
                    "{\"code\":\"A\",\"name\":\"B\",\"" + field + "\":\"x\"}"), field);
            assertEquals(Map.of("request", "unknown_field"), rejected(EstimateRequestReader::readRevision,
                    "{" + CONTENT + ",\"expectedVersion\":0,\"" + field + "\":\"x\"}"), field);
        }
        assertEquals(Map.of("draft", "unknown_field"), rejected(EstimateRequestReader::readCreate,
                "{\"code\":\"A\",\"name\":\"B\",\"draft\":{" + CONTENT + ",\"plannedBatchPrice\":\"1\"}}"));
        assertEquals(Map.of("lines[0]", "unknown_field"), rejected(EstimateRequestReader::readRevision,
                "{\"lines\":[{\"costGroup\":\"MATERIAL\",\"description\":\"d\",\"quantity\":\"1\",\"unit\":\"u\",\"unitCost\":\"1\",\"plannedCost\":\"1\"}],"
                        + "\"pricingMode\":\"PROPOSED_PRICE\",\"pricingValue\":\"1\",\"expectedVersion\":0}"));
    }

    @Test void revisionRequiresIntegralExpectedVersionAndStringDecimals() {
        var revision = EstimateRequestReader.readRevision(json("{" + CONTENT + ",\"expectedVersion\":3}"));
        assertEquals(3L, revision.expectedVersion());
        assertEquals(Map.of("expectedVersion", "required"), rejected(EstimateRequestReader::readRevision, "{" + CONTENT + "}"));
        assertEquals(Map.of("expectedVersion", "version_invalid"), rejected(EstimateRequestReader::readRevision, "{" + CONTENT + ",\"expectedVersion\":-1}"));
        assertEquals(Map.of("expectedVersion", "version_invalid"), rejected(EstimateRequestReader::readRevision, "{" + CONTENT + ",\"expectedVersion\":\"1\"}"));
        assertEquals(Map.of("pricingValue", "string_required"), rejected(EstimateRequestReader::readRevision,
                "{\"lines\":[{\"costGroup\":\"MATERIAL\",\"description\":\"d\",\"quantity\":\"1\",\"unit\":\"u\",\"unitCost\":\"1\"}],"
                        + "\"pricingMode\":\"PROPOSED_PRICE\",\"pricingValue\":1,\"expectedVersion\":0}"));
        assertEquals(Map.of("request", "json_invalid"), rejected(EstimateRequestReader::readTransition,
                "{\"expectedVersion\":1,\"expectedVersion\":2}"));
        assertEquals(Map.of("request", "json_invalid"), rejected(EstimateRequestReader::readTransition, "{\"expectedVersion\":1} {}"));
    }

    @Test void conversionAcceptsOnlyTargetProjectAndExpectedVersion() {
        UUID project = UUID.randomUUID();
        var request = EstimateRequestReader.readConversion(json("{\"targetProjectId\":\"" + project + "\",\"expectedProjectVersion\":2}"));
        assertEquals(project, request.targetProjectId());
        assertEquals(2L, request.expectedProjectVersion());
        for (String forbidden : List.of("businessId", "plannedRevenue", "plannedCost", "budgetTotal", "revision", "lineage",
                "convertedByUserId", "projectBudgetVersionId", "sourceEstimateRef")) {
            assertEquals(Map.of("request", "unknown_field"), rejected(EstimateRequestReader::readConversion,
                    "{\"targetProjectId\":\"" + project + "\",\"expectedProjectVersion\":0,\"" + forbidden + "\":\"x\"}"), forbidden);
        }
        assertEquals(Map.of("targetProjectId", "uuid_invalid"), rejected(EstimateRequestReader::readConversion,
                "{\"targetProjectId\":\"not-a-uuid\",\"expectedProjectVersion\":0}"));
        assertEquals(Map.of("targetProjectId", "required"), rejected(EstimateRequestReader::readConversion, "{\"expectedProjectVersion\":0}"));
        assertEquals(Map.of("expectedProjectVersion", "required"), rejected(EstimateRequestReader::readConversion,
                "{\"targetProjectId\":\"" + project + "\"}"));
    }

    @Test void phaseAPreviewReaderIsUnchanged() {
        assertEquals("800.00", EstimateRequestReader.read(json("{" + CONTENT + "}")).lines().getFirst().unitCost());
        assertEquals(Map.of("request", "unknown_field"), rejected(EstimateRequestReader::read, "{" + CONTENT + ",\"expectedVersion\":0}"));
    }
}
