package com.app.sme_health_backend;

import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * V52 transaction-driven monthly model: MonthlyRecordService is read-only. Manual monthly financial entry is refused
 * with a typed 409 so no caller can create a second source of truth next to the authoritative daily records.
 */
class MonthlyRecordServiceTests {

    private final MonthlyRecordRepository repository = mock(MonthlyRecordRepository.class);
    private final com.app.sme_health_backend.identity.repository.BusinessRepository businesses =
            mock(com.app.sme_health_backend.identity.repository.BusinessRepository.class);
    private MonthlyRecordService service;
    private final UUID businessId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MonthlyRecordService(repository, businesses);
    }

    @Test
    void manualMonthlyEntryIsRefusedAndPersistsNothing() {
        MonthlyRecord record = new MonthlyRecord();
        record.setUserId(businessId);
        record.setMonth("2026-03");
        record.setRevenue(new BigDecimal("1000.00"));

        DomainException refused = assertThrows(DomainException.class,
                () -> service.saveMonthlyRecord(record, UUID.randomUUID(), null));
        assertEquals("monthly_figures_derived", refused.code());
        assertEquals(409, refused.status());
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void readsRecordsForTheBusinessOnly() {
        MonthlyRecord record = new MonthlyRecord();
        record.setUserId(businessId);
        record.setMonth("2026-03");
        when(repository.findByUserIdOrderByMonthDesc(businessId)).thenReturn(List.of(record));
        when(repository.findByUserIdAndMonth(businessId, "2026-03")).thenReturn(Optional.of(record));

        assertEquals(1, service.getUserRecords(businessId).size());
        assertTrue(service.getMonthlyRecord(businessId, "2026-03").isPresent());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> service.getUserRecords(null));
        assertThrows(IllegalArgumentException.class, () -> service.getMonthlyRecord(businessId, "2026-13"));
        assertThrows(IllegalArgumentException.class, () -> service.getMonthlyRecord(businessId, " "));
        assertThrows(IllegalArgumentException.class, () -> service.getRecordById(null, businessId));
        assertThrows(IllegalArgumentException.class, () -> service.getRecordById(UUID.randomUUID(), null));
    }

    @Test
    void recordLookupIsTenantScoped() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, businessId)).thenReturn(Optional.empty());
        assertTrue(service.getRecordById(id, businessId).isEmpty());
        verify(repository).findByIdAndUserId(id, businessId);
    }
}
