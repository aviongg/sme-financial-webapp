package com.app.sme_health_backend.records;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
import com.app.sme_health_backend.scoring.service.ScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonthlyRecordContributionTests {

    @Mock
    private MonthlyRecordRepository repository;

    @Mock
    private ScoringService scoringService;

    private MonthlyRecordService service;
    private final UUID userId = UUID.randomUUID();
    private final String month = "2026-03";

    @BeforeEach
    void setUp() {
        service = new MonthlyRecordService(repository, scoringService);
    }

    @Test
    void incrementsPeriodFlowFieldsWithoutMutatingPointInTimeBalances() {
        MonthlyRecord existing = new MonthlyRecord();
        existing.setUserId(userId);
        existing.setMonth(month);
        existing.setRevenue(new BigDecimal("100000.00"));
        existing.setOperatingExpenses(new BigDecimal("40000.00"));
        existing.setCogs(new BigDecimal("30000.00"));
        existing.setCashInflow(new BigDecimal("95000.00"));
        existing.setCashOutflow(new BigDecimal("65000.00"));
        // Point-in-time balances:
        existing.setCashBalanceEom(new BigDecimal("50000.00"));
        existing.setReceivablesOutstanding(new BigDecimal("15000.00"));
        existing.setPayablesOutstanding(new BigDecimal("12000.00"));
        existing.setInventoryValue(new BigDecimal("25000.00"));
        existing.setLoanOutstanding(new BigDecimal("80000.00"));

        when(repository.findByUserIdAndMonth(userId, month)).thenReturn(Optional.of(existing));
        when(repository.save(any(MonthlyRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByUserIdOrderByMonthAsc(userId)).thenReturn(List.of(existing));

        // Apply an operating expense of 5,000 with cash outflow of 5,000
        MonthlyRecord updated = service.applyDocumentContribution(
                userId,
                month,
                "operating_expenses",
                "cash_outflow",
                new BigDecimal("5000.00"),
                null
        );

        // Period-flow fields incremented:
        assertEquals(new BigDecimal("45000.00"), updated.getOperatingExpenses());
        assertEquals(new BigDecimal("70000.00"), updated.getCashOutflow());
        assertEquals(new BigDecimal("100000.00"), updated.getRevenue());
        assertEquals(new BigDecimal("30000.00"), updated.getCogs());
        assertEquals(new BigDecimal("95000.00"), updated.getCashInflow());

        // Point-in-time balances remain untouched:
        assertEquals(new BigDecimal("50000.00"), updated.getCashBalanceEom());
        assertEquals(new BigDecimal("15000.00"), updated.getReceivablesOutstanding());
        assertEquals(new BigDecimal("12000.00"), updated.getPayablesOutstanding());
        assertEquals(new BigDecimal("25000.00"), updated.getInventoryValue());
        assertEquals(new BigDecimal("80000.00"), updated.getLoanOutstanding());

        verify(scoringService).calculateAndSaveScore(userId, month);
    }

    @Test
    void rejectsContributionForNonExistentMonthWithoutInitialCashBalance() {
        when(repository.findByUserIdAndMonth(userId, "2026-04")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.applyDocumentContribution(
                        userId,
                        "2026-04",
                        "revenue",
                        "cash_inflow",
                        new BigDecimal("25000.00"),
                        null
                )
        );

        assertTrue(ex.getMessage().contains("initialCashBalanceEom"));
        verify(repository, never()).save(any());
    }

    @Test
    void createsMinimalValidRecordWhenInitialCashBalanceIsProvided() {
        when(repository.findByUserIdAndMonth(userId, "2026-04")).thenReturn(Optional.empty());
        when(repository.save(any(MonthlyRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        MonthlyRecord expectedRecord = new MonthlyRecord();
        expectedRecord.setUserId(userId);
        expectedRecord.setMonth("2026-04");
        when(repository.findByUserIdOrderByMonthAsc(userId)).thenReturn(List.of(expectedRecord));

        MonthlyRecord created = service.applyDocumentContribution(
                userId,
                "2026-04",
                "revenue",
                "cash_inflow",
                new BigDecimal("25000.00"),
                new BigDecimal("10000.00")
        );

        assertEquals(userId, created.getUserId());
        assertEquals("2026-04", created.getMonth());
        assertEquals(new BigDecimal("25000.00"), created.getRevenue());
        assertEquals(new BigDecimal("25000.00"), created.getCashInflow());
        assertEquals(new BigDecimal("10000.00"), created.getCashBalanceEom());
        assertEquals(BigDecimal.ZERO, created.getOperatingExpenses());
        assertEquals(BigDecimal.ZERO, created.getCashOutflow());
        assertEquals("none", created.getFinancingType());

        verify(repository).save(any(MonthlyRecord.class));
        verify(scoringService).calculateAndSaveScore(userId, "2026-04");
    }

    @Test
    void rejectsInvalidAmountOrNegativeCashBalance() {
        assertThrows(IllegalArgumentException.class, () ->
                service.applyDocumentContribution(userId, month, "revenue", "none", BigDecimal.ZERO, null));

        assertThrows(IllegalArgumentException.class, () ->
                service.applyDocumentContribution(userId, month, "revenue", "none", new BigDecimal("-10.00"), null));

        when(repository.findByUserIdAndMonth(userId, "2026-05")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () ->
                service.applyDocumentContribution(userId, "2026-05", "revenue", "none", new BigDecimal("100.00"), new BigDecimal("-5.00")));
    }
}
