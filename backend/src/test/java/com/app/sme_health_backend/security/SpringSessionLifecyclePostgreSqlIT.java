package com.app.sme_health_backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SpringSessionLifecyclePostgreSqlIT {

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Runtime role finsight_app executes full Spring Session lifecycle without DDL")
    void testCompleteSessionLifecycleUnderAppRole() {
        @SuppressWarnings("unchecked")
        FindByIndexNameSessionRepository<Session> repo = (FindByIndexNameSessionRepository<Session>) sessionRepository;

        // 1. Create
        Session session = repo.createSession();
        String sessionId = session.getId();
        assertNotNull(sessionId);

        // 2. Set attributes and max inactive interval
        String attrKey = "TEST_ATTR_" + UUID.randomUUID();
        String attrVal = "FinSight_Session_Val_" + UUID.randomUUID();
        session.setAttribute(attrKey, attrVal);
        session.setMaxInactiveInterval(Duration.ofMinutes(15));
        session.setLastAccessedTime(Instant.now());

        // 3. Save (INSERT into spring_session and spring_session_attributes)
        repo.save(session);

        // Verify rows in PostgreSQL
        Integer sessionCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        assertEquals(1, sessionCount, "spring_session record must be created by finsight_app");

        Integer attrCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session_attributes WHERE session_primary_id = (SELECT primary_id FROM spring_session WHERE session_id = ?)",
                Integer.class,
                sessionId
        );
        assertTrue(attrCount != null && attrCount >= 1, "spring_session_attributes record must be created by finsight_app");

        // 4. Read
        Session fetched = repo.findById(sessionId);
        assertNotNull(fetched);
        assertEquals(attrVal, fetched.getAttribute(attrKey));

        // 5. Update expiry & attributes
        String updatedVal = "Updated_" + attrVal;
        fetched.setAttribute(attrKey, updatedVal);
        fetched.setLastAccessedTime(Instant.now());
        repo.save(fetched);

        Session updatedSession = repo.findById(sessionId);
        assertNotNull(updatedSession);
        assertEquals(updatedVal, updatedSession.getAttribute(attrKey));

        // 6. Delete
        repo.deleteById(sessionId);

        // Verify deleted from DB
        Integer remainingCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        assertEquals(0, remainingCount, "spring_session record must be deleted by finsight_app");

        Session deletedSession = repo.findById(sessionId);
        assertNull(deletedSession, "Session must no longer be found after deletion");
    }
}
