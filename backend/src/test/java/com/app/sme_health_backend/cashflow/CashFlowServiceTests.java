package com.app.sme_health_backend.cashflow;

import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.cashflow.dto.CashFlowProjectionResponse;
import com.app.sme_health_backend.cashflow.service.CashFlowService;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    @Test
    void shouldReturnInsufficientDataWhenHistoryIsEmptyForProjection() {
        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(Collections.emptyList());

        CashFlowProjectionResponse response = cashFlowService.getTrendProjection(userId);

        assertNotNull(response);
        assertNull(response.getProjectedMonth());
        assertNull(response.getProjectedNetCashFlow());
        assertNull(response.getTrendDirection());
        assertNull(response.getConfidence());
        assertEquals(0, response.getHistoricalMonthsCount());
        assertEquals("Need at least 3 months of data for a trend", response.getMessage());
    }

    @Test
    void shouldReturnInsufficientDataWhenHistoryHasOneMonth() {
        MonthlyRecord m1 = createRecord("2026-08", "150000", "100000", "200000");
        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(m1));

        CashFlowProjectionResponse response = cashFlowService.getTrendProjection(userId);

        assertNotNull(response);
        assertNull(response.getProjectedMonth());
        assertNull(response.getProjectedNetCashFlow());
        assertEquals(1, response.getHistoricalMonthsCount());
        assertEquals("Need at least 3 months of data for a trend", response.getMessage());
    }

    @Test
    void shouldReturnInsufficientDataWhenHistoryHasTwoMonths() {
        MonthlyRecord m2 = createRecord("2026-08", "150000", "100000", "200000");
        MonthlyRecord m1 = createRecord("2026-07", "140000", "90000", "150000");
        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(m2, m1));

        CashFlowProjectionResponse response = cashFlowService.getTrendProjection(userId);

        assertNotNull(response);
        assertNull(response.getProjectedMonth());
        assertNull(response.getProjectedNetCashFlow());
        assertEquals(2, response.getHistoricalMonthsCount());
        assertEquals("Need at least 3 months of data for a trend", response.getMessage());
    }

    @Test
    void shouldReturnValidProjectionForThreeMonthsHistory() {
        // Returned descending: 2026-08, 2026-07, 2026-06
        // Chronological net cash flows: 2026-06: 100k, 2026-07: 200k, 2026-08: 300k
        MonthlyRecord m3 = createRecord("2026-08", "400000", "100000", "500000"); // net 300k
        MonthlyRecord m2 = createRecord("2026-07", "300000", "100000", "300000"); // net 200k
        MonthlyRecord m1 = createRecord("2026-06", "200000", "100000", "200000"); // net 100k
        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(m3, m2, m1));

        CashFlowProjectionResponse response = cashFlowService.getTrendProjection(userId);

        assertNotNull(response);
        assertEquals("2026-09", response.getProjectedMonth());
        assertEquals(0, response.getProjectedNetCashFlow().compareTo(new BigDecimal("400000.00")));
        assertEquals("upward", response.getTrendDirection());
        assertEquals("reasonable", response.getConfidence());
        assertEquals(3, response.getHistoricalMonthsCount());
        assertNull(response.getMessage());
    }

    @Test
    void shouldReturnValidProjectionForSixMonthsHistory() {
        // 6 months descending: 2026-08 down to 2026-03
        // All net cash flows = 50000 (flat)
        List<MonthlyRecord> records = new ArrayList<>();
        for (int i = 8; i >= 3; i--) {
            records.add(createRecord("2026-0" + i, "150000", "100000", "200000"));
        }
        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(records);

        CashFlowProjectionResponse response = cashFlowService.getTrendProjection(userId);

        assertNotNull(response);
        assertEquals("2026-09", response.getProjectedMonth());
        assertEquals(0, response.getProjectedNetCashFlow().compareTo(new BigDecimal("50000.00")));
        assertEquals("flat", response.getTrendDirection());
        assertEquals("reasonable", response.getConfidence());
        assertEquals(6, response.getHistoricalMonthsCount());
        assertNull(response.getMessage());
    }

    @Test
    void shouldEnsureChronologicalOrderingBeforeRegression() {
        // Repository returns descending: Aug (net 300k), July (net 200k), June (net 100k)
        // If chronological: June (100k), July (200k), Aug (300k) -> positive slope
        // If wrong order (descending): June (300k)... -> negative slope
        MonthlyRecord mAug = createRecord("2026-08", "400000", "100000", "500000"); // 300k
        MonthlyRecord mJul = createRecord("2026-07", "300000", "100000", "300000"); // 200k
        MonthlyRecord mJun = createRecord("2026-06", "200000", "100000", "200000"); // 100k
        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(mAug, mJul, mJun));

        CashFlowProjectionResponse response = cashFlowService.getTrendProjection(userId);

        assertNotNull(response);
        assertEquals("upward", response.getTrendDirection());
        assertEquals(0, response.getProjectedNetCashFlow().compareTo(new BigDecimal("400000.00")));
    }

    @Test
    void shouldCalculateProjectedMonthAfterNormalMonthTransition() {
        MonthlyRecord m3 = createRecord("2026-08", "200000", "100000", "200000");
        MonthlyRecord m2 = createRecord("2026-07", "200000", "100000", "200000");
        MonthlyRecord m1 = createRecord("2026-06", "200000", "100000", "200000");
        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(m3, m2, m1));

        CashFlowProjectionResponse response = cashFlowService.getTrendProjection(userId);

        assertNotNull(response);
        assertEquals("2026-09", response.getProjectedMonth());
    }

    @Test
    void shouldCalculateProjectedMonthAfterYearRollover() {
        MonthlyRecord m3 = createRecord("2026-12", "200000", "100000", "200000");
        MonthlyRecord m2 = createRecord("2026-11", "200000", "100000", "200000");
        MonthlyRecord m1 = createRecord("2026-10", "200000", "100000", "200000");
        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(m3, m2, m1));

        CashFlowProjectionResponse response = cashFlowService.getTrendProjection(userId);

        assertNotNull(response);
        assertEquals("2027-01", response.getProjectedMonth());
    }

    @Test
    void shouldCalculateFinancialValuesUsingNetEqualToInflowMinusOutflow() {
        MonthlyRecord m3 = createRecord("2026-08", "300000", "100000", "200000"); // net 200k
        MonthlyRecord m2 = createRecord("2026-07", "250000", "100000", "200000"); // net 150k
        MonthlyRecord m1 = createRecord("2026-06", "200000", "100000", "200000"); // net 100k
        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(m3, m2, m1));

        CashFlowProjectionResponse response = cashFlowService.getTrendProjection(userId);

        assertNotNull(response);
        // June: 100k, July: 150k, Aug: 200k -> slope = 50k, projection at Sept = 250k
        assertEquals(0, response.getProjectedNetCashFlow().compareTo(new BigDecimal("250000.00")));
    }

    @Test
    void shouldThrowExceptionWhenUserIdIsNullForProjection() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> cashFlowService.getTrendProjection(null)
                );

        assertEquals("User ID is required", exception.getMessage());
        verify(monthlyRecordRepository, never()).findTop6ByUserIdOrderByMonthDesc(any());
    }

    @Test
    void shouldHandleNullInflowOrOutflowGracefullyDuringProjection() {
        MonthlyRecord m3 = new MonthlyRecord();
        m3.setUserId(userId);
        m3.setMonth("2026-08");
        m3.setCashInflow(new BigDecimal("200000"));
        m3.setCashOutflow(null); // null outflow treated as 0 -> net = 200000

        MonthlyRecord m2 = new MonthlyRecord();
        m2.setUserId(userId);
        m2.setMonth("2026-07");
        m2.setCashInflow(null); // null inflow treated as 0
        m2.setCashOutflow(new BigDecimal("50000")); // net = -50000

        MonthlyRecord m1 = new MonthlyRecord();
        m1.setUserId(userId);
        m1.setMonth("2026-06");
        m1.setCashInflow(new BigDecimal("100000"));
        m1.setCashOutflow(new BigDecimal("50000")); // net = 50000

        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(m3, m2, m1));

        CashFlowProjectionResponse response = cashFlowService.getTrendProjection(userId);

        assertNotNull(response);
        assertNotNull(response.getProjectedNetCashFlow());
        assertEquals("2026-09", response.getProjectedMonth());
    }

    @Test
    void shouldProperlyAccountForCalendarGapsInHistoricalRecords() {
        // Records descending: 2026-04 (net 400k), 2026-02 (net 200k), 2026-01 (net 100k)
        // 2026-03 is missing
        // Projection month should be 2026-05, value should be 500k
        MonthlyRecord mApr = createRecord("2026-04", "500000", "100000", "400000"); // net 400k
        MonthlyRecord mFeb = createRecord("2026-02", "300000", "100000", "200000"); // net 200k
        MonthlyRecord mJan = createRecord("2026-01", "200000", "100000", "100000"); // net 100k

        when(monthlyRecordRepository.findTop6ByUserIdOrderByMonthDesc(userId))
                .thenReturn(List.of(mApr, mFeb, mJan));

        CashFlowProjectionResponse response = cashFlowService.getTrendProjection(userId);

        assertNotNull(response);
        assertEquals("2026-05", response.getProjectedMonth());
        assertEquals(0, response.getProjectedNetCashFlow().compareTo(new BigDecimal("500000.00")));
        assertEquals("upward", response.getTrendDirection());
        assertEquals("reasonable", response.getConfidence());
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
