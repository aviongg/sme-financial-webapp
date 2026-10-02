package com.app.sme_health_backend.security;

import com.app.sme_health_backend.identity.controller.AuthController;
import com.app.sme_health_backend.identity.dto.LoginRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.identity.service.ActiveBusinessContext;
import com.app.sme_health_backend.identity.service.AuthenticationService;
import com.app.sme_health_backend.mfa.dto.MfaChallengeRequest;
import com.app.sme_health_backend.mfa.dto.MfaRecoveryRequest;
import com.app.sme_health_backend.mfa.entity.UserMfa;
import com.app.sme_health_backend.mfa.repository.UserMfaRepository;
import com.app.sme_health_backend.mfa.service.MfaService;
import com.app.sme_health_backend.security.filter.AuthenticationStageValidationFilter;
import com.app.sme_health_backend.security.service.AppUserDetails;
import com.app.sme_health_backend.security.service.PreAuthenticationInvalidException;
import com.app.sme_health_backend.security.service.PreAuthenticationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.app.sme_health_backend.security.filter.AuthenticationStageValidationFilter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PreAuthenticationSecurityTest {
    private AppUserRepository users;
    private UserMfaRepository factors;
    private MfaService mfa;
    private AuthenticationManager manager;
    private BusinessMembershipRepository memberships;
    private BusinessRepository businesses;
    private PreAuthenticationService preAuth;
    private AuthenticationService authentication;
    private AuthController controller;
    private AppUser user;
    private UserMfa factor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        users = mock(AppUserRepository.class);
        factors = mock(UserMfaRepository.class);
        mfa = mock(MfaService.class);
        manager = mock(AuthenticationManager.class);
        memberships = mock(BusinessMembershipRepository.class);
        businesses = mock(BusinessRepository.class);
        preAuth = new PreAuthenticationService(users, factors);
        authentication = new AuthenticationService(users, mock(PasswordEncoder.class), manager,
                new ChangeSessionIdAuthenticationStrategy(), memberships, businesses, preAuth, null, null);
        controller = new AuthController(authentication, null, mfa, users, null);
        user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail("challenge@example.test");
        user.setAuthVersion(7L);
        factor = new UserMfa(user.getId(), "encrypted-test-fixture", "ENABLED");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(factors.findByUserId(user.getId())).thenReturn(Optional.of(factor));
        when(manager.authenticate(any())).thenAnswer(invocation -> {
            AppUserDetails details = new AppUserDetails(user);
            return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        });
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    private MockHttpSession login() {
        assertEquals("MFA_CHALLENGE_REQUIRED", authentication.login(
                new LoginRequest(user.getEmail(), "test-password-123"), request, response).authStage());
        return (MockHttpSession) request.getSession(false);
    }

    @Test
    void passwordAcceptedSessionIsVersionBoundIndexedAndNotAuthenticated() {
        request.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.createEmptyContext());
        MockHttpSession session = login();
        assertEquals(7L, session.getAttribute(FINSIGHT_PRE_AUTH_VERSION));
        assertEquals(user.getEmail(), session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME));
        assertNull(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "must-change", "disabled", "factor", "expired", "stage", "missing-version", "missing-expiry", "missing-user"})
    void staleChallengeIsRejectedBeforeTheFactorIsConsumed(String change) {
        MockHttpSession session = login();
        mutate(change, session);
        assertThrows(PreAuthenticationInvalidException.class, () -> controller.verifyMfaChallenge(
                new MfaChallengeRequest("123456"), request, response));
        assertTrue(session.isInvalid());
        verifyNoInteractions(mfa);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "must-change", "disabled", "factor", "expired", "stage"})
    void securityChangesDuringTotpVerificationAreRecheckedBeforePromotion(String change) {
        MockHttpSession session = login();
        when(mfa.verifyTotpChallenge(eq(user.getId()), anyString(), eq(request))).thenAnswer(invocation -> {
            mutate(change, session);
            return true;
        });
        assertThrows(PreAuthenticationInvalidException.class, () -> controller.verifyMfaChallenge(
                new MfaChallengeRequest("123456"), request, response));
        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(memberships);
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "must-change", "disabled", "factor", "expired", "stage"})
    void securityChangesDuringRecoveryVerificationAreRecheckedBeforePromotion(String change) {
        MockHttpSession session = login();
        when(mfa.verifyRecoveryCode(eq(user.getId()), anyString(), eq(request))).thenAnswer(invocation -> {
            mutate(change, session);
            return true;
        });
        assertThrows(PreAuthenticationInvalidException.class, () -> controller.verifyMfaRecovery(
                new MfaRecoveryRequest("recovery-fixture"), request, response));
        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void disabledPendingRecoveryIsRejectedBeforeConsumingCode() {
        MockHttpSession session = login();
        user.setAccountStatus(AccountStatus.DISABLED);
        assertThrows(PreAuthenticationInvalidException.class, () -> controller.verifyMfaRecovery(
                new MfaRecoveryRequest("recovery-fixture"), request, response));
        assertTrue(session.isInvalid());
        verifyNoInteractions(mfa);
    }

    @ParameterizedTest
    @ValueSource(strings = {"totp", "recovery"})
    void successfulChallengeRotatesSessionClearsTemporaryStateAndSelectsBusiness(String method) {
        MockHttpSession session = login();
        String previousId = session.getId();
        UUID businessId = UUID.randomUUID();
        when(memberships.findByUserId(user.getId())).thenReturn(List.of(
                new BusinessMembership(user.getId(), businessId, MembershipRole.OWNER, MembershipStatus.ACTIVE)));
        when(businesses.findById(businessId)).thenReturn(Optional.of(new Business(businessId, "ACTIVE")));
        if ("totp".equals(method)) {
            when(mfa.verifyTotpChallenge(eq(user.getId()), anyString(), eq(request))).thenReturn(true);
            assertEquals("FULLY_AUTHENTICATED", controller.verifyMfaChallenge(
                    new MfaChallengeRequest("123456"), request, response).getBody().authStage());
        } else {
            when(mfa.verifyRecoveryCode(eq(user.getId()), anyString(), eq(request))).thenReturn(true);
            assertEquals("FULLY_AUTHENTICATED", controller.verifyMfaRecovery(
                    new MfaRecoveryRequest("recovery-fixture"), request, response).getBody().authStage());
        }
        assertNotEquals(previousId, session.getId());
        assertEquals(7L, session.getAttribute(FINSIGHT_AUTH_VERSION));
        assertNull(session.getAttribute(FINSIGHT_PRE_AUTH_VERSION));
        assertNull(session.getAttribute(FINSIGHT_PRE_AUTH_USER_ID));
        assertNull(session.getAttribute(FINSIGHT_AUTH_STAGE));
        assertEquals(businessId, session.getAttribute(ActiveBusinessContext.ACTIVE_BUSINESS_SESSION_ATTR));
        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
    }

    @Test
    void currentSecurityStateDeterminesFreshLoginStageInPriorityOrder() {
        user.setMustChangePassword(true);
        user.setPlatformRole("PLATFORM_ADMIN");
        assertEquals("PASSWORD_CHANGE_REQUIRED", authentication.login(
                new LoginRequest(user.getEmail(), "test-password-123"), request, response).authStage());
        user.setMustChangePassword(false);
        factor.setStatus("PENDING");
        assertEquals("MFA_ENROLLMENT_REQUIRED", authentication.login(
                new LoginRequest(user.getEmail(), "test-password-123"), request, response).authStage());
        factor.setStatus("ENABLED");
        login();
        user.setPlatformRole(null);
        factor.setStatus("PENDING");
        assertEquals("FULLY_AUTHENTICATED", authentication.login(
                new LoginRequest(user.getEmail(), "test-password-123"), request, response).authStage());
    }

    @Test
    void resetBetweenPasswordVerificationAndUserReloadCannotBindTheNewVersion() {
        AppUserDetails accepted = new AppUserDetails(user);
        when(manager.authenticate(any())).thenAnswer(invocation -> {
            user.setAuthVersion(8L);
            return new UsernamePasswordAuthenticationToken(accepted, null, accepted.getAuthorities());
        });
        assertThrows(PreAuthenticationInvalidException.class, () -> authentication.login(
                new LoginRequest(user.getEmail(), "test-password-123"), request, response));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void requestFilterRevalidatesPendingStateEvenForAllowedEndpoints() throws Exception {
        MockHttpSession session = login();
        user.setAuthVersion(8L);
        request.setRequestURI("/api/auth/mfa/challenge");
        var chain = mock(jakarta.servlet.FilterChain.class);
        new AuthenticationStageValidationFilter(preAuth).doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("pre_auth_invalid"));
        assertTrue(session.isInvalid());
        verifyNoInteractions(chain);
    }

    private void mutate(String change, MockHttpSession session) {
        switch (change) {
            case "version" -> user.setAuthVersion(8L);
            case "must-change" -> user.setMustChangePassword(true);
            case "disabled" -> user.setAccountStatus(AccountStatus.DISABLED);
            case "factor" -> factor.setStatus("PENDING");
            case "expired" -> session.setAttribute(FINSIGHT_PRE_AUTH_EXPIRES_AT, System.currentTimeMillis() - 300_001L);
            case "stage" -> session.setAttribute(FINSIGHT_AUTH_STAGE, "PASSWORD_CHANGE_REQUIRED");
            case "missing-version" -> session.removeAttribute(FINSIGHT_PRE_AUTH_VERSION);
            case "missing-expiry" -> session.removeAttribute(FINSIGHT_PRE_AUTH_EXPIRES_AT);
            case "missing-user" -> when(users.findById(user.getId())).thenReturn(Optional.empty());
            default -> fail("Unknown fixture");
        }
    }
}
