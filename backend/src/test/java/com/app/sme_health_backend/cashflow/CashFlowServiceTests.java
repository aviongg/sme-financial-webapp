package com.app.sme_health_backend.cashflow;

import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.cashflow.service.CashFlowService;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashFlowServiceTests {

    @Mock
    private MonthlyRecordRepository monthlyRecordRepository;

    private CashFlowService cashFlowService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        cashFlowService = new CashFlowService(monthlyRecordRepository);
        userId = UUID.randomUUID();
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoRecords() {
        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(Collections.emptyList());

        List<CashFlowChartPointResponse> result = cashFlowService.getCashFlowHistory(userId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(monthlyRecordRepository).findTop6ByUserIdOrderByMonthDesc(userId);
    }

    @Test
    void shouldReturnSinglePointForSingleMonth() {
        MonthlyRecord record = createRecord("2026-08", "150000", "100000", "200000");

        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(record));

        List<CashFlowChartPointResponse> result = cashFlowService.getCashFlowHistory(userId);

        assertEquals(1, result.size());
        CashFlowChartPointResponse point = result.getFirst();
        assertEquals("2026-08", point.getMonth());
        assertEquals(0, point.getInflow().compareTo(new BigDecimal("150000")));
        assertEquals(0, point.getOutflow().compareTo(new BigDecimal("100000")));
        assertEquals(0, point.getNet().compareTo(new BigDecimal("50000")));
        assertEquals(0, point.getRunningBalance().compareTo(new BigDecimal("200000")));
    }

    @Test
    void shouldReturnMultipleMonthsInChronologicalAscendingOrder() {
        // Repository returns descending order
        MonthlyRecord m8 = createRecord("2026-08", "150000", "100000", "200000");
        MonthlyRecord m7 = createRecord("2026-07", "140000", "90000", "150000");
        MonthlyRecord m6 = createRecord("2026-06", "130000", "80000", "100000");

        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(m8, m7, m6));

        List<CashFlowChartPointResponse> result = cashFlowService.getCashFlowHistory(userId);

        assertEquals(3, result.size());
        // Chronological ascending order
        assertEquals("2026-06", result.get(0).getMonth());
        assertEquals("2026-07", result.get(1).getMonth());
        assertEquals("2026-08", result.get(2).getMonth());
    }

    @Test
    void shouldReturnAtMostSixLatestMonthsInChronologicalAscendingOrder() {
        // Repository returns top 6 in descending order
        MonthlyRecord m8 = createRecord("2026-08", "180000", "100000", "250000");
        MonthlyRecord m7 = createRecord("2026-07", "170000", "95000", "170000");
        MonthlyRecord m6 = createRecord("2026-06", "160000", "90000", "95000");
        MonthlyRecord m5 = createRecord("2026-05", "150000", "85000", "25000");
        MonthlyRecord m4 = createRecord("2026-04", "140000", "80000", "40000");
        MonthlyRecord m3 = createRecord("2026-03", "130000", "75000", "30000");

        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(m8, m7, m6, m5, m4, m3));

        List<CashFlowChartPointResponse> result = cashFlowService.getCashFlowHistory(userId);

        assertEquals(6, result.size());
        assertEquals("2026-03", result.get(0).getMonth());
        assertEquals("2026-04", result.get(1).getMonth());
        assertEquals("2026-05", result.get(2).getMonth());
        assertEquals("2026-06", result.get(3).getMonth());
        assertEquals("2026-07", result.get(4).getMonth());
        assertEquals("2026-08", result.get(5).getMonth());
    }

    @Test
    void shouldCalculateNetCashFlowCorrectly() {
        MonthlyRecord record = createRecord("2026-08", "150000", "100000", "200000");

        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(record));

        List<CashFlowChartPointResponse> result = cashFlowService.getCashFlowHistory(userId);

        assertEquals(1, result.size());
        assertEquals(0, result.getFirst().getNet().compareTo(new BigDecimal("50000")));
    }

    @Test
    void shouldPreserveZeroOutflowAndCalculateNetEqualToInflow() {
        MonthlyRecord record = createRecord("2026-08", "100000", "0", "120000");

        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(record));

        List<CashFlowChartPointResponse> result = cashFlowService.getCashFlowHistory(userId);

        assertEquals(1, result.size());
        CashFlowChartPointResponse point = result.getFirst();
        assertEquals(0, point.getInflow().compareTo(new BigDecimal("100000")));
        assertEquals(0, point.getOutflow().compareTo(BigDecimal.ZERO));
        assertEquals(0, point.getNet().compareTo(new BigDecimal("100000")));
        assertEquals(0, point.getRunningBalance().compareTo(new BigDecimal("120000")));
    }

    @Test
    void shouldMapRunningBalanceFromEndingCashBalanceNotCumulativeNet() {
        // net is 150000 - 100000 = 50000, but ending cash balance is 999999
        MonthlyRecord record = createRecord("2026-08", "150000", "100000", "999999");

        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(record));

        List<CashFlowChartPointResponse> result = cashFlowService.getCashFlowHistory(userId);

        assertEquals(1, result.size());
        assertEquals(0, result.getFirst().getRunningBalance().compareTo(new BigDecimal("999999")));
        assertNotEquals(0, result.getFirst().getRunningBalance().compareTo(new BigDecimal("50000")));
    }

    @Test
    void shouldThrowExceptionWhenUserIdIsNull() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cashFlowService.getCashFlowHistory(null)
                );

        assertEquals("User ID is required", exception.getMessage());
        verify(monthlyRecordRepository, never()).findTop6ByUserIdOrderByMonthDesc(any());
    }

    private MonthlyRecord createRecord(String month, String inflow, String outflow, String balance) {
        MonthlyRecord record = new MonthlyRecord();
        record.setUserId(userId);
        record.setMonth(month);
        record.setCashInflow(new BigDecimal(inflow));
        record.setCashOutflow(new BigDecimal(outflow));
        record.setCashBalanceEom(new BigDecimal(balance));
        return record;
    }
}
