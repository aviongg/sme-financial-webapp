package com.app.sme_health_backend.operations.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RuntimeHealthControllerTests {
    final ApplicationAvailability availability = mock(ApplicationAvailability.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final RuntimeHealthController controller = new RuntimeHealthController(availability, jdbc);

    @Test void liveDoesNotDependOnDatabase() {
        assertEquals("UP", controller.live().get("status"));
        verifyNoInteractions(jdbc);
    }

    @Test void startupAndShutdownRefuseTrafficWithoutDatabaseQuery() {
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        assertEquals(503, controller.ready().getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test void acceptingTrafficAlsoRequiresAuthenticatedDatabaseQuery() {
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        when(jdbc.query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class))).thenReturn(1);
        assertEquals(200, controller.ready().getStatusCode().value());
    }

    @Test void databaseFailureReturns503WithoutLeakingDiagnostics() {
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        when(jdbc.query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class)))
                .thenThrow(new IllegalStateException("sensitive database diagnostic"));
        assertEquals(java.util.Map.of("status", "DOWN"), controller.ready().getBody());
        assertEquals(503, controller.ready().getStatusCode().value());
    }
}
