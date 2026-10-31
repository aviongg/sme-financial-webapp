package com.app.sme_health_backend.samples;
import com.app.sme_health_backend.samples.model.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class SampleLifecycleTests {
    @Test void allActivityTransitionsAreExplicitAndArchiveIsTerminal() {
        Set<String> allowed=Set.of("DRAFT:ACTIVE","DRAFT:CANCELLED","DRAFT:ARCHIVED","ACTIVE:COMPLETED","ACTIVE:CANCELLED","ACTIVE:ARCHIVED","COMPLETED:ARCHIVED","CANCELLED:ARCHIVED");
        for (SampleStatus from:SampleStatus.values()) for(SampleStatus to:SampleStatus.values())
            assertEquals(allowed.contains(from+":"+to),from.canTransitionTo(to),from+":"+to);
        for(SampleStatus s:SampleStatus.values()) assertFalse(s.canTransitionTo(null));
    }
    @Test void onlyOpenActivitiesCanMutateAndOnlySafeDraftActivitiesCanDelete() {
        for(SampleStatus s:SampleStatus.values()) {
            assertEquals(s==SampleStatus.DRAFT||s==SampleStatus.ACTIVE,s.allowsPlanningMutations());
            assertEquals(s==SampleStatus.DRAFT,s.canDeleteActivity(false));
            assertFalse(s.canDeleteActivity(true));
        }
    }
    @Test void onlyDraftPlansAreMutableOrDeletable() {
        assertFalse(SamplePlanStatus.DRAFT.isImmutable()); assertTrue(SamplePlanStatus.DRAFT.canDeleteDraft());
        for(SamplePlanStatus s: new SamplePlanStatus[]{SamplePlanStatus.APPROVED,SamplePlanStatus.SUPERSEDED}) {
            assertTrue(s.isImmutable()); assertFalse(s.canDeleteDraft());
        }
    }
}
