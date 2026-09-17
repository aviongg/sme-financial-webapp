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

    @Test
    void shouldSaveMonthlyRecordWhenCogsIsNull() {
        MonthlyRecord record = validRecord();
        record.setCogs(null);

        when(monthlyRecordRepository
                .findByUserIdAndMonth(userId, "2026-08"))
                .thenReturn(Optional.empty());

        when(monthlyRecordRepository.save(record))
                .thenReturn(record);

        MonthlyRecord result = monthlyRecordService.saveMonthlyRecord(record);

        assertNotNull(result);
        assertNull(result.getCogs());
        verify(monthlyRecordRepository).save(record);
    }

    @Test
    void shouldPreserveCogsWhenProvided() {
        MonthlyRecord record = validRecord();
        record.setCogs(new BigDecimal("75000"));

        when(monthlyRecordRepository
                .findByUserIdAndMonth(userId, "2026-08"))
                .thenReturn(Optional.empty());

        when(monthlyRecordRepository.save(record))
                .thenReturn(record);

        MonthlyRecord result = monthlyRecordService.saveMonthlyRecord(record);

        assertNotNull(result);
        assertEquals(new BigDecimal("75000"), result.getCogs());
        verify(monthlyRecordRepository).save(record);
    }

    @Test
    void shouldRejectNegativeCogs() {
        MonthlyRecord record = validRecord();
        record.setCogs(new BigDecimal("-500"));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> monthlyRecordService.saveMonthlyRecord(record)
                );

        assertEquals("COGS cannot be negative", exception.getMessage());
        verify(monthlyRecordRepository, never()).save(any());
    }

    @Test
    void shouldReplaceExistingRecordFieldsOnManualEditWithoutSumming() {
        MonthlyRecord existing = new MonthlyRecord();
        existing.setUserId(userId);
        existing.setMonth("2026-08");
        existing.setCashInflow(new BigDecimal("100000"));
        existing.setCashOutflow(new BigDecimal("40000"));
        existing.setRevenue(new BigDecimal("120000"));
        existing.setCogs(new BigDecimal("50000"));
        existing.setOperatingExpenses(new BigDecimal("30000"));
        existing.setCashBalanceEom(new BigDecimal("60000"));
        existing.setReceivablesOutstanding(new BigDecimal("20000"));
        existing.setPayablesOutstanding(new BigDecimal("10000"));
        existing.setInventoryValue(new BigDecimal("15000"));
        existing.setLoanOutstanding(new BigDecimal("50000"));
        existing.setInterestExpense(new BigDecimal("5000"));
        existing.setFinancingType("none");

        // User edits the monthly record: incoming snapshot values
        MonthlyRecord incoming = new MonthlyRecord();
        incoming.setUserId(userId);
        incoming.setMonth("2026-08");
        incoming.setCashInflow(new BigDecimal("130000"));
        incoming.setCashOutflow(new BigDecimal("50000"));
        incoming.setRevenue(new BigDecimal("150000"));
        incoming.setCogs(null); // explicitly null
        incoming.setOperatingExpenses(new BigDecimal("35000"));
        incoming.setCashBalanceEom(new BigDecimal("80000"));
        incoming.setReceivablesOutstanding(new BigDecimal("25000"));
        incoming.setPayablesOutstanding(new BigDecimal("12000"));
        incoming.setInventoryValue(new BigDecimal("18000"));
        incoming.setLoanOutstanding(new BigDecimal("45000"));
        incoming.setInterestExpense(new BigDecimal("4500"));
        incoming.setFinancingType("conventional");

        when(monthlyRecordRepository
                .findByUserIdAndMonth(userId, "2026-08"))
                .thenReturn(Optional.of(existing));

        when(monthlyRecordRepository.save(existing))
                .thenReturn(existing);

        MonthlyRecord result = monthlyRecordService.saveMonthlyRecord(incoming);

        assertNotNull(result);
        // Verify EXACT replacement, NOT summation (+=)
        assertEquals(new BigDecimal("130000"), existing.getCashInflow());
        assertNotEquals(new BigDecimal("230000"), existing.getCashInflow());

        assertEquals(new BigDecimal("50000"), existing.getCashOutflow());
        assertNotEquals(new BigDecimal("90000"), existing.getCashOutflow());

        assertEquals(new BigDecimal("150000"), existing.getRevenue());
        assertNotEquals(new BigDecimal("270000"), existing.getRevenue());

        assertNull(existing.getCogs());

        assertEquals(new BigDecimal("35000"), existing.getOperatingExpenses());
        assertEquals(new BigDecimal("80000"), existing.getCashBalanceEom());
        assertEquals(new BigDecimal("25000"), existing.getReceivablesOutstanding());
        assertEquals(new BigDecimal("12000"), existing.getPayablesOutstanding());
        assertEquals(new BigDecimal("18000"), existing.getInventoryValue());
        assertEquals(new BigDecimal("45000"), existing.getLoanOutstanding());
        assertEquals(new BigDecimal("4500"), existing.getInterestExpense());
        assertEquals("conventional", existing.getFinancingType());

        verify(monthlyRecordRepository, times(1)).save(existing);
        verify(monthlyRecordRepository, never()).save(incoming);
    }

    @Test
    void shouldFindRecordById() {
        UUID recordId = UUID.randomUUID();
        MonthlyRecord record = validRecord();
        record.setId(recordId);

        when(monthlyRecordRepository.findById(recordId))
                .thenReturn(Optional.of(record));

        Optional<MonthlyRecord> result = monthlyRecordService.getRecordById(recordId);

        assertTrue(result.isPresent());
        assertEquals(recordId, result.get().getId());
        verify(monthlyRecordRepository).findById(recordId);
    }

    @Test
    void shouldReturnEmptyWhenRecordIdNotFound() {
        UUID recordId = UUID.randomUUID();

        when(monthlyRecordRepository.findById(recordId))
                .thenReturn(Optional.empty());

        Optional<MonthlyRecord> result = monthlyRecordService.getRecordById(recordId);

        assertTrue(result.isEmpty());
        verify(monthlyRecordRepository).findById(recordId);
    }

    @Test
    void shouldThrowWhenRecordIdIsNull() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> monthlyRecordService.getRecordById(null)
                );

        assertEquals("Record ID is required", exception.getMessage());
        verify(monthlyRecordRepository, never()).findById(any());
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