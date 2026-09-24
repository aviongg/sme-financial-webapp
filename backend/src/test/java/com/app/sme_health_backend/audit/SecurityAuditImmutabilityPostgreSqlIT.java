package com.app.sme_health_backend.audit;

import com.app.sme_health_backend.audit.entity.SecurityAuditEvent;
import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SecurityAuditImmutabilityPostgreSqlIT {

    private static final String APP_USER = "finsight_app";
    private static final String APP_PASSWORD = "FinSight_App_Runtime_2026_!*7vQ";

    private static final String MIGRATOR_USER = "finsight_migrator";
    private static final String MIGRATOR_PASSWORD = "FinSight_Migrator_Ddl_2026_!$4mP";

    private static final String DBA_USER = "finsight_dba";
    private static final String DBA_PASSWORD = "FinSight_Dba_Admin_Sec_2026_!#9xK";

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/sme_health";

    @Autowired
    private SecurityAuditRepository auditRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BusinessRepository businessRepository;

    private Connection createConnection(String user, String password) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("sslmode", "prefer");
        return DriverManager.getConnection(JDBC_URL, props);
    }

    @Test
    @DisplayName("finsight_app INSERT and SELECT are allowed on security_audit_events")
    void testFinsightAppInsertAndSelectAllowed() throws Exception {
        UUID eventId = UUID.randomUUID();
        try (Connection conn = createConnection(APP_USER, APP_PASSWORD)) {
            // INSERT
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO security_audit_events (id, occurred_at, event_type, target_type, outcome, is_system, request_id) " +
                    "VALUES (?, now(), 'AUTH_LOGIN_SUCCESS', 'USER', 'SUCCESS', false, ?)")) {
                ps.setObject(1, eventId);
                ps.setString(2, UUID.randomUUID().toString());
                int rows = ps.executeUpdate();
                assertEquals(1, rows, "finsight_app must be able to insert audit events");
            }

            // SELECT
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, event_type, outcome FROM security_audit_events WHERE id = ?")) {
                ps.setObject(1, eventId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "finsight_app must be able to select audit events");
                    assertEquals("AUTH_LOGIN_SUCCESS", rs.getString("event_type"));
                    assertEquals("SUCCESS", rs.getString("outcome"));
                }
            }
        }
    }

    @Test
    @DisplayName("finsight_app UPDATE, DELETE, and TRUNCATE are denied by database privileges")
    void testFinsightAppUpdateDeleteTruncateDenied() throws Exception {
        UUID eventId = UUID.randomUUID();
        // Insert an event first using migrator
        try (Connection conn = createConnection(MIGRATOR_USER, MIGRATOR_PASSWORD)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO security_audit_events (id, occurred_at, event_type, target_type, outcome, is_system, request_id) " +
                    "VALUES (?, now(), 'AUTH_LOGIN_SUCCESS', 'USER', 'SUCCESS', false, ?)")) {
                ps.setObject(1, eventId);
                ps.setString(2, "priv-test-req");
                ps.executeUpdate();
            }
        }

        try (Connection conn = createConnection(APP_USER, APP_PASSWORD)) {
            // UPDATE denied
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE security_audit_events SET event_type = 'TAMPERED' WHERE id = ?")) {
                ps.setObject(1, eventId);
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                        "UPDATE must be denied to finsight_app");
                assertTrue(ex.getSQLState().equals("42501") || ex.getMessage().toLowerCase().contains("permission denied"),
                        "Expected permission denied (42501): " + ex.getMessage());
            }

            // DELETE denied
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM security_audit_events WHERE id = ?")) {
                ps.setObject(1, eventId);
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                        "DELETE must be denied to finsight_app");
                assertTrue(ex.getSQLState().equals("42501") || ex.getMessage().toLowerCase().contains("permission denied"),
                        "Expected permission denied (42501): " + ex.getMessage());
            }

            // TRUNCATE denied
            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () -> stmt.execute("TRUNCATE TABLE security_audit_events"),
                        "TRUNCATE must be denied to finsight_app");
                assertTrue(ex.getSQLState().equals("42501") || ex.getMessage().toLowerCase().contains("permission denied"),
                        "Expected permission denied (42501): " + ex.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Row trigger trg_audit_no_update_delete blocks UPDATE and DELETE even under privileged role")
    void testRowTriggerBlocksUpdateAndDelete() throws Exception {
        UUID eventId = UUID.randomUUID();
        try (Connection conn = createConnection(MIGRATOR_USER, MIGRATOR_PASSWORD)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO security_audit_events (id, occurred_at, event_type, target_type, outcome, is_system, request_id) " +
                    "VALUES (?, now(), 'AUTH_LOGIN_SUCCESS', 'USER', 'SUCCESS', false, ?)")) {
                ps.setObject(1, eventId);
                ps.setString(2, "trigger-test-req");
                ps.executeUpdate();
            }

            // Attempt UPDATE under table owner finsight_migrator
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE security_audit_events SET target_type = 'TAMPERED' WHERE id = ?")) {
                ps.setObject(1, eventId);
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                        "Trigger must block UPDATE on security_audit_events");
                assertTrue(ex.getMessage().contains("append-only") || ex.getSQLState().equals("55000"),
                        "Expected trigger error 55000: " + ex.getMessage());
            }

            // Attempt DELETE under table owner finsight_migrator
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM security_audit_events WHERE id = ?")) {
                ps.setObject(1, eventId);
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                        "Trigger must block DELETE on security_audit_events");
                assertTrue(ex.getMessage().contains("append-only") || ex.getSQLState().equals("55000"),
                        "Expected trigger error 55000: " + ex.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Statement trigger trg_audit_no_truncate blocks TRUNCATE")
    void testStatementTriggerBlocksTruncate() throws Exception {
        try (Connection conn = createConnection(MIGRATOR_USER, MIGRATOR_PASSWORD)) {
            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () -> stmt.execute("TRUNCATE TABLE security_audit_events"),
                        "Trigger must block TRUNCATE on security_audit_events");
                assertTrue(ex.getMessage().contains("TRUNCATE is prohibited") || ex.getSQLState().equals("55000"),
                        "Expected trigger error 55000: " + ex.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Audit event survives referenced user deletion with actor_user_id set to NULL")
    void testAuditSurvivesUserDeletion() {
        AppUser user = new AppUser();
        user.setEmail("audit-del-user-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$argon2id$v=19$m=65536,t=3,p=1$fakehash1234567890123456$fakehash");
        user.setFullName("Audit Delete User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        AppUser savedUser = userRepository.saveAndFlush(user);

        SecurityAuditEvent event = new SecurityAuditEvent();
        event.setOccurredAt(OffsetDateTime.now());
        event.setEventType(AuditEventType.AUTH_LOGIN_SUCCESS.name());
        event.setActorUserId(savedUser.getId());
        event.setTargetType("USER");
        event.setTargetId(savedUser.getId().toString());
        event.setOutcome(AuditOutcome.SUCCESS);
        event.setRequestId(UUID.randomUUID().toString());
        SecurityAuditEvent savedEvent = auditRepository.saveAndFlush(event);

        // Delete user
        userRepository.delete(savedUser);
        userRepository.flush();

        // Audit event must still exist with actor_user_id = null
        SecurityAuditEvent retrieved = auditRepository.findById(savedEvent.getId()).orElse(null);
        assertNotNull(retrieved, "Audit event must survive user deletion");
        assertNull(retrieved.getActorUserId(), "actor_user_id must be SET NULL upon user deletion");
    }

    @Test
    @DisplayName("Audit event survives business deletion with business_id set to NULL")
    void testAuditSurvivesBusinessDeletion() {
        Business business = new Business();
        business.setId(UUID.randomUUID());
        business.setStatus("ACTIVE");
        Business savedBiz = businessRepository.saveAndFlush(business);

        SecurityAuditEvent event = new SecurityAuditEvent();
        event.setOccurredAt(OffsetDateTime.now());
        event.setEventType(AuditEventType.BUSINESS_CREATED.name());
        event.setBusinessId(savedBiz.getId());
        event.setTargetType("BUSINESS");
        event.setTargetId(savedBiz.getId().toString());
        event.setOutcome(AuditOutcome.SUCCESS);
        event.setRequestId(UUID.randomUUID().toString());
        SecurityAuditEvent savedEvent = auditRepository.saveAndFlush(event);

        // Delete business
        businessRepository.delete(savedBiz);
        businessRepository.flush();

        // Audit event must still exist with business_id = null
        SecurityAuditEvent retrieved = auditRepository.findById(savedEvent.getId()).orElse(null);
        assertNotNull(retrieved, "Audit event must survive business deletion");
        assertNull(retrieved.getBusinessId(), "business_id must be SET NULL upon business deletion");
    }
}
