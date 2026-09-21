package com.app.sme_health_backend.score;

import com.app.sme_health_backend.score.dto.ScoreResult;
import com.app.sme_health_backend.score.repository.ScoreResultReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreResultReaderTests {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ResultSet row;
    private ScoreResultReader reader;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reader = new ScoreResultReader(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void mapsSulemansJsonWithNullValuesAndFractionalCompleteness() throws Exception {
        stubRow("""
                {"cashflow":72.35,"profitability":64.50,"repayment":null,"trend":null,"compliance":100}
                """);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<ScoreResult>>any(), eq(userId)))
                .thenAnswer(invocation -> List.of(invocation.<RowMapper<ScoreResult>>getArgument(1).mapRow(row, 0)));

        ScoreResult score = reader.findLatest(userId).orElseThrow();

        assertEquals(userId, score.getUserId());
        assertEquals("2026-09", score.getMonth());
        assertEquals(new BigDecimal("72.35"), score.getComponentScores().get("cashflow"));
        assertNull(score.getComponentScores().get("repayment"));
        assertNull(score.getComponentScores().get("trend"));
        assertEquals(5, score.getComponentScores().size());
        assertEquals(new BigDecimal("0.65"), score.getDataCompleteness());
        assertEquals("Stable", score.getBand());
        assertEquals("profitability", score.getWeakestComponent());
        assertEquals(LocalDateTime.of(2026, 9, 19, 12, 30), score.getComputedAt());
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(query.capture(), ArgumentMatchers.<RowMapper<ScoreResult>>any(), eq(userId));
        assertTrue(query.getValue().contains("ORDER BY month DESC LIMIT 1 FOR SHARE"));
    }

    @Test
    void monthLookupUsesBoundParametersAndLocksPersistedSnapshot() {
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<ScoreResult>>any(), eq(userId), eq("2026-08")))
                .thenReturn(List.of());
        assertTrue(reader.findByUserIdAndMonth(userId, "2026-08").isEmpty());
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(query.capture(), ArgumentMatchers.<RowMapper<ScoreResult>>any(), eq(userId), eq("2026-08"));
        assertTrue(query.getValue().contains("WHERE user_id = ? AND month = ? FOR SHARE"));
    }

    @Test
    void rejectsMalformedPersistedComponentsInsteadOfInventingScores() throws Exception {
        when(row.getObject("user_id", UUID.class)).thenReturn(userId);
        when(row.getString("month")).thenReturn("2026-09");
        when(row.getBigDecimal("composite_score")).thenReturn(new BigDecimal("68.25"));
        when(row.getString("band")).thenReturn("Stable");
        when(row.getString("component_scores")).thenReturn("""
                {"cash_flow":72.35,"profitability":64.50,"repayment":null,"trend":null,"compliance":100}
                """);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<ScoreResult>>any(), eq(userId)))
                .thenAnswer(invocation -> List.of(invocation.<RowMapper<ScoreResult>>getArgument(1).mapRow(row, 0)));
        assertThrows(IllegalStateException.class, () -> reader.findLatest(userId));
    }

    @Test
    void rejectsNumericStringsInsteadOfCoercingThem() throws Exception {
        when(row.getObject("user_id", UUID.class)).thenReturn(userId);
        when(row.getString("month")).thenReturn("2026-09");
        when(row.getBigDecimal("composite_score")).thenReturn(new BigDecimal("68.25"));
        when(row.getString("band")).thenReturn("Stable");
        when(row.getString("component_scores")).thenReturn("""
                {"cashflow":"72.35","profitability":64.50,"repayment":null,"trend":null,"compliance":100}
                """);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<ScoreResult>>any(), eq(userId)))
                .thenAnswer(invocation -> List.of(invocation.<RowMapper<ScoreResult>>getArgument(1).mapRow(row, 0)));
        assertThrows(IllegalStateException.class, () -> reader.findLatest(userId));
    }

    @Test
    void rejectsInvalidRequestsBeforeTouchingTheDatabase() {
        assertThrows(IllegalArgumentException.class, () -> reader.findLatest(null));
        assertThrows(IllegalArgumentException.class, () -> reader.findByUserIdAndMonth(userId, "2026-13"));
        verifyNoInteractions(jdbcTemplate);
    }

    private void stubRow(String components) throws Exception {
        when(row.getObject("user_id", UUID.class)).thenReturn(userId);
        when(row.getString("month")).thenReturn("2026-09");
        when(row.getBigDecimal("composite_score")).thenReturn(new BigDecimal("68.25"));
        when(row.getString("band")).thenReturn("Stable");
        when(row.getString("component_scores")).thenReturn(components);
        when(row.getString("weakest_component")).thenReturn("profitability");
        when(row.getBigDecimal("data_completeness")).thenReturn(new BigDecimal("0.65"));
        when(row.getTimestamp("computed_at")).thenReturn(Timestamp.valueOf("2026-09-19 12:30:00"));
    }
}
