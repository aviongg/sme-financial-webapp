package com.app.sme_health_backend.projects;

import com.app.sme_health_backend.projects.controller.ProjectRequestReader;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
import com.app.sme_health_backend.projects.model.ProjectKind;
import com.app.sme_health_backend.projects.model.ProjectStatus;
import com.app.sme_health_backend.projects.service.ProjectValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ProjectRequestReaderTests {

    private static byte[] utf8(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void readsValidCreateUpdateTransitionAndBudgetPayloads() {
        var create = ProjectRequestReader.readCreate(utf8("""
                {
                  "code": "PRJ-001",
                  "name": "Order 1",
                  "kind": "ORDER",
                  "description": "Initial planning",
                  "startDate": "2026-10-25",
                  "targetEndDate": "2026-12-01"
                }
                """));
        assertEquals("PRJ-001", create.code());
        assertEquals(ProjectKind.ORDER, create.kind());

        var update = ProjectRequestReader.readUpdate(utf8("""
                {
                  "code": "PRJ-001",
                  "name": "Order 1 Revised",
                  "kind": "JOB",
                  "description": null,
                  "startDate": null,
                  "targetEndDate": null,
                  "expectedVersion": 3
                }
                """));
        assertEquals(3L, update.expectedVersion());
        assertEquals(ProjectKind.JOB, update.kind());

        var transition = ProjectRequestReader.readTransition(utf8("""
                {
                  "targetStatus": "ACTIVE",
                  "reason": "Ready to start",
                  "expectedVersion": 3
                }
                """));
        assertEquals(ProjectStatus.ACTIVE, transition.targetStatus());
        assertEquals(3L, transition.expectedVersion());

        var budgetCreate = ProjectRequestReader.readBudgetCreate(utf8("""
                {
                  "expectedProjectVersion": 4,
                  "plannedRevenue": "1500.00",
                  "lines": [
                    {
                      "category": "MATERIAL",
                      "description": "Raw material",
                      "quantity": "10",
                      "unit": "kg",
                      "unitCost": "80.00"
                    }
                  ]
                }
                """));
        assertEquals(4L, budgetCreate.expectedVersion());
        assertEquals("1500.00", budgetCreate.plannedRevenue());
        assertEquals(1, budgetCreate.lines().size());
        assertEquals(ProjectCostCategory.MATERIAL, budgetCreate.lines().getFirst().category());

        var budgetApprove = ProjectRequestReader.readBudgetApproval(utf8("""
                {
                  "expectedProjectVersion": 5,
                  "expectedBudgetVersion": 0
                }
                """));
        assertEquals(5L, budgetApprove.expectedProjectVersion());
        assertEquals(0L, budgetApprove.expectedBudgetVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"code":"P1","name":"N","kind":"PROJECT","businessId":"00000000-0000-0000-0000-000000000001"}
            """,
            """
            {"code":"P1","name":"N","kind":"PROJECT","actorUserId":"00000000-0000-0000-0000-000000000001"}
            """,
            """
            {"code":"P1","name":"N","kind":"PROJECT","createdBy":"00000000-0000-0000-0000-000000000001"}
            """,
            """
            {"code":"P1","name":"N","kind":"PROJECT","baseCurrency":"USD"}
            """,
            """
            {"code":"P1","name":"N","kind":"PROJECT","status":"ACTIVE"}
            """
    })
    void rejectsSpoofedTenantActorCurrencyOrStatusOnProjectCreate(String payload) {
        var ex = assertThrows(ProjectValidationException.class, () -> ProjectRequestReader.readCreate(utf8(payload)));
        assertEquals("unknown_field", ex.errors().get("request"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"expectedProjectVersion":0,"plannedRevenue":"1000.00","plannedCost":"800.00","lines":[{"category":"MATERIAL","description":"D","quantity":"1","unit":"u","unitCost":"800.00"}]}
            """,
            """
            {"expectedProjectVersion":0,"plannedRevenue":"1000.00","plannedMargin":"200.00","lines":[{"category":"MATERIAL","description":"D","quantity":"1","unit":"u","unitCost":"800.00"}]}
            """,
            """
            {"expectedProjectVersion":0,"plannedRevenue":"1000.00","plannedMarginPct":"0.20","lines":[{"category":"MATERIAL","description":"D","quantity":"1","unit":"u","unitCost":"800.00"}]}
            """,
            """
            {"expectedProjectVersion":0,"plannedRevenue":"1000.00","businessId":"other","lines":[{"category":"MATERIAL","description":"D","quantity":"1","unit":"u","unitCost":"800.00"}]}
            """,
            """
            {"expectedProjectVersion":0,"plannedRevenue":"1000.00","approvedBy":"actor","lines":[{"category":"MATERIAL","description":"D","quantity":"1","unit":"u","unitCost":"800.00"}]}
            """
    })
    void rejectsCallerSuppliedTotalsTenantOrApprovalAuthorityOnBudgetWrite(String payload) {
        var ex = assertThrows(ProjectValidationException.class, () -> ProjectRequestReader.readBudgetCreate(utf8(payload)));
        assertEquals("unknown_field", ex.errors().get("request"));
    }

    @Test
    void rejectsCallerSuppliedPlannedLineCostInBudgetLine() {
        String payload = """
                {
                  "expectedProjectVersion": 0,
                  "plannedRevenue": "1000.00",
                  "lines": [
                    {
                      "category": "MATERIAL",
                      "description": "D",
                      "quantity": "1",
                      "unit": "u",
                      "unitCost": "800.00",
                      "plannedLineCost": "800.00"
                    }
                  ]
                }
                """;
        var ex = assertThrows(ProjectValidationException.class, () -> ProjectRequestReader.readBudgetCreate(utf8(payload)));
        assertEquals("unknown_field", ex.errors().get("lines[0]"));
    }

    @Test
    void rejectsNumericJsonTokensForDecimalStringFields() {
        String numericRevenue = """
                {
                  "expectedProjectVersion": 0,
                  "plannedRevenue": 1000.00,
                  "lines": [
                    {
                      "category": "MATERIAL",
                      "description": "D",
                      "quantity": "1",
                      "unit": "u",
                      "unitCost": "800.00"
                    }
                  ]
                }
                """;
        var ex = assertThrows(ProjectValidationException.class, () -> ProjectRequestReader.readBudgetCreate(utf8(numericRevenue)));
        assertEquals("string_required", ex.errors().get("plannedRevenue"));

        String numericQuantity = """
                {
                  "expectedProjectVersion": 0,
                  "plannedRevenue": "1000.00",
                  "lines": [
                    {
                      "category": "MATERIAL",
                      "description": "D",
                      "quantity": 1,
                      "unit": "u",
                      "unitCost": "800.00"
                    }
                  ]
                }
                """;
        var ex2 = assertThrows(ProjectValidationException.class, () -> ProjectRequestReader.readBudgetCreate(utf8(numericQuantity)));
        assertEquals("string_required", ex2.errors().get("lines[0].quantity"));
    }

    @Test
    void rejectsDuplicateKeysTrailingTokensAndOversizedPayloads() {
        String duplicateKey = """
                {"code":"P1","code":"P2","name":"N","kind":"PROJECT"}
                """;
        assertEquals("json_invalid", assertThrows(ProjectValidationException.class,
                () -> ProjectRequestReader.readCreate(utf8(duplicateKey))).errors().get("request"));

        String trailingToken = """
                {"code":"P1","name":"N","kind":"PROJECT"} {"extra":true}
                """;
        assertEquals("json_invalid", assertThrows(ProjectValidationException.class,
                () -> ProjectRequestReader.readCreate(utf8(trailingToken))).errors().get("request"));

        byte[] oversized = " ".repeat(ProjectRequestReader.MAX_REQUEST_BYTES + 1).getBytes(StandardCharsets.UTF_8);
        assertEquals("request_too_large", assertThrows(ProjectValidationException.class,
                () -> ProjectRequestReader.readCreate(oversized)).errors().get("request"));
    }
}
