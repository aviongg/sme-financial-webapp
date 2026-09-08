package com.app.sme_health_backend;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonthlyRecordServiceTests {

    @Mock
    private MonthlyRecordRepository monthlyRecordRepository;

    private MonthlyRecordService monthlyRecordService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        monthlyRecordService =
                new MonthlyRecordService(monthlyRecordRepository);

        userId = UUID.randomUUID();
    }

    @Test
    void shouldCreateMonthlyRecord() {
        MonthlyRecord record = validRecord();

        when(monthlyRecordRepository
                .findByUserIdAndMonth(userId, "2026-08"))
                .thenReturn(Optional.empty());

        when(monthlyRecordRepository.save(record))
                .thenReturn(record);

        MonthlyRecord result =
                monthlyRecordService.saveMonthlyRecord(record);

        assertNotNull(result);
        verify(monthlyRecordRepository).save(record);
    }

    @Test
    void shouldUpdateExistingMonthlyRecordInsteadOfCreatingDuplicate() {
        MonthlyRecord existing = validRecord();
        MonthlyRecord incoming = validRecord();

        incoming.setRevenue(new BigDecimal("250000"));

        when(monthlyRecordRepository
                .findByUserIdAndMonth(userId, "2026-08"))
                .thenReturn(Optional.of(existing));

        when(monthlyRecordRepository.save(existing))
                .thenReturn(existing);

        MonthlyRecord result =
                monthlyRecordService.saveMonthlyRecord(incoming);

        assertNotNull(result);
        assertEquals(
                new BigDecimal("250000"),
                existing.getRevenue()
        );

        verify(monthlyRecordRepository, times(1)).save(existing);
    }

    @Test
    void shouldRejectNegativeRevenue() {
        MonthlyRecord record = validRecord();
        record.setRevenue(new BigDecimal("-1"));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> monthlyRecordService.saveMonthlyRecord(record)
                );

        assertEquals(
                "Revenue cannot be negative",
                exception.getMessage()
        );

        verify(monthlyRecordRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidMonth() {
        MonthlyRecord record = validRecord();
        record.setMonth("2026-13");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> monthlyRecordService.saveMonthlyRecord(record)
                );

        assertEquals(
                "Month must be in YYYY-MM format",
                exception.getMessage()
        );

        verify(monthlyRecordRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidFinancingType() {
        MonthlyRecord record = validRecord();
        record.setFinancingType("invalid");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> monthlyRecordService.saveMonthlyRecord(record)
                );

        assertEquals(
                "financingType must be one of: none, conventional, islamic",
                exception.getMessage()
        );

        verify(monthlyRecordRepository, never()).save(any());
    }

    @Test
    void shouldPreventDuplicateCreationForSameUserAndMonth() {
        MonthlyRecord existing = validRecord();
        MonthlyRecord incoming = validRecord();

        when(monthlyRecordRepository
                .findByUserIdAndMonth(userId, "2026-08"))
                .thenReturn(Optional.of(existing));

        when(monthlyRecordRepository.save(existing))
                .thenReturn(existing);

        monthlyRecordService.saveMonthlyRecord(incoming);

        verify(monthlyRecordRepository, times(1))
                .findByUserIdAndMonth(userId, "2026-08");

        verify(monthlyRecordRepository, times(1))
                .save(existing);

        verify(monthlyRecordRepository, never())
                .save(incoming);
    }

    private MonthlyRecord validRecord() {
        MonthlyRecord record = new MonthlyRecord();

        record.setUserId(userId);
        record.setMonth("2026-08");

        record.setCashInflow(new BigDecimal("100000"));
        record.setCashOutflow(new BigDecimal("50000"));
        record.setRevenue(new BigDecimal("150000"));
        record.setCogs(new BigDecimal("60000"));
        record.setOperatingExpenses(new BigDecimal("30000"));
        record.setCashBalanceEom(new BigDecimal("70000"));

        record.setReceivablesOutstanding(new BigDecimal("20000"));
        record.setPayablesOutstanding(new BigDecimal("10000"));
        record.setInventoryValue(new BigDecimal("15000"));
        record.setLoanOutstanding(new BigDecimal("50000"));
        record.setInterestExpense(new BigDecimal("5000"));

        record.setFinancingType("none");

        return record;
    }
}