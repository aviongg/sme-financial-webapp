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

        // ACCOUNTANT: financial read/write, scoring, documents, confirmation, deletion, edit/retry, Zakat, accounts read/create, transactions read/post
        map.put(MembershipRole.ACCOUNTANT, Collections.unmodifiableSet(EnumSet.of(
                BusinessPermission.FINANCIAL_DATA_READ,
                BusinessPermission.RECORD_CREATE_UPDATE,
                BusinessPermission.DOCUMENT_UPLOAD,
                BusinessPermission.DOCUMENT_READ,
                BusinessPermission.DOCUMENT_CONFIRM,
                BusinessPermission.DOCUMENT_DELETE,
                BusinessPermission.DOCUMENT_EDIT,
                BusinessPermission.SCORE_CALCULATE,
                BusinessPermission.ZAKAT_READ_CALCULATE,
                BusinessPermission.FINANCIAL_ACCOUNT_READ,
                BusinessPermission.FINANCIAL_ACCOUNT_CREATE,
                BusinessPermission.FINANCIAL_TRANSACTION_READ,
                BusinessPermission.FINANCIAL_TRANSACTION_POST
        )));

        // MANAGER: financial read, document upload/read, Zakat read/calculate, accounts read, transactions read (NO SCORE_CALCULATE, NO DOCUMENT_EDIT, NO CREATION/POSTING)
        map.put(MembershipRole.MANAGER, Collections.unmodifiableSet(EnumSet.of(
                BusinessPermission.FINANCIAL_DATA_READ,
                BusinessPermission.DOCUMENT_UPLOAD,
                BusinessPermission.DOCUMENT_READ,
                BusinessPermission.ZAKAT_READ_CALCULATE,
                BusinessPermission.FINANCIAL_ACCOUNT_READ,
                BusinessPermission.FINANCIAL_TRANSACTION_READ
        )));

        // VIEWER: financial read, accounts read, transactions read (NO SCORE_CALCULATE, NO CREATION/POSTING)
        map.put(MembershipRole.VIEWER, Collections.unmodifiableSet(EnumSet.of(
                BusinessPermission.FINANCIAL_DATA_READ,
                BusinessPermission.FINANCIAL_ACCOUNT_READ,
                BusinessPermission.FINANCIAL_TRANSACTION_READ
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

    /**
     * Enforces active business context and role permission.
     * DOCUMENT_EDIT covers both document draft correction (PATCH) and OCR retry processing.
     *
     * @return the authoritative active BusinessAccessContext
     */
    public BusinessAccessContext requirePermission(HttpServletRequest request, BusinessPermission permission) {
        BusinessAccessContext context = requireActiveContext(request);
        if (!hasRolePermission(context.role(), permission)) {
            throw new AccessDeniedException(
                    "Role " + context.role() + " does not have permission: " + permission
            );
        }
        return context;
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
