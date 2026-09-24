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
        String migratorPw = System.getenv("POSTGRES_MIGRATOR_PASSWORD");
        if (migratorPw == null || migratorPw.isBlank()) {
            migratorPw = System.getProperty("migrator_db_password", DEFAULT_DEV_MIGRATOR_PASSWORD);
        }
        String url = System.getProperty("spring.flyway.url");
        if (url == null || url.isBlank()) {
            url = "jdbc:postgresql://localhost:5432/sme_health";
        }
        return java.sql.DriverManager.getConnection(url, "finsight_migrator", migratorPw);
    }

    @Transactional
    public void resetPlatformAdminMfa(String email) {
        Objects.requireNonNull(email, "email is required");
        String normalizedEmail = EmailValidator.normalizeAndValidate(email);

        AppUser user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for email " + normalizedEmail));

        userMfaRepository.deleteByUserId(user.getId());
        recoveryCodeRepository.deleteByUserId(user.getId());

        jdbcTemplate.update(
                "UPDATE app_users SET must_change_password = true, auth_version = auth_version + 1, updated_at = now() WHERE id = ?",
                user.getId()
        );

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
