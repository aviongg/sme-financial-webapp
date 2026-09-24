package com.app.sme_health_backend.identity.service;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.security.service.AppUserDetails;
import com.app.sme_health_backend.shared.exception.ActiveBusinessRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ActiveBusinessContext {

    public static final String ACTIVE_BUSINESS_SESSION_ATTR = "FINSIGHT_ACTIVE_BUSINESS_ID";

    private final AppUserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final BusinessMembershipRepository membershipRepository;

    public ActiveBusinessContext(
            AppUserRepository userRepository,
            BusinessRepository businessRepository,
            BusinessMembershipRepository membershipRepository
    ) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.membershipRepository = membershipRepository;
    }

    public BusinessAccessContext getRequiredContext(HttpServletRequest request) {
        UUID userId = resolveAuthenticatedUserId();
        if (userId == null) {
            throw new AccessDeniedException("User is not authenticated");
        }
        return getCurrentContext(request)
                .orElseThrow(ActiveBusinessRequiredException::new);
    }

    public Optional<BusinessAccessContext> getCurrentContext(HttpServletRequest request) {
        UUID userId = resolveAuthenticatedUserId();
        if (userId == null) {
            return Optional.empty();
        }

        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getAccountStatus() != AccountStatus.ACTIVE) {
            return Optional.empty();
        }

        if (request == null) {
            return Optional.empty();
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }

        Object attr = session.getAttribute(ACTIVE_BUSINESS_SESSION_ATTR);
        if (attr == null) {
            return Optional.empty();
        }

        UUID activeBusinessId;
        if (attr instanceof UUID u) {
            activeBusinessId = u;
        } else {
            try {
                activeBusinessId = UUID.fromString(attr.toString());
            } catch (IllegalArgumentException ex) {
                session.removeAttribute(ACTIVE_BUSINESS_SESSION_ATTR);
                return Optional.empty();
            }
        }

        Business business = businessRepository.findById(activeBusinessId).orElse(null);
        if (business == null || !"ACTIVE".equalsIgnoreCase(business.getStatus())) {
            session.removeAttribute(ACTIVE_BUSINESS_SESSION_ATTR);
            return Optional.empty();
        }

        BusinessMembership membership = membershipRepository
                .findByUserIdAndBusinessId(userId, activeBusinessId)
                .orElse(null);

        if (membership == null || membership.getStatus() != MembershipStatus.ACTIVE) {
            session.removeAttribute(ACTIVE_BUSINESS_SESSION_ATTR);
            return Optional.empty();
        }

        return Optional.of(new BusinessAccessContext(userId, activeBusinessId, membership.getRole()));
    }

    public void setActiveBusinessId(HttpServletRequest request, UUID businessId) {
        if (request == null) {
            return;
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(ACTIVE_BUSINESS_SESSION_ATTR, businessId);
    }

    public void clearActiveBusinessId(HttpServletRequest request) {
        if (request == null) {
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(ACTIVE_BUSINESS_SESSION_ATTR);
        }
    }

    public UUID getSessionActiveBusinessId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object attr = session.getAttribute(ACTIVE_BUSINESS_SESSION_ATTR);
        if (attr instanceof UUID u) {
            return u;
        } else if (attr != null) {
            try {
                return UUID.fromString(attr.toString());
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    private UUID resolveAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getPrincipal() instanceof AppUserDetails userDetails) {
            return userDetails.getId();
        } else if (auth.getName() != null) {
            return userRepository.findByEmail(auth.getName())
                    .map(AppUser::getId)
                    .orElse(null);
        }
        return null;
    }
}
