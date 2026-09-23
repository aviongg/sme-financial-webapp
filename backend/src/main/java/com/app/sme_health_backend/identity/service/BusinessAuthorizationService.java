package com.app.sme_health_backend.identity.service;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BusinessAuthorizationService {

    private static final Map<MembershipRole, Set<BusinessPermission>> ROLE_PERMISSIONS;

    static {
        Map<MembershipRole, Set<BusinessPermission>> map = new EnumMap<>(MembershipRole.class);

        // OWNER: all business permissions
        map.put(MembershipRole.OWNER, Collections.unmodifiableSet(EnumSet.allOf(BusinessPermission.class)));

        // ACCOUNTANT: financial read/write, scoring, documents, confirmation, deletion, Zakat
        map.put(MembershipRole.ACCOUNTANT, Collections.unmodifiableSet(EnumSet.of(
                BusinessPermission.FINANCIAL_DATA_READ,
                BusinessPermission.RECORD_CREATE_UPDATE,
                BusinessPermission.DOCUMENT_UPLOAD,
                BusinessPermission.DOCUMENT_READ,
                BusinessPermission.DOCUMENT_CONFIRM,
                BusinessPermission.DOCUMENT_DELETE,
                BusinessPermission.SCORE_CALCULATE,
                BusinessPermission.ZAKAT_READ_CALCULATE
        )));

        // MANAGER: financial read, document upload/read, Zakat read/calculate (NO SCORE_CALCULATE)
        map.put(MembershipRole.MANAGER, Collections.unmodifiableSet(EnumSet.of(
                BusinessPermission.FINANCIAL_DATA_READ,
                BusinessPermission.DOCUMENT_UPLOAD,
                BusinessPermission.DOCUMENT_READ,
                BusinessPermission.ZAKAT_READ_CALCULATE
        )));

        // VIEWER: financial read only (NO SCORE_CALCULATE)
        map.put(MembershipRole.VIEWER, Collections.unmodifiableSet(EnumSet.of(
                BusinessPermission.FINANCIAL_DATA_READ
        )));

        ROLE_PERMISSIONS = Collections.unmodifiableMap(map);
    }

    private final ActiveBusinessContext activeBusinessContext;

    public BusinessAuthorizationService(ActiveBusinessContext activeBusinessContext) {
        this.activeBusinessContext = activeBusinessContext;
    }

    public boolean hasPermission(HttpServletRequest request, BusinessPermission permission) {
        if (permission == null) {
            return false;
        }
        Optional<BusinessAccessContext> contextOpt = activeBusinessContext.getCurrentContext(request);
        if (contextOpt.isEmpty()) {
            return false;
        }
        MembershipRole role = contextOpt.get().role();
        return hasRolePermission(role, permission);
    }

    public void requirePermission(HttpServletRequest request, BusinessPermission permission) {
        BusinessAccessContext context = requireActiveContext(request);
        if (!hasRolePermission(context.role(), permission)) {
            throw new AccessDeniedException(
                    "Role " + context.role() + " does not have permission: " + permission
            );
        }
    }

    public BusinessAccessContext requireActiveContext(HttpServletRequest request) {
        return activeBusinessContext.getRequiredContext(request);
    }

    public boolean hasRolePermission(MembershipRole role, BusinessPermission permission) {
        if (role == null || permission == null) {
            return false;
        }
        Set<BusinessPermission> permissions = ROLE_PERMISSIONS.get(role);
        return permissions != null && permissions.contains(permission);
    }

    public Set<BusinessPermission> getPermissionsForRole(MembershipRole role) {
        if (role == null) {
            return Collections.emptySet();
        }
        return ROLE_PERMISSIONS.getOrDefault(role, Collections.emptySet());
    }
}
