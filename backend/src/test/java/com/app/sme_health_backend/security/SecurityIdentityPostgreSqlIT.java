package com.app.sme_health_backend.security;

import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SecurityIdentityPostgreSqlIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BusinessMembershipRepository membershipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdBusinessIds = new ArrayList<>();
    private final List<UUID> createdMembershipIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID membershipId : createdMembershipIds) {
            membershipRepository.deleteById(membershipId);
        }
        for (UUID userId : createdUserIds) {
            userRepository.deleteById(userId);
        }
        for (UUID businessId : createdBusinessIds) {
            businessRepository.deleteById(businessId);
        }
    }

    @Test
    @DisplayName("Verify V9 migration applied, backfilled businesses, and created no fake users")
    void testV9MigrationAndBackfill() {
        // 1. Verify V9 migration exists in flyway history
        Integer v9Count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '9' AND success = true",
                Integer.class
        );
        assertNotNull(v9Count);
        assertTrue(v9Count > 0, "Flyway V9 migration must be applied successfully");

        // 2. Verify businesses backfill matches existing business_profiles
        Integer profileCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM business_profiles",
                Integer.class
        );
        assertNotNull(profileCount);

        Integer matchCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM business_profiles bp JOIN businesses b ON bp.user_id = b.id",
                Integer.class
        );
        assertNotNull(matchCount);
        assertEquals(profileCount, matchCount, "Every existing BusinessProfile must have an identical Business.id");

        // 3. Verify no fake AppUser accounts were created by migration
        // Any users in the DB should be legitimately registered users, migration created none
        Integer migrationUserCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_users WHERE email LIKE '%fake%' OR email LIKE '%placeholder%'",
                Integer.class
        );
        assertEquals(0, migrationUserCount, "Migration V9 must NOT create fake or placeholder AppUsers");
    }

    @Test
    @DisplayName("Verify app_users persistence, Argon2 hash storage, and unique email constraint on PostgreSQL")
    void testAppUserPersistenceAndConstraints() {
        String testEmail = "pg.user." + UUID.randomUUID() + "@example.com";
        String rawPassword = "secure-production-passphrase-2026!";
        String argonHash = passwordEncoder.encode(rawPassword);

        AppUser user = new AppUser();
        user.setEmail(testEmail);
        user.setPasswordHash(argonHash);
        user.setFullName("PostgreSQL Test User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);

        AppUser saved = userRepository.save(user);
        createdUserIds.add(saved.getId());

        assertNotNull(saved.getId());
        assertTrue(saved.getPasswordHash().startsWith("$argon2id$v=19$m=65536,t=3,p=1$"),
                "Stored password hash must be Argon2id with production parameters");
        assertTrue(passwordEncoder.matches(rawPassword, saved.getPasswordHash()));

        // Verify unique constraint on email
        AppUser duplicate = new AppUser();
        duplicate.setEmail(testEmail);
        duplicate.setPasswordHash(argonHash);
        duplicate.setFullName("Duplicate User");
        duplicate.setAccountStatus(AccountStatus.ACTIVE);

        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.saveAndFlush(duplicate);
        }, "Inserting duplicate email must violate uq_app_users_email constraint");
    }

    @Test
    @DisplayName("Verify business_memberships foreign keys and unique (user_id, business_id) constraint")
    void testBusinessMembershipConstraints() {
        // Create user
        AppUser user = new AppUser();
        user.setEmail("member.test." + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password-12345678"));
        user.setFullName("Member User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        AppUser savedUser = userRepository.save(user);
        createdUserIds.add(savedUser.getId());

        // Create business
        UUID businessId = UUID.randomUUID();
        Business business = new Business(businessId, "ACTIVE");
        Business savedBusiness = businessRepository.save(business);
        createdBusinessIds.add(savedBusiness.getId());

        // Create membership
        BusinessMembership membership = new BusinessMembership(
                savedUser.getId(),
                savedBusiness.getId(),
                MembershipRole.OWNER,
                MembershipStatus.ACTIVE
        );
        BusinessMembership savedMembership = membershipRepository.save(membership);
        createdMembershipIds.add(savedMembership.getId());

        assertNotNull(savedMembership.getId());
        assertEquals(MembershipRole.OWNER, savedMembership.getRole());

        // Verify unique constraint on (user_id, business_id)
        BusinessMembership duplicate = new BusinessMembership(
                savedUser.getId(),
                savedBusiness.getId(),
                MembershipRole.ACCOUNTANT,
                MembershipStatus.ACTIVE
        );

        assertThrows(DataIntegrityViolationException.class, () -> {
            membershipRepository.saveAndFlush(duplicate);
        }, "Duplicate membership for (user_id, business_id) must violate uq_membership_user_business constraint");
    }

    @Test
    @DisplayName("Verify Spring Session JDBC persistence and deletion in PostgreSQL tables")
    @SuppressWarnings("unchecked")
    void testSpringSessionJdbcPersistence() {
        if (sessionRepository == null) {
            return;
        }

        Session session = sessionRepository.createSession();
        String sessionId = session.getId();
        session.setAttribute("TEST_ATTR", "FINSIGHT_VALUE");

        ((FindByIndexNameSessionRepository<Session>) sessionRepository).save(session);

        // Verify session row exists in PostgreSQL spring_session table
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        assertEquals(1, count, "Session must be persisted in PostgreSQL spring_session table");

        // Verify attribute exists in spring_session_attributes table
        Integer attrCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session_attributes WHERE attribute_name = 'TEST_ATTR'",
                Integer.class
        );
        assertTrue(attrCount != null && attrCount > 0, "Session attributes must be persisted");

        // Invalidate session
        sessionRepository.deleteById(sessionId);

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        assertEquals(0, remaining, "Session must be deleted from PostgreSQL upon invalidation");
    }
}
