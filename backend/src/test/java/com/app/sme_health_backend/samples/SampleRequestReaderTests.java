package com.app.sme_health_backend.samples;

import com.app.sme_health_backend.samples.controller.SampleRequestReader;
import com.app.sme_health_backend.samples.model.*;
import com.app.sme_health_backend.samples.service.SampleValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SampleRequestReaderTests {
    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    private static final String CREATE = "{\"code\":\"S1\",\"name\":\"Sample\",\"kind\":\"SAMPLE\",\"recoveryIntent\":\"FREE\"}";
    private static final String LINES = "[{\"category\":\"MATERIAL\",\"description\":\"D\",\"quantity\":\"1\",\"unit\":\"u\",\"unitCost\":\"2\"}]";
    @Test void readsActivityProjectAndPlanCommands() {
        UUID project = UUID.randomUUID();
        var create = SampleRequestReader.readCreate(bytes(CREATE.replace("}",",\"projectId\":\""+project+"\"}")));
        assertEquals(project,create.projectId()); assertEquals(RecoveryIntent.FREE,create.recoveryIntent());
        var update = SampleRequestReader.readUpdate(bytes(CREATE.replace("}",",\"expectedVersion\":3}")));
        assertEquals(3L,update.expectedVersion());
        var plan = SampleRequestReader.readPlanCreate(bytes("{\"expectedActivityVersion\":3,\"plannedRecovery\":null,\"lines\":"+LINES+"}"));
        assertNull(plan.plannedRecovery()); assertEquals(3L,plan.expectedVersion());
        var changed = SampleRequestReader.readPlanUpdate(bytes("{\"expectedPlanVersion\":2,\"plannedRecovery\":\"0\",\"lines\":"+LINES+"}"));
        assertEquals("0",changed.plannedRecovery()); assertEquals(2L,changed.expectedVersion());
        var approval = SampleRequestReader.readPlanApproval(bytes("{\"expectedActivityVersion\":3,\"expectedPlanVersion\":2}"));
        assertEquals(2L,approval.expectedPlanVersion());
        assertEquals(SampleStatus.ACTIVE, SampleRequestReader.readTransition(bytes("{\"expectedVersion\":0,\"targetStatus\":\"ACTIVE\"}")).targetStatus());
    }
    @ParameterizedTest @ValueSource(strings={"businessId","actorUserId","createdBy","updatedBy","financialBasis","status","approvedBy","supersededBy","plannedCost","actualCost"})
    void rejectsAuthorityFields(String field) {
        var error=assertThrows(SampleValidationException.class, () -> SampleRequestReader.readCreate(bytes(CREATE.replace("}",",\""+field+"\":\"spoof\"}"))));
        assertEquals("unknown_field",error.errors().get("request"));
    }
    @ParameterizedTest @ValueSource(strings={"plannedCost","plannedContribution","status","recoveryIntent","businessId","approvedBy","supersededBy"})
    void rejectsPlanAuthority(String field) {
        var json="{\"expectedActivityVersion\":0,\"lines\":"+LINES+",\""+field+"\":\"spoof\"}";
        assertEquals("unknown_field", assertThrows(SampleValidationException.class, () -> SampleRequestReader.readPlanCreate(bytes(json))).errors().get("request"));
    }
    @Test void rejectsNumbersInsteadOfDecimalStringsAndCalculatedLines() {
        assertThrows(SampleValidationException.class, () -> SampleRequestReader.readPlanCreate(bytes("{\"expectedActivityVersion\":0,\"plannedRecovery\":0,\"lines\":"+LINES+"}")));
        assertThrows(SampleValidationException.class, () -> SampleRequestReader.readPlanCreate(bytes("{\"expectedActivityVersion\":0,\"lines\":"+LINES.replace("\"quantity\":\"1\"","\"quantity\":1")+"}")));
        assertThrows(SampleValidationException.class, () -> SampleRequestReader.readPlanCreate(bytes("{\"expectedActivityVersion\":0,\"lines\":"+LINES.replace("}",",\"plannedLineCost\":\"2\"}")+"}")));
    }
    @Test void rejectsAmbiguousOrOversizedJsonAndMalformedProjectId() {
        assertEquals("json_invalid",assertThrows(SampleValidationException.class, () -> SampleRequestReader.readCreate(bytes(CREATE+" {}"))).errors().get("request"));
        assertEquals("json_invalid",assertThrows(SampleValidationException.class, () -> SampleRequestReader.readCreate(bytes(CREATE.replace("}",",\"code\":\"S2\"}")))).errors().get("request"));
        assertThrows(SampleValidationException.class, () -> SampleRequestReader.readCreate(new byte[SampleRequestReader.MAX_REQUEST_BYTES+1]));
        assertEquals("uuid_invalid",assertThrows(SampleValidationException.class, () -> SampleRequestReader.readCreate(bytes(CREATE.replace("}",",\"projectId\":\"1-1-1-1-1\"}")))).errors().get("projectId"));
    }
    @ParameterizedTest @ValueSource(strings={"-1","0.5","\"0\"","9223372036854775808","null"})
    void rejectsMalformedOptimisticVersions(String version) {
        assertThrows(SampleValidationException.class, () -> SampleRequestReader.readPlanApproval(bytes("{\"expectedActivityVersion\":"+version+",\"expectedPlanVersion\":0}")));
    }
}
