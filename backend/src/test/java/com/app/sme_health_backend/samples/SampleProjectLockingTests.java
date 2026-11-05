package com.app.sme_health_backend.samples;

import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.samples.dto.SampleUpdateRequest;
import com.app.sme_health_backend.samples.model.*;
import com.app.sme_health_backend.samples.repository.*;
import com.app.sme_health_backend.samples.service.*;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SampleProjectLockingTests {
    @Test void locksProjectBeforeActivityAndRejectsAStalePreReadWithoutWriting() {
        var activities=mock(SampleRepository.class);
        var plans=mock(SamplePlanRepository.class);
        var audit=mock(SecurityAuditService.class);
        var service=new SamplePlanningService(activities,plans,new SamplePlanCalculator(),audit);
        UUID actor=UUID.randomUUID(),business=UUID.randomUUID(),id=UUID.randomUUID(),project=UUID.randomUUID();
        var now=OffsetDateTime.now();
        var observed=new SampleRecord(id,business,"S-1","Sample",SampleKind.SAMPLE,null,RecoveryIntent.FREE,project,
                SampleStatus.DRAFT,null,null,"PKR",0L,actor,actor,now,now);
        // Another update has changed the relationship/version after our pre-read.
        var locked=new SampleRecord(id,business,"S-1","Sample",SampleKind.SAMPLE,null,RecoveryIntent.FREE,null,
                SampleStatus.DRAFT,null,null,"PKR",1L,actor,actor,now,now);
        when(activities.findById(business,id)).thenReturn(Optional.of(observed));
        when(activities.findForUpdate(business,id)).thenReturn(Optional.of(locked));
        var error=assertThrows(SampleException.class,() -> service.updateActivity(
                new BusinessAccessContext(actor,business,MembershipRole.OWNER),id,
                new SampleUpdateRequest("S-1","Changed",SampleKind.SAMPLE,null,RecoveryIntent.FREE,project,null,null,0L)));
        assertEquals("stale_activity_version",error.code()); assertEquals(409,error.status());
        var order=inOrder(activities);
        order.verify(activities).lockActiveBusiness(business);
        order.verify(activities).findById(business,id);
        order.verify(activities).validateProjectLink(business,project,project);
        order.verify(activities).findForUpdate(business,id);
        verify(activities,never()).updateMetadata(any(),any(),anyLong(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
        verifyNoInteractions(plans,audit);
    }
}
