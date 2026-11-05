package com.app.sme_health_backend.estimates;

import com.app.sme_health_backend.estimates.controller.EstimatePreviewController;
import com.app.sme_health_backend.estimates.service.*;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.ActiveBusinessContext;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EstimatePreviewBoundaryTests {
    @Test void deniedAuthorizationStopsBeforeParsingOrCalculating() {
        var authorization = mock(BusinessAuthorizationService.class);
        var calculator = mock(PricingCalculator.class);
        var request = new MockHttpServletRequest();
        when(authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ)).thenThrow(new AccessDeniedException("Denied"));
        assertThrows(AccessDeniedException.class, () -> new EstimatePreviewController(authorization, calculator).preview(request));
        verifyNoInteractions(calculator);
        // There is no disallowed active business-role enum in this MVP: every current role has FINANCIAL_DATA_READ.
        var realAuthorization = new BusinessAuthorizationService(mock(ActiveBusinessContext.class));
        assertFalse(realAuthorization.hasRolePermission(null, BusinessPermission.FINANCIAL_DATA_READ));
        for (var role : MembershipRole.values()) assertTrue(realAuthorization.hasRolePermission(role, BusinessPermission.FINANCIAL_DATA_READ));
    }
    @Test void unknownContentLengthBodyStillHasHardByteBoundBeforeJsonParsing() throws Exception {
        var request = new MockHttpServletRequest() { @Override public long getContentLengthLong() { return -1; } };
        request.setContent(" ".repeat(EstimatePreviewController.MAX_REQUEST_BYTES + 1).getBytes(StandardCharsets.UTF_8));
        var authorization = mock(BusinessAuthorizationService.class);
        when(authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ))
                .thenReturn(new BusinessAccessContext(UUID.randomUUID(), UUID.randomUUID(), MembershipRole.OWNER));
        var calculator = mock(PricingCalculator.class);
        assertEquals(413, new EstimatePreviewController(authorization, calculator).preview(request).getStatusCode().value());
        verifyNoInteractions(calculator);
    }
}
