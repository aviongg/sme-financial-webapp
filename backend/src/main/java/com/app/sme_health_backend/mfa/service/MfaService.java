package com.app.sme_health_backend.mfa.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.crypto.SensitiveDataCipher;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.mfa.dto.MfaInitiateResponse;
import com.app.sme_health_backend.mfa.entity.UserMfa;
import com.app.sme_health_backend.mfa.entity.UserMfaRecoveryCode;
import com.app.sme_health_backend.mfa.repository.UserMfaRecoveryCodeRepository;
import com.app.sme_health_backend.mfa.repository.UserMfaRepository;
import com.app.sme_health_backend.security.service.SessionRevocationService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    public static final String AAD_PREFIX = "FinSight|UserMfa|totpSecret|";
    private static final String RECOVERY_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int PENDING_TTL_MINUTES = 15;

    private final UserMfaRepository userMfaRepository;
    private final UserMfaRecoveryCodeRepository recoveryCodeRepository;
    private final AppUserRepository userRepository;
    private final TotpEngine totpEngine;
    private final SensitiveDataCipher cipher;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevocationService sessionRevocationService;
    private final SecurityAuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public MfaService(
            UserMfaRepository userMfaRepository,
            UserMfaRecoveryCodeRepository recoveryCodeRepository,
            AppUserRepository userRepository,
            TotpEngine totpEngine,
            SensitiveDataCipher cipher,
            PasswordEncoder passwordEncoder,
            SessionRevocationService sessionRevocationService,
            SecurityAuditService auditService
    ) {
        this.userMfaRepository = userMfaRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.userRepository = userRepository;
        this.totpEngine = totpEngine;
        this.cipher = cipher;
        this.passwordEncoder = passwordEncoder;
        this.sessionRevocationService = sessionRevocationService;
        this.auditService = auditService;
    }

    public static String getAad(UUID userId) {
        return AAD_PREFIX + userId.toString();
    }

    @Transactional(readOnly = true)
    public boolean isMfaEnabled(UUID userId) {
        if (userId == null) return false;
        return userMfaRepository.findByUserId(userId)
                .map(m -> "ENABLED".equals(m.getStatus()))
                .orElse(false);
    }

    @Transactional
    public MfaInitiateResponse initiateEnrollment(UUID userId) {
        String email = userRepository.findById(userId).map(AppUser::getEmail).orElse("user@finsight.internal");
        return initiateEnrollment(userId, email);
    }

    @Transactional
    public MfaInitiateResponse initiateEnrollment(UUID userId, String email) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(email, "email is required");

        String secret = totpEngine.generateSecret();
        String encryptedSecret = cipher.encrypt(secret, getAad(userId));

        UserMfa userMfa = userMfaRepository.findByUserId(userId).orElseGet(() -> {
            UserMfa m = new UserMfa();
            m.setUserId(userId);
            return m;
        });

        userMfa.setTotpSecret(encryptedSecret);
        userMfa.setStatus("PENDING");
        userMfa.setCreatedAt(OffsetDateTime.now());
        userMfa.setVerifiedAt(null);
        userMfa.setEnabledAt(null);
        userMfa.setLastUsedTimeStep(null);
        userMfaRepository.save(userMfa);

        String provisioningUri = totpEngine.buildProvisioningUri(email, secret);
        return new MfaInitiateResponse(secret, provisioningUri);
    }

    @Transactional
    public List<String> confirmEnrollment(UUID userId, String password, String code) {
        return confirmEnrollment(userId, code, password, null);
    }

    @Transactional
    public boolean verifyTotp(UUID userId, String candidateCode) {
        return verifyTotpChallenge(userId, candidateCode, null);
    }

    @Transactional
    public boolean verifyRecoveryCode(UUID userId, String recoveryCode) {
        return verifyRecoveryCode(userId, recoveryCode, null);
    }

    @Transactional
    public void disableMfa(UUID userId) {
        userMfaRepository.deleteByUserId(userId);
        recoveryCodeRepository.deleteByUserId(userId);
        userRepository.findById(userId).ifPresent(u -> {
            u.setAuthVersion(u.getAuthVersion() + 1);
            userRepository.save(u);
            if (sessionRevocationService != null) {
                sessionRevocationService.revokeAllSessions(u.getEmail(), userId, "MFA_DISABLED");
            }
        });
    }

    @Transactional
    public List<String> confirmEnrollment(UUID userId, String code, String password, HttpServletRequest request) {
        Objects.requireNonNull(userId, "userId is required");

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new DisabledException("Account is disabled");
        }

        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Current password verification failed");
        }

        UserMfa userMfa = userMfaRepository.findByUserId(userId)
                .orElseThrow(() -> new BadCredentialsException("No pending MFA enrollment found"));

        if (!"PENDING".equals(userMfa.getStatus())) {
            throw new BadCredentialsException("MFA enrollment is not in PENDING state");
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(PENDING_TTL_MINUTES);
        if (userMfa.getCreatedAt().isBefore(cutoff)) {
            userMfaRepository.delete(userMfa);
            throw new BadCredentialsException("Pending MFA enrollment has expired. Please initiate setup again.");
        }

        String decryptedSecret = cipher.decrypt(userMfa.getTotpSecret(), getAad(userId));
        OptionalLong matchedStep = totpEngine.validateCode(decryptedSecret, code, Instant.now().getEpochSecond());

        if (matchedStep.isEmpty()) {
            auditService.recordSecurityEvent(
                    AuditEventType.MFA_CHALLENGE_FAILURE,
                    userId,
                    user.getPlatformRole(),
                    null,
                    "MFA",
                    userId.toString(),
                    AuditOutcome.FAILURE,
                    request,
                    Map.of("reason", "invalid_enrollment_code")
            );
            throw new BadCredentialsException("Invalid verification code");
        }

        long acceptedStep = matchedStep.getAsLong();
        userMfa.setStatus("ENABLED");
        userMfa.setVerifiedAt(OffsetDateTime.now());
        userMfa.setEnabledAt(OffsetDateTime.now());
        userMfa.setLastUsedTimeStep(acceptedStep);
        userMfaRepository.save(userMfa);

        // Clear existing recovery codes if any
        recoveryCodeRepository.deleteByUserId(userId);

        // Generate 10 human-safe single-use recovery codes
        List<String> rawRecoveryCodes = generateRecoveryCodes();
        for (String rawCode : rawRecoveryCodes) {
            String codeHash = passwordEncoder.encode(rawCode);
            UserMfaRecoveryCode entity = new UserMfaRecoveryCode(userId, codeHash);
            recoveryCodeRepository.save(entity);
        }

        auditService.recordEvent(
                AuditEventType.MFA_ENABLED,
                userId,
                user.getPlatformRole(),
                null,
                "MFA",
                userId.toString(),
                AuditOutcome.SUCCESS,
                request,
                Map.of("recovery_codes_count", RECOVERY_CODE_COUNT)
        );

        return rawRecoveryCodes;
    }

    @Transactional
    public boolean verifyTotpChallenge(UUID userId, String candidateCode, HttpServletRequest request) {
        Objects.requireNonNull(userId, "userId is required");

        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getAccountStatus() != AccountStatus.ACTIVE) {
            auditService.recordSecurityEvent(
                    AuditEventType.MFA_CHALLENGE_FAILURE,
                    userId,
                    user != null ? user.getPlatformRole() : null,
                    null,
                    "MFA",
                    userId.toString(),
                    AuditOutcome.FAILURE,
                    request,
                    Map.of("reason", "inactive_account")
            );
            return false;
        }

        Optional<UserMfa> mfaOpt = userMfaRepository.findByUserId(userId);
        if (mfaOpt.isEmpty() || !"ENABLED".equals(mfaOpt.get().getStatus())) {
            auditService.recordSecurityEvent(
                    AuditEventType.MFA_CHALLENGE_FAILURE,
                    userId,
                    user.getPlatformRole(),
                    null,
                    "MFA",
                    userId.toString(),
                    AuditOutcome.FAILURE,
                    request,
                    Map.of("reason", "mfa_not_enabled")
            );
            return false;
        }

        UserMfa mfa = mfaOpt.get();
        String decryptedSecret = cipher.decrypt(mfa.getTotpSecret(), getAad(userId));
        OptionalLong matchedStep = totpEngine.validateCode(decryptedSecret, candidateCode, Instant.now().getEpochSecond());

        if (matchedStep.isEmpty()) {
            auditService.recordSecurityEvent(
                    AuditEventType.MFA_CHALLENGE_FAILURE,
                    userId,
                    user.getPlatformRole(),
                    null,
                    "MFA",
                    userId.toString(),
                    AuditOutcome.FAILURE,
                    request,
                    Map.of("reason", "invalid_totp_code")
            );
            return false;
        }

        long candidateStep = matchedStep.getAsLong();
        int claimed = userMfaRepository.claimTimeStep(userId, candidateStep);
        if (claimed == 0) {
            auditService.recordSecurityEvent(
                    AuditEventType.MFA_CHALLENGE_FAILURE,
                    userId,
                    user.getPlatformRole(),
                    null,
                    "MFA",
                    userId.toString(),
                    AuditOutcome.FAILURE,
                    request,
                    Map.of("reason", "replay_detected")
            );
            return false;
        }

        auditService.recordEvent(
                AuditEventType.MFA_CHALLENGE_SUCCESS,
                userId,
                user.getPlatformRole(),
                null,
                "MFA",
                userId.toString(),
                AuditOutcome.SUCCESS,
                request,
                Map.of("time_step", candidateStep)
        );

        return true;
    }

    @Transactional
    public boolean verifyRecoveryCode(UUID userId, String recoveryCode, HttpServletRequest request) {
        Objects.requireNonNull(userId, "userId is required");
        if (recoveryCode == null || recoveryCode.isBlank()) return false;

        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getAccountStatus() != AccountStatus.ACTIVE) {
            return false;
        }

        String cleanCode = recoveryCode.trim().toUpperCase().replace(" ", "").replace("-", "");
        List<UserMfaRecoveryCode> unusedCodes = recoveryCodeRepository.findByUserIdAndUsedAtIsNull(userId);

        for (UserMfaRecoveryCode candidate : unusedCodes) {
            // Test candidate code with Argon2id matches
            if (passwordEncoder.matches(cleanCode, candidate.getCodeHash())) {
                int claimed = recoveryCodeRepository.claimCode(candidate.getId(), OffsetDateTime.now());
                if (claimed == 1) {
                    int remaining = unusedCodes.size() - 1;
                    auditService.recordEvent(
                            AuditEventType.RECOVERY_CODE_USED,
                            userId,
                            user.getPlatformRole(),
                            null,
                            "MFA_RECOVERY_CODE",
                            candidate.getId().toString(),
                            AuditOutcome.SUCCESS,
                            request,
                            Map.of("remaining_codes", remaining)
                    );
                    return true;
                }
            }
        }

        auditService.recordSecurityEvent(
                AuditEventType.MFA_CHALLENGE_FAILURE,
                userId,
                user.getPlatformRole(),
                null,
                "MFA_RECOVERY_CODE",
                userId.toString(),
                AuditOutcome.FAILURE,
                request,
                Map.of("reason", "invalid_recovery_code")
        );

        return false;
    }

    @Transactional
    public void disableMfa(UUID userId, String password, String verificationCode, HttpServletRequest request) {
        Objects.requireNonNull(userId, "userId is required");

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new DisabledException("Account is disabled");
        }

        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Password verification failed");
        }

        // Verify either TOTP code or recovery code
        boolean validVerification = verifyTotpChallenge(userId, verificationCode, request)
                || verifyRecoveryCode(userId, verificationCode, request);

        if (!validVerification) {
            throw new BadCredentialsException("Invalid verification code for MFA disablement");
        }

        userMfaRepository.deleteByUserId(userId);
        recoveryCodeRepository.deleteByUserId(userId);

        user.setAuthVersion(user.getAuthVersion() + 1);
        userRepository.save(user);

        auditService.recordEvent(
                AuditEventType.MFA_DISABLED,
                userId,
                user.getPlatformRole(),
                null,
                "MFA",
                userId.toString(),
                AuditOutcome.SUCCESS,
                request,
                Map.of("auth_version", user.getAuthVersion())
        );

        sessionRevocationService.revokeAllSessions(user.getEmail(), userId, "MFA_DISABLED");
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 12; j++) {
                int idx = secureRandom.nextInt(RECOVERY_ALPHABET.length());
                sb.append(RECOVERY_ALPHABET.charAt(idx));
            }
            codes.add(sb.toString());
        }
        return codes;
    }
}
