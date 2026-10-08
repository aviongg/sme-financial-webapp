package com.app.sme_health_backend.identity.service;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessAuthorizationServiceTest {

    @Mock
    private ActiveBusinessContext activeBusinessContext;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private BusinessAuthorizationService authorizationService;

    private UUID businessId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("OWNER must possess all permissions, including SCORE_CALCULATE")
    void ownerShouldHaveAllPermissions() {
        Set<BusinessPermission> ownerPermissions = authorizationService.getPermissionsForRole(MembershipRole.OWNER);

        for (BusinessPermission permission : BusinessPermission.values()) {
            assertThat(ownerPermissions).contains(permission);
            assertThat(authorizationService.hasRolePermission(MembershipRole.OWNER, permission)).isTrue();
        }
    }

    @Test
    @DisplayName("ACCOUNTANT has SCORE_CALCULATE and financial permissions, but cannot manage settings or members")
    void accountantPermissions() {
        assertThat(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.SCORE_CALCULATE)).isTrue();
        assertThat(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.FINANCIAL_DATA_READ)).isTrue();
        assertThat(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.RECORD_CREATE_UPDATE)).isTrue();
        assertThat(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.DOCUMENT_UPLOAD)).isTrue();
        assertThat(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.DOCUMENT_READ)).isTrue();
        assertThat(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.DOCUMENT_CONFIRM)).isTrue();
        assertThat(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.DOCUMENT_DELETE)).isTrue();
        assertThat(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.ZAKAT_READ_CALCULATE)).isTrue();

        // Mutating membership/settings not allowed
        assertThat(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.BUSINESS_SETTINGS_MANAGE)).isFalse();
        assertThat(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.MEMBERSHIP_MANAGE)).isFalse();
        assertThat(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.WHATSAPP_CONFIG_MANAGE)).isFalse();
    }

    @Test
    @DisplayName("MANAGER cannot SCORE_CALCULATE or mutate records, but can upload/read documents and calculate Zakat")
    void managerPermissions() {
        assertThat(authorizationService.hasRolePermission(MembershipRole.MANAGER, BusinessPermission.FINANCIAL_DATA_READ)).isTrue();
        assertThat(authorizationService.hasRolePermission(MembershipRole.MANAGER, BusinessPermission.DOCUMENT_UPLOAD)).isTrue();
        assertThat(authorizationService.hasRolePermission(MembershipRole.MANAGER, BusinessPermission.DOCUMENT_READ)).isTrue();
        assertThat(authorizationService.hasRolePermission(MembershipRole.MANAGER, BusinessPermission.ZAKAT_READ_CALCULATE)).isTrue();

        // Must NOT have SCORE_CALCULATE
        assertThat(authorizationService.hasRolePermission(MembershipRole.MANAGER, BusinessPermission.SCORE_CALCULATE)).isFalse();
        assertThat(authorizationService.hasRolePermission(MembershipRole.MANAGER, BusinessPermission.RECORD_CREATE_UPDATE)).isFalse();
        assertThat(authorizationService.hasRolePermission(MembershipRole.MANAGER, BusinessPermission.DOCUMENT_CONFIRM)).isFalse();
        assertThat(authorizationService.hasRolePermission(MembershipRole.MANAGER, BusinessPermission.DOCUMENT_DELETE)).isFalse();
    }

    @Test
    @DisplayName("VIEWER has only FINANCIAL_DATA_READ and strictly lacks SCORE_CALCULATE")
    void viewerPermissions() {
        assertThat(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.FINANCIAL_DATA_READ)).isTrue();

        assertThat(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.SCORE_CALCULATE)).isFalse();
        assertThat(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.RECORD_CREATE_UPDATE)).isFalse();
        assertThat(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.DOCUMENT_UPLOAD)).isFalse();
        assertThat(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.DOCUMENT_READ)).isFalse();
        assertThat(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.DOCUMENT_CONFIRM)).isFalse();
        assertThat(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.DOCUMENT_DELETE)).isFalse();
        assertThat(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.ZAKAT_READ_CALCULATE)).isFalse();
    }

    @Test
    @DisplayName("hasPermission returns false when no active business context exists")
    void hasPermissionFalseWhenNoContext() {
        when(activeBusinessContext.getCurrentContext(request)).thenReturn(Optional.empty());

        assertThat(authorizationService.hasPermission(request, BusinessPermission.FINANCIAL_DATA_READ)).isFalse();
    }

    @Test
    @DisplayName("requirePermission throws AccessDeniedException when permission is missing")
    void requirePermissionThrowsWhenDenied() {
        BusinessAccessContext context = new BusinessAccessContext(
                userId,
                businessId,
                MembershipRole.VIEWER
        );
        when(activeBusinessContext.getRequiredContext(request)).thenReturn(context);

        assertThatThrownBy(() -> authorizationService.requirePermission(request, BusinessPermission.SCORE_CALCULATE))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Role VIEWER does not have permission: SCORE_CALCULATE");
    }

    @Test
    @DisplayName("requirePermission passes when role has permission")
    void requirePermissionPassesWhenAllowed() {
        BusinessAccessContext context = new BusinessAccessContext(
                userId,
                businessId,
                MembershipRole.ACCOUNTANT
        );
        when(activeBusinessContext.getRequiredContext(request)).thenReturn(context);

        // Should not throw
        authorizationService.requirePermission(request, BusinessPermission.SCORE_CALCULATE);
    }
}
