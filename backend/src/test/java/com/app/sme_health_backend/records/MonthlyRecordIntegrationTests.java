package com.app.sme_health_backend.records;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MonthlyRecordIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MonthlyRecordRepository monthlyRecordRepository;

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    private UUID createTestUserWithProfile() {
        UUID userId = UUID.randomUUID();
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setBusinessType("retail");
        profile.setLanguagePreference("en");
        profile.setWhatsappOptIn(false);
        profile.setCreatedAt(LocalDateTime.now());
        businessProfileRepository.save(profile);
        return userId;
    }

    @Test
    void shouldPersistRecordWithNullCogsInDatabaseAndRetrieveIt() throws Exception {
        UUID userId = createTestUserWithProfile();

        String payload = """
                {
                  "userId": "%s",
                  "month": "2026-07",
                  "cashInflow": 500000,
                  "cashOutflow": 200000,
                  "revenue": 500000,
                  "cogs": null,
                  "operatingExpenses": 50000,
                  "cashBalanceEom": 300000,
                  "financingType": "none"
                }
                """.formatted(userId);

        mockMvc.perform(post("/api/records/monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.month").value("2026-07"))
                .andExpect(jsonPath("$.cogs").doesNotExist());

        // Direct DB verification
        Optional<MonthlyRecord> inDb = monthlyRecordRepository.findByUserIdAndMonth(userId, "2026-07");
        assertTrue(inDb.isPresent());
        assertNull(inDb.get().getCogs(), "Database should persist COGS as NULL, not 0");
        UUID recordId = inDb.get().getId();

        // Verify retrieval via GET by ID endpoint
        mockMvc.perform(get("/api/records/monthly/id/{id}", recordId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recordId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.cogs").doesNotExist());
    }

    @Test
    void shouldUpsertAndReplaceSnapshotValuesInDatabaseWithoutDuplicate() throws Exception {
        UUID userId = createTestUserWithProfile();

        // 1. Initial submission
        String initialPayload = """
                {
                  "userId": "%s",
                  "month": "2026-05",
                  "cashInflow": 100000,
                  "cashOutflow": 40000,
                  "revenue": 120000,
                  "cogs": 50000,
                  "operatingExpenses": 20000,
                  "cashBalanceEom": 60000,
                  "financingType": "none"
                }
                """.formatted(userId);

        mockMvc.perform(post("/api/records/monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialPayload))
                .andExpect(status().isCreated());

        // 2. Second submission for same (userId, month) - manual snapshot edit
        String updatedPayload = """
                {
                  "userId": "%s",
                  "month": "2026-05",
                  "cashInflow": 130000,
                  "cashOutflow": 50000,
                  "revenue": 150000,
                  "cogs": null,
                  "operatingExpenses": 35000,
                  "cashBalanceEom": 80000,
                  "financingType": "conventional"
                }
                """.formatted(userId);

        mockMvc.perform(post("/api/records/monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isCreated());

        // Verify only ONE record exists in DB for this user & month
        List<MonthlyRecord> records = monthlyRecordRepository.findByUserIdOrderByMonthDesc(userId);
        assertEquals(1, records.size(), "Should have exactly 1 record, not duplicate");

        MonthlyRecord persisted = records.getFirst();
        // Verifies replacement, NOT summation
        assertEquals(0, persisted.getCashInflow().compareTo(new BigDecimal("130000")));
        assertNotEquals(0, persisted.getCashInflow().compareTo(new BigDecimal("230000")));

        assertEquals(0, persisted.getCashOutflow().compareTo(new BigDecimal("50000")));
        assertNotEquals(0, persisted.getCashOutflow().compareTo(new BigDecimal("90000")));

        assertEquals(0, persisted.getRevenue().compareTo(new BigDecimal("150000")));
        assertNotEquals(0, persisted.getRevenue().compareTo(new BigDecimal("270000")));

        assertNull(persisted.getCogs(), "COGS should be null after replacement");
        assertEquals(0, persisted.getOperatingExpenses().compareTo(new BigDecimal("35000")));
        assertEquals(0, persisted.getCashBalanceEom().compareTo(new BigDecimal("80000")));
        assertEquals("conventional", persisted.getFinancingType());
    }

    @Test
    void shouldReturn404ForNonexistentRecordId() throws Exception {
        UUID nonexistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/records/monthly/id/{id}", nonexistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Monthly record not found with id: " + nonexistentId));
    }

    @Test
    void shouldReturn400ForMalformedRecordId() throws Exception {
        mockMvc.perform(get("/api/records/monthly/id/invalid-id-format"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid record ID format: invalid-id-format"));
    }

    @Test
    void shouldPersistRecordEvenWhenScoringCalculationFailsDueToInsufficientData() throws Exception {
        UUID userId = createTestUserWithProfile();

        // Revenue = 0, so profitability score cannot be calculated; single month, so trend cannot be calculated;
        // repayment cannot be calculated. Available weight = 0, scoring throws IllegalArgumentException: Insufficient financial data
        String payload = """
                {
                  "userId": "%s",
                  "month": "2026-08",
                  "cashInflow": 0,
                  "cashOutflow": 0,
                  "revenue": 0,
                  "operatingExpenses": 0,
                  "cashBalanceEom": 0,
                  "financingType": "none"
                }
                """.formatted(userId);

        // Record persistence MUST succeed regardless of score calculation failure
        mockMvc.perform(post("/api/records/monthly")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()));

        Optional<MonthlyRecord> saved = monthlyRecordRepository.findByUserIdAndMonth(userId, "2026-08");
        assertTrue(saved.isPresent());
    }
}
