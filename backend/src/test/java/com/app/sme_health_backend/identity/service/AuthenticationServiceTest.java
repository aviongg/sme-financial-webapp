package com.app.sme_health_backend.identity.service;

import com.app.sme_health_backend.identity.dto.LoginRequest;
import com.app.sme_health_backend.identity.dto.RegisterRequest;
import com.app.sme_health_backend.identity.dto.UserResponse;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.security.service.AppUserDetails;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private HttpServletResponse httpResponse;

    @Mock
    private HttpSession httpSession;

    @Mock
    private org.springframework.security.web.authentication.session.SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @Mock
    private BusinessMembershipRepository membershipRepository;

    @Mock
    private BusinessRepository businessRepository;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userRepository,
                passwordEncoder,
                authenticationManager,
                sessionAuthenticationStrategy,
                membershipRepository,
                businessRepository
        );
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Register valid user succeeds, hashes password, canonicalizes email, and returns UserResponse")
    void testRegisterValidUser() {
        RegisterRequest request = new RegisterRequest("  User.Test@Example.COM  ", "valid-secure-password-123", "Test User");
        when(userRepository.existsByEmail("user.test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("valid-secure-password-123")).thenReturn("$argon2id$mockhash");

        AppUser savedUser = new AppUser();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail("user.test@example.com");
        savedUser.setPasswordHash("$argon2id$mockhash");
        savedUser.setFullName("Test User");
        savedUser.setAccountStatus(AccountStatus.ACTIVE);
        savedUser.setCreatedAt(OffsetDateTime.now());
        when(userRepository.save(any(AppUser.class))).thenReturn(savedUser);

        UserResponse response = authenticationService.register(request);

        assertNotNull(response);
        assertEquals(savedUser.getId(), response.id());
        assertEquals("user.test@example.com", response.email());
        assertEquals("Test User", response.fullName());
        assertEquals(AccountStatus.ACTIVE, response.accountStatus());

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        AppUser persisted = captor.getValue();
        assertEquals("user.test@example.com", persisted.getEmail(), "Email must be trimmed and lowercased");
        assertEquals("$argon2id$mockhash", persisted.getPasswordHash(), "Stored password must be encoded");
    }

    @Test
    @DisplayName("Register rejects duplicate email deterministically")
    void testRegisterDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("existing@example.com", "valid-secure-password-123", "Existing User");
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authenticationService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Register rejects weak/short password (<12 characters)")
    void testRegisterShortPassword() {
        RegisterRequest request = new RegisterRequest("user@example.com", "short-pass", "User");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authenticationService.register(request));
        assertTrue(ex.getMessage().contains("at least 12 characters"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Register rejects excessively long password (>128 characters)")
    void testRegisterExcessivePassword() {
        String longPass = "a".repeat(129);
        RegisterRequest request = new RegisterRequest("user@example.com", longPass, "User");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authenticationService.register(request));
        assertTrue(ex.getMessage().contains("must not exceed 128 characters"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Register preserves password content exactly without trimming or normalizing")
    void testRegisterPasswordNotTrimmed() {
        String passWithSpaces = "  password-with-exact-spaces-123  ";
        RegisterRequest request = new RegisterRequest("user@example.com", passWithSpaces, "User");
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode(passWithSpaces)).thenReturn("$argon2id$exact");

        AppUser savedUser = new AppUser();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail("user@example.com");
        savedUser.setPasswordHash("$argon2id$exact");
        savedUser.setFullName("User");
        when(userRepository.save(any(AppUser.class))).thenReturn(savedUser);

        authenticationService.register(request);

        verify(passwordEncoder).encode(passWithSpaces);
    }

    @Test
    @DisplayName("Login with valid credentials authenticates, sets session, and returns UserResponse")
    void testLoginSuccess() {
        LoginRequest request = new LoginRequest("User@Example.com", "valid-password-123");
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPasswordHash("$argon2id$hash");
        user.setFullName("Login User");
        user.setAccountStatus(AccountStatus.ACTIVE);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(httpRequest.getSession(org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(httpSession);

        UserResponse response = authenticationService.login(request, httpRequest, httpResponse);

        assertNotNull(response);
        assertEquals(user.getId(), response.id());
        assertEquals("user@example.com", response.email());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(sessionAuthenticationStrategy).onAuthentication(any(), eq(httpRequest), eq(httpResponse));
        verify(httpSession).setAttribute(eq("FINSIGHT_AUTH_TIME"), anyLong());
    }

    @Test
    @DisplayName("Login with wrong password fails with BadCredentialsException")
    void testLoginWrongPassword() {
        LoginRequest request = new LoginRequest("user@example.com", "wrong-password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authenticationService.login(request, httpRequest, httpResponse));
        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    @DisplayName("Login with unknown user fails with identical generic BadCredentialsException")
    void testLoginUnknownUser() {
        LoginRequest request = new LoginRequest("unknown@example.com", "password-12345");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authenticationService.login(request, httpRequest, httpResponse));
        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    @DisplayName("Login with disabled account fails with DisabledException")
    void testLoginDisabledAccount() {
        LoginRequest request = new LoginRequest("disabled@example.com", "valid-password-123");
        AppUser user = new AppUser();
        user.setEmail("disabled@example.com");
        user.setAccountStatus(AccountStatus.DISABLED);

        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(userRepository.findByEmail("disabled@example.com")).thenReturn(Optional.of(user));

        assertThrows(DisabledException.class, () -> authenticationService.login(request, httpRequest, httpResponse));
    }

    @Test
    @DisplayName("Logout invalidates session and clears SecurityContext")
    void testLogout() {
        when(httpRequest.getSession(false)).thenReturn(httpSession);

        authenticationService.logout(httpRequest, httpResponse);

        verify(httpSession).invalidate();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("getCurrentUser returns authenticated user details")
    void testGetCurrentUserAuthenticated() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        user.setEmail("me@example.com");
        user.setFullName("Me User");
        user.setAccountStatus(AccountStatus.ACTIVE);

        AppUserDetails userDetails = new AppUserDetails(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse response = authenticationService.getCurrentUser();
        assertNotNull(response);
        assertEquals(userId, response.id());
        assertEquals("me@example.com", response.email());
    }

    @Test
    @DisplayName("getCurrentUser throws AccessDeniedException when unauthenticated")
    void testGetCurrentUserUnauthenticated() {
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> authenticationService.getCurrentUser());
    }

    @Test
    @DisplayName("Login with zero memberships clears active business and leaves it unset")
    void testLoginWithZeroMembershipsLeavesActiveBusinessUnset() {
        LoginRequest request = new LoginRequest("user@example.com", "valid-password-123");
        AppUser user = new AppUser();
        UUID userId = UUID.randomUUID();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setPasswordHash("$argon2id$hash");
        user.setAccountStatus(AccountStatus.ACTIVE);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(httpRequest.getSession(anyBoolean())).thenReturn(httpSession);
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of());

        authenticationService.login(request, httpRequest, httpResponse);

        verify(httpSession).removeAttribute(ActiveBusinessContext.ACTIVE_BUSINESS_SESSION_ATTR);
        verify(httpSession, never()).setAttribute(eq(ActiveBusinessContext.ACTIVE_BUSINESS_SESSION_ATTR), any());
    }

    @Test
    @DisplayName("Login with exactly one active membership in an active business auto-selects active business")
    void testLoginWithExactlyOneActiveMembershipAutoSelects() {
        LoginRequest request = new LoginRequest("user@example.com", "valid-password-123");
        AppUser user = new AppUser();
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setPasswordHash("$argon2id$hash");
        user.setAccountStatus(AccountStatus.ACTIVE);

        BusinessMembership membership = new BusinessMembership(userId, businessId, MembershipRole.OWNER, MembershipStatus.ACTIVE);
        Business business = new Business(businessId, "ACTIVE");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(httpRequest.getSession(anyBoolean())).thenReturn(httpSession);
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(membership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

        authenticationService.login(request, httpRequest, httpResponse);

        verify(httpSession).removeAttribute(ActiveBusinessContext.ACTIVE_BUSINESS_SESSION_ATTR);
        verify(httpSession).setAttribute(ActiveBusinessContext.ACTIVE_BUSINESS_SESSION_ATTR, businessId);
    }

    @Test
    @DisplayName("Login with multiple active memberships leaves active business unset")
    void testLoginWithMultipleActiveMembershipsLeavesActiveBusinessUnset() {
        LoginRequest request = new LoginRequest("user@example.com", "valid-password-123");
        AppUser user = new AppUser();
        UUID userId = UUID.randomUUID();
        UUID businessId1 = UUID.randomUUID();
        UUID businessId2 = UUID.randomUUID();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setPasswordHash("$argon2id$hash");
        user.setAccountStatus(AccountStatus.ACTIVE);

        BusinessMembership m1 = new BusinessMembership(userId, businessId1, MembershipRole.OWNER, MembershipStatus.ACTIVE);
        BusinessMembership m2 = new BusinessMembership(userId, businessId2, MembershipRole.ACCOUNTANT, MembershipStatus.ACTIVE);
        Business b1 = new Business(businessId1, "ACTIVE");
        Business b2 = new Business(businessId2, "ACTIVE");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(httpRequest.getSession(anyBoolean())).thenReturn(httpSession);
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(m1, m2));
        when(businessRepository.findById(businessId1)).thenReturn(Optional.of(b1));
        when(businessRepository.findById(businessId2)).thenReturn(Optional.of(b2));

        authenticationService.login(request, httpRequest, httpResponse);

        verify(httpSession).removeAttribute(ActiveBusinessContext.ACTIVE_BUSINESS_SESSION_ATTR);
        verify(httpSession, never()).setAttribute(eq(ActiveBusinessContext.ACTIVE_BUSINESS_SESSION_ATTR), any());
    }
}
