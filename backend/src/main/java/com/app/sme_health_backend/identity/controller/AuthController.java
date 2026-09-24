package com.app.sme_health_backend.identity.controller;

import com.app.sme_health_backend.identity.credential.dto.ChangePasswordRequest;
import com.app.sme_health_backend.identity.credential.dto.PasswordResetConfirmRequest;
import com.app.sme_health_backend.identity.credential.dto.PasswordResetRequest;
import com.app.sme_health_backend.identity.credential.service.PasswordResetService;
import com.app.sme_health_backend.identity.dto.CsrfResponse;
import com.app.sme_health_backend.identity.dto.LoginRequest;
import com.app.sme_health_backend.identity.dto.RegisterRequest;
import com.app.sme_health_backend.identity.dto.UserResponse;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.AuthenticationService;
import com.app.sme_health_backend.mfa.dto.*;
import com.app.sme_health_backend.mfa.service.MfaService;
import com.app.sme_health_backend.security.filter.AuthenticationStageValidationFilter;
import com.app.sme_health_backend.security.service.AppUserDetails;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;
    private final MfaService mfaService;
    private final AppUserRepository userRepository;

    public AuthController(
            AuthenticationService authenticationService,
            PasswordResetService passwordResetService,
            MfaService mfaService,
            AppUserRepository userRepository
    ) {
        this.authenticationService = authenticationService;
        this.passwordResetService = passwordResetService;
        this.mfaService = mfaService;
        this.userRepository = userRepository;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> getCsrfToken(HttpServletRequest request) {
        request.getSession(true);
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute("_csrf");
        }
        if (csrfToken != null) {
            return ResponseEntity.ok(new CsrfResponse(
                    csrfToken.getToken(),
                    csrfToken.getHeaderName(),
                    csrfToken.getParameterName()
            ));
        }
        return ResponseEntity.ok(new CsrfResponse("FINSIGHT-CSRF-TOKEN", "X-XSRF-TOKEN", "_csrf"));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        UserResponse response = authenticationService.login(request, httpRequest, httpResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authenticationService.logout(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(HttpServletRequest httpRequest) {
        UserResponse response = authenticationService.getCurrentUser(httpRequest);
        if (response == null) {
            response = authenticationService.getCurrentUser();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        authenticationService.changePassword(request, httpRequest);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully. Please log in with your new password."));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, String>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest
    ) {
        String msg = passwordResetService.requestPasswordReset(request.email(), httpRequest);
        return ResponseEntity.ok(Map.of("message", msg));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Map<String, String>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request,
            HttpServletRequest httpRequest
    ) {
        passwordResetService.confirmPasswordReset(request, httpRequest);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully. Please log in with your new password."));
    }

    @PostMapping("/mfa/enroll/initiate")
    public ResponseEntity<MfaInitiateResponse> initiateMfaEnrollment(HttpServletRequest httpRequest) {
        UUID userId = resolvePreAuthOrAuthenticatedUserId(httpRequest);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        MfaInitiateResponse response = mfaService.initiateEnrollment(userId, user.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/enroll/confirm")
    public ResponseEntity<Map<String, Object>> confirmMfaEnrollment(
            @Valid @RequestBody MfaConfirmRequest request,
            HttpServletRequest httpRequest
    ) {
        UUID userId = resolvePreAuthOrAuthenticatedUserId(httpRequest);
        List<String> recoveryCodes = mfaService.confirmEnrollment(userId, request.code(), request.password(), httpRequest);
        return ResponseEntity.ok(Map.of(
                "message", "MFA successfully enabled",
                "recoveryCodes", recoveryCodes
        ));
    }

    @PostMapping("/mfa/challenge")
    public ResponseEntity<UserResponse> verifyMfaChallenge(
            @Valid @RequestBody MfaChallengeRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        UUID preAuthUserId = resolvePreAuthUserId(httpRequest);
        boolean valid = mfaService.verifyTotpChallenge(preAuthUserId, request.code(), httpRequest);

        if (!valid) {
            authenticationService.incrementFailedPreAuthChallenge(httpRequest.getSession(false), preAuthUserId, httpRequest);
            throw new BadCredentialsException("Invalid MFA verification code");
        }

        AppUser user = userRepository.findById(preAuthUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + preAuthUserId));

        UserResponse userResponse = authenticationService.establishFullAuthentication(user, httpRequest, httpResponse);
        return ResponseEntity.ok(userResponse);
    }

    @PostMapping("/mfa/recovery")
    public ResponseEntity<UserResponse> verifyMfaRecovery(
            @Valid @RequestBody MfaRecoveryRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        UUID preAuthUserId = resolvePreAuthUserId(httpRequest);
        boolean valid = mfaService.verifyRecoveryCode(preAuthUserId, request.recoveryCode(), httpRequest);

        if (!valid) {
            authenticationService.incrementFailedPreAuthChallenge(httpRequest.getSession(false), preAuthUserId, httpRequest);
            throw new BadCredentialsException("Invalid recovery code");
        }

        AppUser user = userRepository.findById(preAuthUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + preAuthUserId));

        UserResponse userResponse = authenticationService.establishFullAuthentication(user, httpRequest, httpResponse);
        return ResponseEntity.ok(userResponse);
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<Map<String, String>> disableMfa(
            @Valid @RequestBody MfaDisableRequest request,
            HttpServletRequest httpRequest
    ) {
        UUID userId = resolveAuthenticatedUserId();
        mfaService.disableMfa(userId, request.password(), request.verificationCode(), httpRequest);
        return ResponseEntity.ok(Map.of("message", "MFA disabled successfully"));
    }

    private UUID resolvePreAuthUserId(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            Object preAuthId = session.getAttribute(AuthenticationStageValidationFilter.FINSIGHT_PRE_AUTH_USER_ID);
            if (preAuthId instanceof UUID u) {
                return u;
            }
        }
        throw new BadCredentialsException("No active pre-authentication challenge found");
    }

    private UUID resolvePreAuthOrAuthenticatedUserId(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            Object preAuthId = session.getAttribute(AuthenticationStageValidationFilter.FINSIGHT_PRE_AUTH_USER_ID);
            if (preAuthId instanceof UUID u) {
                return u;
            }
        }
        return resolveAuthenticatedUserId();
    }

    private UUID resolveAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("User is not authenticated");
        }
        if (auth.getPrincipal() instanceof AppUserDetails userDetails) {
            return userDetails.getId();
        } else if (auth.getName() != null) {
            return userRepository.findByEmail(auth.getName())
                    .map(AppUser::getId)
                    .orElseThrow(() -> new AccessDeniedException("User not found"));
        }
        throw new AccessDeniedException("Invalid authentication principal");
    }
}
