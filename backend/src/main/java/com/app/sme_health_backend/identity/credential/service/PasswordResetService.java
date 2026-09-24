package com.app.sme_health_backend.identity.credential.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.identity.credential.dto.PasswordResetConfirmRequest;
import com.app.sme_health_backend.identity.credential.entity.PasswordResetToken;
import com.app.sme_health_backend.identity.credential.notification.PasswordResetNotifier;
import com.app.sme_health_backend.identity.credential.repository.PasswordResetTokenRepository;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.validation.EmailValidator;
import com.app.sme_health_backend.identity.validation.PasswordValidator;
import com.app.sme_health_backend.security.service.SessionRevocationService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Service
public class PasswordResetService {

    public static final String GENERIC_RESET_RESPONSE =
            "If an account exists with this email, a password reset link has been dispatched.";
    private static final int TOKEN_BYTES = 32;
    private static final int EXPIRY_MINUTES = 15;

    private final AppUserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetNotifier notifier;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevocationService sessionRevocationService;
    private final SecurityAuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            AppUserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordResetNotifier notifier,
            PasswordEncoder passwordEncoder,
            SessionRevocationService sessionRevocationService,
            SecurityAuditService auditService
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.notifier = notifier;
        this.passwordEncoder = passwordEncoder;
        this.sessionRevocationService = sessionRevocationService;
        this.auditService = auditService;
    }

    @Transactional
    public String requestPasswordReset(String email, HttpServletRequest httpRequest) {
        if (email == null || email.isBlank()) {
            return GENERIC_RESET_RESPONSE;
        }

        String normalizedEmail;
        try {
            normalizedEmail = EmailValidator.normalizeAndValidate(email);
        } catch (IllegalArgumentException e) {
            return GENERIC_RESET_RESPONSE;
        }

        Optional<AppUser> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            if (user.getAccountStatus() == AccountStatus.ACTIVE) {
                OffsetDateTime now = OffsetDateTime.now();
                // Revoke prior active reset tokens
                tokenRepository.revokeAllActiveForUser(user.getId(), now);

                // Generate 32 bytes cryptographically secure random entropy
                byte[] randomBytes = new byte[TOKEN_BYTES];
                secureRandom.nextBytes(randomBytes);
                String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

                String tokenHash = sha256Hex(rawToken);
                OffsetDateTime expiresAt = now.plusMinutes(EXPIRY_MINUTES);

                PasswordResetToken token = new PasswordResetToken(user.getId(), tokenHash, expiresAt);
                tokenRepository.save(token);

                auditService.recordEvent(
                        AuditEventType.PASSWORD_RESET_REQUESTED,
                        user.getId(),
                        user.getPlatformRole(),
                        null,
                        "USER",
                        user.getId().toString(),
                        AuditOutcome.SUCCESS,
                        httpRequest,
                        Map.of("expires_at", expiresAt.toString())
                );

                notifier.sendPasswordResetNotification(user.getEmail(), rawToken, expiresAt);
            }
        }

        return GENERIC_RESET_RESPONSE;
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request, HttpServletRequest httpRequest) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            throw new BadCredentialsException("Reset token is required");
        }
        PasswordValidator.validate(request.newPassword());

        String tokenHash = sha256Hex(request.token().trim());
        OffsetDateTime now = OffsetDateTime.now();

        // Atomically claim token (prevents concurrent reuse)
        int updated = tokenRepository.claimToken(tokenHash, now);
        if (updated == 0) {
            auditService.recordSecurityEvent(
                    AuditEventType.PASSWORD_RESET_COMPLETED,
                    null,
                    null,
                    null,
                    "PASSWORD_RESET_TOKEN",
                    "CLAIM_FAILED",
                    AuditOutcome.FAILURE,
                    httpRequest,
                    Map.of("reason", "invalid_or_expired_token")
            );
            throw new IllegalArgumentException("Invalid or expired password reset token");
        }

        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset token"));

        AppUser user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + token.getUserId()));

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            auditService.recordSecurityEvent(
                    AuditEventType.PASSWORD_RESET_COMPLETED,
                    user.getId(),
                    user.getPlatformRole(),
                    null,
                    "USER",
                    user.getId().toString(),
                    AuditOutcome.FAILURE,
                    httpRequest,
                    Map.of("reason", "account_disabled")
            );
            throw new DisabledException("Account is disabled");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must not be the same as the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setAuthVersion(user.getAuthVersion() + 1);
        userRepository.save(user);

        // Invalidate all other reset tokens for this user
        tokenRepository.revokeAllActiveForUser(user.getId(), now);

        auditService.recordEvent(
                AuditEventType.PASSWORD_CHANGED,
                user.getId(),
                user.getPlatformRole(),
                null,
                "USER",
                user.getId().toString(),
                AuditOutcome.SUCCESS,
                httpRequest,
                Map.of("reason", "password_reset")
        );

        auditService.recordEvent(
                AuditEventType.PASSWORD_RESET_COMPLETED,
                user.getId(),
                user.getPlatformRole(),
                null,
                "USER",
                user.getId().toString(),
                AuditOutcome.SUCCESS,
                httpRequest,
                Map.of("auth_version", user.getAuthVersion())
        );

        // Immediately revoke all sessions across the cluster
        sessionRevocationService.revokeAllSessions(user.getEmail(), user.getId(), "PASSWORD_RESET");
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm not available", e);
        }
    }
}
