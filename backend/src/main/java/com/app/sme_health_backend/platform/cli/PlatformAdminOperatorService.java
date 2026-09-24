package com.app.sme_health_backend.platform.cli;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.validation.EmailValidator;
import com.app.sme_health_backend.mfa.repository.UserMfaRecoveryCodeRepository;
import com.app.sme_health_backend.mfa.repository.UserMfaRepository;
import com.app.sme_health_backend.security.service.SessionRevocationService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

/**
 * Operator-only administration service for platform administrator bootstrap, revocation, and MFA reset.
 * Intended for non-web CLI invocation under authorized operator credentials.
 */
@Service
public class PlatformAdminOperatorService {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminOperatorService.class);

    private final AppUserRepository userRepository;
    private final UserMfaRepository userMfaRepository;
    private final UserMfaRecoveryCodeRepository recoveryCodeRepository;
    private final SessionRevocationService sessionRevocationService;
    private final SecurityAuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public PlatformAdminOperatorService(
            AppUserRepository userRepository,
            UserMfaRepository userMfaRepository,
            UserMfaRecoveryCodeRepository recoveryCodeRepository,
            SessionRevocationService sessionRevocationService,
            SecurityAuditService auditService,
            JdbcTemplate jdbcTemplate
    ) {
        this.userRepository = userRepository;
        this.userMfaRepository = userMfaRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.sessionRevocationService = sessionRevocationService;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String DEFAULT_DEV_MIGRATOR_PASSWORD = "FinSight_Migrator_Ddl_2026_!$4mP";

    @Transactional
    public void promotePlatformAdmin(String email) {
        try (java.sql.Connection conn = openOperatorConnection()) {
            promotePlatformAdmin(conn, email);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Operator command failed to acquire operator connection: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void promotePlatformAdmin(java.sql.Connection operatorConn, String email) {
        Objects.requireNonNull(email, "email is required");
        String normalizedEmail = EmailValidator.normalizeAndValidate(email);

        AppUser user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot promote: account not found for email " + normalizedEmail));

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Cannot promote: account status is " + user.getAccountStatus() + "; must be ACTIVE");
        }

        if ("PLATFORM_ADMIN".equals(user.getPlatformRole())) {
            throw new IllegalStateException("Account is already a PLATFORM_ADMIN");
        }

        // Execute role elevation under operator connection (e.g. finsight_migrator / finsight_dba)
        try (java.sql.PreparedStatement ps = operatorConn.prepareStatement(
                "UPDATE app_users SET platform_role = 'PLATFORM_ADMIN', must_change_password = true, auth_version = auth_version + 1, updated_at = now() WHERE id = ?")) {
            ps.setObject(1, user.getId());
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalStateException("User record update did not match any rows");
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Database rejected platform_role elevation: " + e.getMessage(), e);
        }

        sessionRevocationService.revokeAllSessions(user.getEmail(), user.getId(), "PLATFORM_ADMIN_GRANTED");

        auditService.recordEvent(
                AuditEventType.PLATFORM_ADMIN_GRANTED,
                user.getId(),
                "PLATFORM_ADMIN",
                null,
                "USER",
                user.getId().toString(),
                AuditOutcome.SUCCESS,
                null,
                Map.of("action", "promote_platform_admin")
        );

        log.info("Operator command: account {} successfully promoted to PLATFORM_ADMIN", normalizedEmail);
    }

    @Transactional
    public void revokePlatformAdmin(String email) {
        try (java.sql.Connection conn = openOperatorConnection()) {
            revokePlatformAdmin(conn, email);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Operator command failed to acquire operator connection: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void revokePlatformAdmin(java.sql.Connection operatorConn, String email) {
        Objects.requireNonNull(email, "email is required");
        String normalizedEmail = EmailValidator.normalizeAndValidate(email);

        AppUser user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for email " + normalizedEmail));

        if (!"PLATFORM_ADMIN".equals(user.getPlatformRole())) {
            throw new IllegalStateException("Account is not a PLATFORM_ADMIN");
        }

        try (java.sql.PreparedStatement ps = operatorConn.prepareStatement(
                "UPDATE app_users SET platform_role = NULL, auth_version = auth_version + 1, updated_at = now() WHERE id = ?")) {
            ps.setObject(1, user.getId());
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Database rejected platform_role revocation: " + e.getMessage(), e);
        }

        sessionRevocationService.revokeAllSessions(user.getEmail(), user.getId(), "PLATFORM_ADMIN_REVOKED");

        auditService.recordEvent(
                AuditEventType.PLATFORM_ADMIN_REVOKED,
                user.getId(),
                null,
                null,
                "USER",
                user.getId().toString(),
                AuditOutcome.SUCCESS,
                null,
                Map.of("action", "revoke_platform_admin")
        );

        log.info("Operator command: PLATFORM_ADMIN role revoked for account {}", normalizedEmail);
    }

    private java.sql.Connection openOperatorConnection() throws java.sql.SQLException {
        String migratorPw = resolveOperatorPassword();
        String url = resolveOperatorUrl();
        String user = resolveOperatorUser();
        return java.sql.DriverManager.getConnection(url, user, migratorPw);
    }

    private static String resolveOperatorUser() {
        String sysProp = System.getProperty("spring.flyway.user");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp;
        }
        String envUser = System.getenv("POSTGRES_MIGRATOR_USER");
        if (envUser != null && !envUser.trim().isEmpty()) {
            return envUser;
        }
        return "finsight_migrator";
    }

    private static String resolveOperatorUrl() {
        String sysProp = System.getProperty("spring.flyway.url");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp;
        }
        String envUrl = System.getenv("SPRING_FLYWAY_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            return envUrl;
        }
        envUrl = System.getenv("POSTGRES_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            return envUrl;
        }
        return "jdbc:postgresql://localhost:5432/sme_health";
    }

    private static String resolveOperatorPassword() {
        // 1. Check configtree secret /run/secrets/migrator_db_password
        java.nio.file.Path secretPath = java.nio.file.Paths.get("/run/secrets/migrator_db_password");
        if (java.nio.file.Files.exists(secretPath)) {
            try {
                String content = java.nio.file.Files.readString(secretPath, java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    return content;
                }
            } catch (java.io.IOException ignored) {
            }
        }

        // 2. Check custom secrets dir
        String secretsDir = System.getProperty("finsight.secrets.dir");
        if (secretsDir != null) {
            java.nio.file.Path customSecret = java.nio.file.Paths.get(secretsDir, "migrator_db_password");
            if (java.nio.file.Files.exists(customSecret)) {
                try {
                    String content = java.nio.file.Files.readString(customSecret, java.nio.charset.StandardCharsets.UTF_8).trim();
                    if (!content.isEmpty()) {
                        return content;
                    }
                } catch (java.io.IOException ignored) {
                }
            }
        }

        // 3. Check system properties
        String sysProp = System.getProperty("migrator_db_password");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp;
        }
        sysProp = System.getProperty("spring.flyway.password");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp;
        }

        // 4. Check environment variables
        String envPass = System.getenv("MIGRATOR_DB_PASSWORD");
        if (envPass != null && !envPass.trim().isEmpty()) {
            return envPass;
        }
        envPass = System.getenv("POSTGRES_MIGRATOR_PASSWORD");
        if (envPass != null && !envPass.trim().isEmpty()) {
            return envPass;
        }
        envPass = System.getenv("migrator_db_password");
        if (envPass != null && !envPass.trim().isEmpty()) {
            return envPass;
        }

        // 5. Development default fallback
        return DEFAULT_DEV_MIGRATOR_PASSWORD;
    }

    @Transactional
    public void resetPlatformAdminMfa(String email) {
        try (java.sql.Connection conn = openOperatorConnection()) {
            resetPlatformAdminMfa(conn, email);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Operator command failed to acquire operator connection: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void resetPlatformAdminMfa(java.sql.Connection operatorConn, String email) {
        Objects.requireNonNull(email, "email is required");
        String normalizedEmail = EmailValidator.normalizeAndValidate(email);

        AppUser user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for email " + normalizedEmail));

        userMfaRepository.deleteByUserId(user.getId());
        recoveryCodeRepository.deleteByUserId(user.getId());

        try (java.sql.PreparedStatement ps = operatorConn.prepareStatement(
                "UPDATE app_users SET must_change_password = true, auth_version = auth_version + 1, updated_at = now() WHERE id = ?")) {
            ps.setObject(1, user.getId());
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Database error executing MFA reset: " + e.getMessage(), e);
        }

        sessionRevocationService.revokeAllSessions(user.getEmail(), user.getId(), "PLATFORM_ADMIN_MFA_RESET");

        auditService.recordEvent(
                AuditEventType.PLATFORM_ADMIN_MFA_RESET,
                user.getId(),
                user.getPlatformRole(),
                null,
                "USER",
                user.getId().toString(),
                AuditOutcome.SUCCESS,
                null,
                Map.of("action", "reset_platform_admin_mfa")
        );

        log.info("Operator command: MFA reset completed for account {}", normalizedEmail);
    }
}
