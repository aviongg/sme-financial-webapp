package com.app.sme_health_backend.identity.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.identity.credential.dto.ChangePasswordRequest;
import com.app.sme_health_backend.identity.dto.LoginRequest;
import com.app.sme_health_backend.identity.dto.RegisterRequest;
import com.app.sme_health_backend.identity.dto.UserResponse;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.identity.validation.EmailValidator;
import com.app.sme_health_backend.identity.validation.PasswordValidator;
import com.app.sme_health_backend.mfa.service.MfaService;
import com.app.sme_health_backend.security.filter.AuthenticationStageValidationFilter;
import com.app.sme_health_backend.security.filter.SessionMaxLifetimeFilter;
import com.app.sme_health_backend.security.service.AppUserDetails;
import com.app.sme_health_backend.security.service.SessionRevocationService;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthenticationService {

    private static final int MAX_PRE_AUTH_ATTEMPTS = 5;
    private static final long PRE_AUTH_TTL_MILLIS = 300_000L; // 5 minutes

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final BusinessMembershipRepository membershipRepository;
    private final BusinessRepository businessRepository;
    private final MfaService mfaService;
    private final SessionRevocationService sessionRevocationService;
    private final SecurityAuditService auditService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthenticationService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            BusinessMembershipRepository membershipRepository,
            BusinessRepository businessRepository
    ) {
        this(userRepository, passwordEncoder, authenticationManager, sessionAuthenticationStrategy,
                membershipRepository, businessRepository, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthenticationService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            BusinessMembershipRepository membershipRepository,
            BusinessRepository businessRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) MfaService mfaService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SessionRevocationService sessionRevocationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SecurityAuditService auditService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.membershipRepository = membershipRepository;
        this.businessRepository = businessRepository;
        this.mfaService = mfaService;
        this.sessionRevocationService = sessionRevocationService;
        this.auditService = auditService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Registration request is required");
        }
        String normalizedEmail = EmailValidator.normalizeAndValidate(request.email());
        PasswordValidator.validate(request.password());

        if (request.fullName() == null || request.fullName().isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        String fullName = request.fullName().trim();
        if (fullName.length() > 150) {
            throw new IllegalArgumentException("Full name must not exceed 150 characters");
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("An account with email " + normalizedEmail + " already exists");
        }

        AppUser user = new AppUser();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(fullName);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setPlatformRole(null);
        user.setAuthVersion(0L);

        AppUser saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
    }

    public UserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        if (request == null) {
            throw new IllegalArgumentException("Login request is required");
        }
        String normalizedEmail = EmailValidator.normalizeAndValidate(request.email());
        if (request.password() == null || request.password().isBlank()) {
            throw new BadCredentialsException("Password is required");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
        } catch (BadCredentialsException ex) {
            AppUser user = userRepository.findByEmail(normalizedEmail).orElse(null);
            if (auditService != null) {
                auditService.recordSecurityEvent(
                        AuditEventType.AUTH_LOGIN_FAILURE,
                        user != null ? user.getId() : null,
                        user != null ? user.getPlatformRole() : null,
                        null,
                        "USER",
                        user != null ? user.getId().toString() : "UNKNOWN",
                        AuditOutcome.FAILURE,
                        httpRequest,
                        Map.of("reason", "bad_credentials")
                );
            }
            throw new BadCredentialsException("Invalid email or password");
        }

        AppUser user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            if (auditService != null) {
                auditService.recordSecurityEvent(
                        AuditEventType.AUTH_LOGIN_FAILURE,
                        user.getId(),
                        user.getPlatformRole(),
                        null,
                        "USER",
                        user.getId().toString(),
                        AuditOutcome.FAILURE,
                        httpRequest,
                        Map.of("reason", "account_disabled")
                );
            }
            throw new DisabledException("Account is disabled");
        }

        boolean mfaEnrolled = mfaService != null && mfaService.isMfaEnabled(user.getId());
        boolean isPlatformAdmin = "PLATFORM_ADMIN".equals(user.getPlatformRole());

        // Authoritative Authentication State Machine
        if (user.isMustChangePassword()) {
            return enterPreAuthStage(user, "PASSWORD_CHANGE_REQUIRED", httpRequest, httpResponse);
        } else if (isPlatformAdmin && !mfaEnrolled) {
            return enterPreAuthStage(user, "MFA_ENROLLMENT_REQUIRED", httpRequest, httpResponse);
        } else if (mfaEnrolled) {
            return enterPreAuthStage(user, "MFA_CHALLENGE_REQUIRED", httpRequest, httpResponse);
        } else {
            return establishFullAuthentication(user, httpRequest, httpResponse);
        }
    }

    private UserResponse enterPreAuthStage(
            AppUser user,
            String authStage,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        SecurityContextHolder.clearContext();
        if (httpRequest != null) {
            HttpSession session = httpRequest.getSession(true);

            session.setAttribute(AuthenticationStageValidationFilter.FINSIGHT_PRE_AUTH_USER_ID, user.getId());
            session.setAttribute(AuthenticationStageValidationFilter.FINSIGHT_AUTH_STAGE, authStage);
            session.setAttribute(
                    AuthenticationStageValidationFilter.FINSIGHT_PRE_AUTH_EXPIRES_AT,
                    System.currentTimeMillis() + PRE_AUTH_TTL_MILLIS
            );
            session.setAttribute(AuthenticationStageValidationFilter.FINSIGHT_FAILED_CHALLENGES, 0);

            // Explicitly clear any active business from previous sessions
            session.removeAttribute(ActiveBusinessContext.ACTIVE_BUSINESS_SESSION_ATTR);

            // Rotate session ID entering sensitive pre-auth stage
            if (sessionAuthenticationStrategy != null) {
                Authentication dummyAuth = new UsernamePasswordAuthenticationToken(user.getEmail(), "");
                sessionAuthenticationStrategy.onAuthentication(dummyAuth, httpRequest, httpResponse);
            }
        }

        return UserResponse.fromEntity(user, authStage);
    }

    public UserResponse establishFullAuthentication(
            AppUser user,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        AppUserDetails userDetails = new AppUserDetails(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );

        if (httpRequest != null) {
            // Rotate session ID establishing full authentication
            if (sessionAuthenticationStrategy != null) {
                sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
            }

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(SessionMaxLifetimeFilter.SESSION_AUTH_TIME_ATTR, System.currentTimeMillis());
            session.setAttribute(AuthenticationStageValidationFilter.FINSIGHT_AUTH_VERSION, user.getAuthVersion());

            // Clear pre-auth temporary markers
            session.removeAttribute(AuthenticationStageValidationFilter.FINSIGHT_PRE_AUTH_USER_ID);
            session.removeAttribute(AuthenticationStageValidationFilter.FINSIGHT_AUTH_STAGE);
            session.removeAttribute(AuthenticationStageValidationFilter.FINSIGHT_PRE_AUTH_EXPIRES_AT);
            session.removeAttribute(AuthenticationStageValidationFilter.FINSIGHT_FAILED_CHALLENGES);

            // Resolve active business context
            session.removeAttribute(ActiveBusinessContext.ACTIVE_BUSINESS_SESSION_ATTR);
            List<BusinessMembership> memberships = membershipRepository.findByUserId(user.getId());
            List<UUID> activeBusinessIds = memberships.stream()
                    .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                    .map(BusinessMembership::getBusinessId)
                    .filter(bid -> businessRepository.findById(bid).map(b -> "ACTIVE".equalsIgnoreCase(b.getStatus())).orElse(false))
                    .toList();

            if (activeBusinessIds.size() == 1) {
                session.setAttribute(ActiveBusinessContext.ACTIVE_BUSINESS_SESSION_ATTR, activeBusinessIds.get(0));
            }
        } else {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }

        if (auditService != null) {
            auditService.recordEvent(
                    AuditEventType.AUTH_LOGIN_SUCCESS,
                    user.getId(),
                    user.getPlatformRole(),
                    null,
                    "USER",
                    user.getId().toString(),
                    AuditOutcome.SUCCESS,
                    httpRequest,
                    Map.of("auth_stage", "FULLY_AUTHENTICATED")
            );
        }

        return UserResponse.fromEntity(user, "FULLY_AUTHENTICATED");
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, HttpServletRequest httpRequest) {
        if (request == null) {
            throw new IllegalArgumentException("Change password request is required");
        }
        PasswordValidator.validate(request.newPassword());

        AppUser user = resolveUserForPasswordChange(httpRequest);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            if (auditService != null) {
                auditService.recordSecurityEvent(
                        AuditEventType.PASSWORD_CHANGED,
                        user.getId(),
                        user.getPlatformRole(),
                        null,
                        "USER",
                        user.getId().toString(),
                        AuditOutcome.FAILURE,
                        httpRequest,
                        Map.of("reason", "bad_current_password")
                );
            }
            throw new BadCredentialsException("Current password does not match");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must not be the same as the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setAuthVersion(user.getAuthVersion() + 1);
        userRepository.save(user);

        if (auditService != null) {
            auditService.recordEvent(
                    AuditEventType.PASSWORD_CHANGED,
                    user.getId(),
                    user.getPlatformRole(),
                    null,
                    "USER",
                    user.getId().toString(),
                    AuditOutcome.SUCCESS,
                    httpRequest,
                    Map.of("auth_version", user.getAuthVersion())
            );
        }

        // Revoke all sessions cluster-wide and invalidate current session, requiring fresh login
        if (sessionRevocationService != null) {
            sessionRevocationService.revokeAllSessions(user.getEmail(), user.getId(), "PASSWORD_CHANGE");
        }
        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest != null ? httpRequest.getSession(false) : null;
        if (session != null) {
            session.invalidate();
        }
    }

    private AppUser resolveUserForPasswordChange(HttpServletRequest httpRequest) {
        // Check pre-auth stage first
        HttpSession session = httpRequest != null ? httpRequest.getSession(false) : null;
        if (session != null) {
            Object preAuthId = session.getAttribute(AuthenticationStageValidationFilter.FINSIGHT_PRE_AUTH_USER_ID);
            if (preAuthId instanceof UUID u) {
                return userRepository.findById(u)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + u));
            }
        }

        // Fall back to authenticated context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            if (auth.getPrincipal() instanceof AppUserDetails userDetails) {
                return userRepository.findById(userDetails.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getId()));
            } else if (auth.getName() != null) {
                return userRepository.findByEmail(auth.getName())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + auth.getName()));
            }
        }

        throw new AccessDeniedException("User must be authenticated or in PASSWORD_CHANGE_REQUIRED stage");
    }

    public void incrementFailedPreAuthChallenge(HttpSession session, UUID userId, HttpServletRequest request) {
        if (session == null) return;
        int failed = 1;
        Object failedObj = session.getAttribute(AuthenticationStageValidationFilter.FINSIGHT_FAILED_CHALLENGES);
        if (failedObj instanceof Integer count) {
            failed = count + 1;
        }
        session.setAttribute(AuthenticationStageValidationFilter.FINSIGHT_FAILED_CHALLENGES, failed);

        if (failed >= MAX_PRE_AUTH_ATTEMPTS) {
            if (auditService != null) {
                auditService.recordSecurityEvent(
                        AuditEventType.AUTH_LOGIN_FAILURE,
                        userId,
                        null,
                        null,
                        "PRE_AUTH_SESSION",
                        userId != null ? userId.toString() : "UNKNOWN",
                        AuditOutcome.FAILURE,
                        request,
                        Map.of("reason", "max_challenge_attempts_exceeded")
                );
            }
            session.invalidate();
            SecurityContextHolder.clearContext();
            throw new BadCredentialsException("Too many failed challenge attempts. Pre-authentication session invalidated.");
        }
    }

    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        UUID actorId = null;
        String actorRole = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AppUserDetails userDetails) {
            actorId = userDetails.getId();
            actorRole = userDetails.getPlatformRole();
        }

        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.removeAttribute(ActiveBusinessContext.ACTIVE_BUSINESS_SESSION_ATTR);
            session.invalidate();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        if (actorId != null && auditService != null) {
            auditService.recordEvent(
                    AuditEventType.AUTH_LOGOUT,
                    actorId,
                    actorRole,
                    null,
                    "USER",
                    actorId.toString(),
                    AuditOutcome.SUCCESS,
                    httpRequest,
                    Map.of()
            );
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(HttpServletRequest httpRequest) {
        if (httpRequest != null) {
            HttpSession session = httpRequest.getSession(false);
            if (session != null) {
                Object stageObj = session.getAttribute(AuthenticationStageValidationFilter.FINSIGHT_AUTH_STAGE);
                Object preAuthId = session.getAttribute(AuthenticationStageValidationFilter.FINSIGHT_PRE_AUTH_USER_ID);
                if (stageObj != null && preAuthId instanceof UUID u) {
                    AppUser user = userRepository.findById(u)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + u));
                    return UserResponse.fromEntity(user, stageObj.toString());
                }
            }
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("User is not authenticated");
        }

        if (auth.getPrincipal() instanceof AppUserDetails userDetails) {
            AppUser user = userRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getId()));
            return UserResponse.fromEntity(user, "FULLY_AUTHENTICATED");
        } else if (auth.getName() != null) {
            AppUser user = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + auth.getName()));
            return UserResponse.fromEntity(user, "FULLY_AUTHENTICATED");
        }

        throw new AccessDeniedException("Invalid authentication principal");
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        return getCurrentUser(null);
    }
}
