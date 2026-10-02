package com.app.sme_health_backend.security.service;

import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.mfa.repository.UserMfaRepository;
import com.app.sme_health_backend.security.filter.AuthenticationStageValidationFilter;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** The single authority for login stages and revalidation of password-verified sessions. */
@Service
public class PreAuthenticationService {
    private final AppUserRepository userRepository;
    private final UserMfaRepository mfaRepository;

    public PreAuthenticationService(AppUserRepository userRepository, UserMfaRepository mfaRepository) {
        this.userRepository = userRepository;
        this.mfaRepository = mfaRepository;
    }

    public String requiredStage(AppUser user) {
        if (user.isMustChangePassword()) {
            return AuthenticationStage.PASSWORD_CHANGE_REQUIRED.name();
        }
        boolean enabled = mfaRepository != null && mfaRepository.findByUserId(user.getId())
                .map(mfa -> "ENABLED".equals(mfa.getStatus())).orElse(false);
        if ("PLATFORM_ADMIN".equals(user.getPlatformRole()) && !enabled) {
            return AuthenticationStage.MFA_ENROLLMENT_REQUIRED.name();
        }
        return enabled ? AuthenticationStage.MFA_CHALLENGE_REQUIRED.name() : null;
    }

    /**
     * A separate read transaction prevents a prior factor/password transaction's entity cache
     * from masking a concurrent security change. Call again immediately before promotion.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public AppUser validate(HttpSession session, String expectedStage) {
        if (session == null) {
            throw invalid(null);
        }
        try {
            Object id = session.getAttribute(AuthenticationStageValidationFilter.FINSIGHT_PRE_AUTH_USER_ID);
            Object stage = session.getAttribute(AuthenticationStageValidationFilter.FINSIGHT_AUTH_STAGE);
            Object expiry = session.getAttribute(AuthenticationStageValidationFilter.FINSIGHT_PRE_AUTH_EXPIRES_AT);
            Object version = session.getAttribute(AuthenticationStageValidationFilter.FINSIGHT_PRE_AUTH_VERSION);
            if (!(id instanceof UUID userId) || !(stage instanceof String storedStage)
                    || expectedStage == null || !expectedStage.equals(storedStage)
                    || !(expiry instanceof Long expiresAt) || System.currentTimeMillis() >= expiresAt
                    || !(version instanceof Long acceptedVersion)) {
                throw invalid(session);
            }
            AppUser user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getAccountStatus() != AccountStatus.ACTIVE
                    || acceptedVersion != user.getAuthVersion()
                    || !storedStage.equals(requiredStage(user))) {
                throw invalid(session);
            }
            return user;
        } catch (IllegalStateException invalidatedSession) {
            throw invalid(session);
        }
    }

    private PreAuthenticationInvalidException invalid(HttpSession session) {
        SecurityContextHolder.clearContext();
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalidated) {
                // Concurrent revocation is also a failed challenge.
            }
        }
        return new PreAuthenticationInvalidException();
    }
}
