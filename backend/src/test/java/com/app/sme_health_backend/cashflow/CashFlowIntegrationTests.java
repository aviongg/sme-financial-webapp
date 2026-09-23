package com.app.sme_health_backend.cashflow;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class CashFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MonthlyRecordRepository monthlyRecordRepository;

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    @Autowired
    private com.app.sme_health_backend.identity.repository.BusinessRepository businessRepository;

    private UUID createTestUserWithProfile() {
        UUID userId = UUID.randomUUID();
        businessRepository.save(new com.app.sme_health_backend.identity.entity.Business(userId, "ACTIVE"));
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setBusinessType("retail");
        profile.setLanguagePreference("en");
        profile.setWhatsappOptIn(false);
        profile.setCreatedAt(LocalDateTime.now());
        businessProfileRepository.save(profile);
        return userId;
    }

    private void createRecord(UUID userId, String month, String inflow, String outflow, String balance) {
        MonthlyRecord record = new MonthlyRecord();
        record.setUserId(userId);
        record.setMonth(month);
        record.setCashInflow(new BigDecimal(inflow));
        record.setCashOutflow(new BigDecimal(outflow));
        record.setRevenue(new BigDecimal(inflow));
        record.setOperatingExpenses(new BigDecimal("30000"));
        record.setCashBalanceEom(new BigDecimal(balance));
        record.setFinancingType("none");
        record.setUpdatedAt(LocalDateTime.now());
        monthlyRecordRepository.save(record);
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoRecords() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/cashflow/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturnChronologicalCashFlowSeriesForUser() throws Exception {
        UUID userId = createTestUserWithProfile();

        // Insert in non-chronological order to test database ordering
        createRecord(userId, "2026-05", "150000", "100000", "200000");
        createRecord(userId, "2026-03", "120000", "80000", "130000");
        createRecord(userId, "2026-04", "135000", "90000", "160000");

        mockMvc.perform(get("/api/cashflow/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // Ascending chronological order: 2026-03, 2026-04, 2026-05
                .andExpect(jsonPath("$[0].month").value("2026-03"))
                .andExpect(jsonPath("$[0].inflow").value(120000))
                .andExpect(jsonPath("$[0].outflow").value(80000))
                .andExpect(jsonPath("$[0].net").value(40000))
                .andExpect(jsonPath("$[0].runningBalance").value(130000))
                .andExpect(jsonPath("$[1].month").value("2026-04"))
                .andExpect(jsonPath("$[1].inflow").value(135000))
                .andExpect(jsonPath("$[1].outflow").value(90000))
                .andExpect(jsonPath("$[1].net").value(45000))
                .andExpect(jsonPath("$[1].runningBalance").value(160000))
                .andExpect(jsonPath("$[2].month").value("2026-05"))
                .andExpect(jsonPath("$[2].inflow").value(150000))
                .andExpect(jsonPath("$[2].outflow").value(100000))
                .andExpect(jsonPath("$[2].net").value(50000))
                .andExpect(jsonPath("$[2].runningBalance").value(200000));
    }

    @Test
    void shouldReturnAtMostLatestSixMonthsWhenMoreThanSixExist() throws Exception {
        UUID userId = createTestUserWithProfile();

        // Insert 8 months: 2026-01 through 2026-08
        createRecord(userId, "2026-01", "100000", "60000", "40000");
        createRecord(userId, "2026-02", "110000", "65000", "50000");
        createRecord(userId, "2026-03", "120000", "70000", "60000");
        createRecord(userId, "2026-04", "130000", "75000", "70000");
        createRecord(userId, "2026-05", "140000", "80000", "80000");
        createRecord(userId, "2026-06", "150000", "85000", "90000");
        createRecord(userId, "2026-07", "160000", "90000", "100000");
        createRecord(userId, "2026-08", "170000", "95000", "110000");

        mockMvc.perform(get("/api/cashflow/{userId}", userId))
                .andExpect(status().isOk())
                // Exactly 6 latest months
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].month").value("2026-03"))
                .andExpect(jsonPath("$[1].month").value("2026-04"))
                .andExpect(jsonPath("$[2].month").value("2026-05"))
                .andExpect(jsonPath("$[3].month").value("2026-06"))
                .andExpect(jsonPath("$[4].month").value("2026-07"))
                .andExpect(jsonPath("$[5].month").value("2026-08"));
    }

    @Test
    void shouldPreserveZeroOutflowAndCalculateNetEqualToInflow() throws Exception {
        UUID userId = createTestUserWithProfile();

        createRecord(userId, "2026-08", "100000", "0", "150000");

        mockMvc.perform(get("/api/cashflow/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].inflow").value(100000))
                .andExpect(jsonPath("$[0].outflow").value(0))
                .andExpect(jsonPath("$[0].net").value(100000))
                .andExpect(jsonPath("$[0].runningBalance").value(150000));
    }

    @Test
    void shouldEnforceUserIsolationBetweenDifferentUsers() throws Exception {
        UUID userA = createTestUserWithProfile();
        UUID userB = createTestUserWithProfile();

        createRecord(userA, "2026-07", "100000", "50000", "80000");
        createRecord(userA, "2026-08", "120000", "60000", "90000");

        createRecord(userB, "2026-08", "999999", "888888", "777777");

        // Query user A
        mockMvc.perform(get("/api/cashflow/{userId}", userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].month").value("2026-07"))
                .andExpect(jsonPath("$[0].inflow").value(100000))
                .andExpect(jsonPath("$[1].month").value("2026-08"))
                .andExpect(jsonPath("$[1].inflow").value(120000));

        // Query user B
        mockMvc.perform(get("/api/cashflow/{userId}", userB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].month").value("2026-08"))
                .andExpect(jsonPath("$[0].inflow").value(999999))
                .andExpect(jsonPath("$[0].outflow").value(888888));
    }
}
