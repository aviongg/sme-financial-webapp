package com.app.sme_health_backend.inventory;

import com.app.sme_health_backend.inventory.controller.InventoryRequestReader;
import com.app.sme_health_backend.inventory.dto.ValuationPreviewResponse;
import com.app.sme_health_backend.inventory.model.InventoryItemKind;
import com.app.sme_health_backend.inventory.service.InventoryValidationException;
import com.app.sme_health_backend.inventory.service.InventoryValuationPreviewService;
import com.app.sme_health_backend.inventory.service.WeightedAverageCostCalculator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Closed request schemas and the stateless, non-authoritative preview mapping. */
class InventoryRequestAndPreviewTests {
    private final InventoryValuationPreviewService preview =
            new InventoryValuationPreviewService(new WeightedAverageCostCalculator());

    private static byte[] body(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String code(Runnable action) {
        var ex = assertThrows(InventoryValidationException.class, action::run);
        var entry = ex.errors().entrySet().iterator().next();
        return entry.getKey() + "=" + entry.getValue();
    }

    private ValuationPreviewResponse run(String json) {
        return preview.preview(UUID.randomUUID(), InventoryRequestReader.readPreview(body(json)));
    }

    @Test
    void itemSchemaIsClosedAndCarriesNoStockFields() {
        var create = InventoryRequestReader.readCreate(body("""
                {"sku":"rm-001","name":"Cotton yarn","unit":"kg","itemKind":"RAW_MATERIAL","category":null,"reorderLevel":"12.5"}
                """));
        assertEquals(InventoryItemKind.RAW_MATERIAL, create.itemKind());
        assertNull(create.expectedVersion());
        for (String forbidden : new String[] {"quantity", "onHand", "value", "averageCost", "businessId", "status", "openingStock"}) {
            assertEquals("request=unknown_field", code(() -> InventoryRequestReader.readCreate(body(
                    "{\"sku\":\"A\",\"name\":\"n\",\"unit\":\"u\",\"itemKind\":\"CONSUMABLE\",\"" + forbidden + "\":\"1\"}"))), forbidden);
        }
        assertEquals("expectedVersion=required", code(() -> InventoryRequestReader.readUpdate(body(
                "{\"sku\":\"A\",\"name\":\"n\",\"unit\":\"u\",\"itemKind\":\"CONSUMABLE\"}"))));
        assertEquals("expectedVersion=version_invalid", code(() -> InventoryRequestReader.readArchive(body("{\"expectedVersion\":-1}"))));
        assertEquals("itemKind=enum_invalid", code(() -> InventoryRequestReader.readCreate(body(
                "{\"sku\":\"A\",\"name\":\"n\",\"unit\":\"u\",\"itemKind\":\"WIP\"}"))));
        assertEquals("reorderLevel=string_required", code(() -> InventoryRequestReader.readCreate(body(
                "{\"sku\":\"A\",\"name\":\"n\",\"unit\":\"u\",\"itemKind\":\"CONSUMABLE\",\"reorderLevel\":5}"))));
        assertEquals("request=json_invalid", code(() -> InventoryRequestReader.readCreate(body(
                "{\"sku\":\"A\",\"sku\":\"B\",\"name\":\"n\",\"unit\":\"u\",\"itemKind\":\"CONSUMABLE\"}"))));
        assertEquals("request=json_invalid", code(() -> InventoryRequestReader.readArchive(body("{\"expectedVersion\":1} {}"))));
    }

    @Test
    void previewIsLabelledNonAuthoritativeAndReturnsEveryIntermediateState() {
        var response = run("""
                {"openingQuantity":"10","openingValue":"1000","steps":[
                  {"type":"RECEIPT","quantity":"10","value":"2000"},
                  {"type":"ISSUE","quantity":"4"},
                  {"type":"ISSUE","quantity":"16"}]}
                """);
        assertEquals("PLANNING_VALUATION_PREVIEW", response.mode());
        assertFalse(response.authoritative());
        assertFalse(response.persisted());
        assertEquals("NONE", response.financialEffect());
        assertEquals("f08-wac-draft-1", response.policy().version());
        assertEquals("DRAFT_NOT_APPROVED_ACCOUNTING_POLICY", response.policy().status());
        assertEquals("HALF_EVEN", response.policy().valueRounding());
        assertEquals(new ValuationPreviewResponse.State("10", "1000.0000", "100.000000"), response.opening());
        assertEquals(new ValuationPreviewResponse.State("20", "3000.0000", "150.000000"), response.steps().get(0).after());
        assertEquals("2000.0000", response.steps().get(0).receiptValue());
        assertNull(response.steps().get(0).issueValue());
        assertEquals("600.0000", response.steps().get(1).issueValue());
        assertEquals(new ValuationPreviewResponse.State("16", "2400.0000", "150.000000"), response.steps().get(1).after());
        assertTrue(response.steps().get(2).fullDepletion());
        assertEquals(new ValuationPreviewResponse.State("0", "0.0000", null), response.closing());
        assertEquals("3000.0000", response.totals().issuedValue());
        assertTrue(response.totals().valueConserved());
    }

    @Test
    void previewRejectsInexactOrCoercedInput() {
        assertEquals("openingValue=decimal_invalid", code(() -> run(
                "{\"openingQuantity\":\"1\",\"openingValue\":\"1.00001\",\"steps\":[]}")));
        assertEquals("openingQuantity=decimal_invalid", code(() -> run(
                "{\"openingQuantity\":\"1e3\",\"openingValue\":\"1\",\"steps\":[]}")));
        assertEquals("openingQuantity=decimal_invalid", code(() -> run(
                "{\"openingQuantity\":\"-1\",\"openingValue\":\"0\",\"steps\":[]}")));
        assertEquals("openingQuantity=string_required", code(() -> run(
                "{\"openingQuantity\":1,\"openingValue\":\"0\",\"steps\":[]}")));
        assertEquals("steps[0].quantity=decimal_invalid", code(() -> run(
                "{\"openingQuantity\":\"0\",\"openingValue\":\"0\",\"steps\":[{\"type\":\"RECEIPT\",\"quantity\":\"1.1234567\",\"value\":\"1\"}]}")));
        assertEquals("steps[0].value=value_not_allowed", code(() -> run(
                "{\"openingQuantity\":\"5\",\"openingValue\":\"5\",\"steps\":[{\"type\":\"ISSUE\",\"quantity\":\"1\",\"value\":\"1\"}]}")));
        assertEquals("steps[0].type=enum_invalid", code(() -> run(
                "{\"openingQuantity\":\"5\",\"openingValue\":\"5\",\"steps\":[{\"type\":\"ADJUSTMENT\",\"quantity\":\"1\"}]}")));
        assertEquals("steps[0]=unknown_field", code(() -> run(
                "{\"openingQuantity\":\"5\",\"openingValue\":\"5\",\"steps\":[{\"type\":\"ISSUE\",\"quantity\":\"1\",\"itemId\":\"x\"}]}")));
        assertEquals("request=unknown_field", code(() -> run(
                "{\"openingQuantity\":\"5\",\"openingValue\":\"5\",\"steps\":[],\"persist\":\"true\"}")));
        assertEquals("steps[1].quantity=insufficient_stock", code(() -> run(
                "{\"openingQuantity\":\"5\",\"openingValue\":\"5\",\"steps\":[{\"type\":\"ISSUE\",\"quantity\":\"5\"},{\"type\":\"ISSUE\",\"quantity\":\"1\"}]}")));
        assertEquals("steps=step_count_invalid", code(() -> run("{\"openingQuantity\":\"5\",\"openingValue\":\"5\"}")));
    }
}
