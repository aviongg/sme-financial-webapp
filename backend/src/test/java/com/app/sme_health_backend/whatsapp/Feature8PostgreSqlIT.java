package com.app.sme_health_backend.whatsapp;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.repository.ScoreResultRepository;
import com.app.sme_health_backend.whatsapp.dto.WhatsAppDeliveryResponse;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDelivery;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import com.app.sme_health_backend.whatsapp.repository.WhatsAppDeliveryRepository;
import com.app.sme_health_backend.whatsapp.service.WhatsAppDeliveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class Feature8PostgreSqlIT {

    @Autowired
    private WhatsAppDeliveryRepository deliveryRepository;

    @Autowired
    private BusinessProfileRepository profileRepository;

    @Autowired
    private ScoreResultRepository scoreResultRepository;

    @Autowired
    private WhatsAppDeliveryService deliveryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID testUserId;
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdScoreIds = new ArrayList<>();
    private final List<UUID> createdDeliveryIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        createdUserIds.add(testUserId);
    }

    @AfterEach
    void tearDown() {
        for (UUID deliveryId : createdDeliveryIds) {
            try {
                deliveryRepository.deleteById(deliveryId);
            } catch (Exception ignored) {}
        }
        for (UUID scoreId : createdScoreIds) {
            try {
                scoreResultRepository.deleteById(scoreId);
            } catch (Exception ignored) {}
        }
        for (UUID userId : createdUserIds) {
            try {
                jdbcTemplate.update("DELETE FROM whatsapp_deliveries WHERE user_id = ?", userId);
                jdbcTemplate.update("DELETE FROM recommendations WHERE user_id = ?", userId);
                jdbcTemplate.update("DELETE FROM insights WHERE user_id = ?", userId);
                jdbcTemplate.update("DELETE FROM score_results WHERE user_id = ?", userId);
                profileRepository.deleteById(userId);
            } catch (Exception ignored) {}
        }
    }

    @Test
    void shouldVerifyV8SchemaAndConstraintsInRealPostgreSql() {
        // Verify business_profiles.whatsapp_opted_in_at column exists
        Integer optInColExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_name = 'business_profiles' AND column_name = 'whatsapp_opted_in_at'",
                Integer.class
        );
        assertEquals(1, optInColExists, "whatsapp_opted_in_at column must exist in business_profiles");

        // Verify whatsapp_deliveries table exists
        Integer tableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'whatsapp_deliveries'",
                Integer.class
        );
        assertEquals(1, tableExists, "whatsapp_deliveries table must exist");

        // Verify unique constraint on (user_id, delivery_cycle)
        Integer uniqueConstraintExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                        "WHERE table_name = 'whatsapp_deliveries' AND constraint_name = 'uq_whatsapp_deliveries_user_cycle'",
                Integer.class
        );
        assertEquals(1, uniqueConstraintExists, "uq_whatsapp_deliveries_user_cycle unique constraint must exist");
    }

    @Test
    void shouldExecuteEndToEndDeliveryAndEnforceDatabaseIdempotency() {
        // 1. Create opted-in business profile
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(testUserId);
        profile.setBusinessType("retail");
        profile.setLanguagePreference("en");
        profile.setWhatsappOptIn(true);
        profile.setWhatsappNumber("+923001234567");
        profile.setWhatsappOptedInAt(LocalDateTime.now());
        profile.setCreatedAt(LocalDateTime.now());
        profileRepository.saveAndFlush(profile);

        // 2. Create latest score result
        ScoreResult score = new ScoreResult();
        score.setUserId(testUserId);
        score.setMonth("2026-09");
        score.setCompositeScore(new BigDecimal("82.00"));
        score.setBand("STRONG");
        score.setWeakestComponent("cashflow");
        score.setComponentScores(new ComponentScoresDto(
                new BigDecimal("80"), new BigDecimal("85"), new BigDecimal("75"),
                new BigDecimal("85"), new BigDecimal("85")
        ));
        score.setDataCompleteness(new BigDecimal("1.00"));
        score.setComputedAt(LocalDateTime.now());
        ScoreResult savedScore = scoreResultRepository.saveAndFlush(score);
        createdScoreIds.add(savedScore.getId());

        // 3. Deliver weekly summary
        LocalDate targetDate = LocalDate.of(2026, 9, 23);
        Optional<WhatsAppDelivery> deliveryOpt = deliveryService.deliverWeeklySummary(testUserId, targetDate);

        assertTrue(deliveryOpt.isPresent(), "Summary delivery should succeed");
        WhatsAppDelivery delivery = deliveryOpt.get();
        createdDeliveryIds.add(delivery.getId());

        assertEquals(WhatsAppDeliveryStatus.SENT, delivery.getDeliveryStatus());
        assertEquals("2026-W39", delivery.getDeliveryCycle());
        assertEquals("+923001234567", delivery.getDestinationNumber());
        assertEquals("2026-09", delivery.getTargetMonth());
        assertNotNull(delivery.getSourceFingerprint());
        assertEquals(64, delivery.getSourceFingerprint().length());
        assertNotNull(delivery.getSentAt());
        assertNotNull(delivery.getProviderMessageId());

        // 4. Test Idempotency: second delivery in the same cycle must return the existing record without duplicate
        Optional<WhatsAppDelivery> secondDelivery = deliveryService.deliverWeeklySummary(testUserId, targetDate);
        assertTrue(secondDelivery.isPresent());
        assertEquals(delivery.getId(), secondDelivery.get().getId(), "Must return identical existing delivery row");

        // 5. Verify database unique constraint rejects manual duplicate
        WhatsAppDelivery duplicate = new WhatsAppDelivery();
        duplicate.setUserId(testUserId);
        duplicate.setTargetMonth("2026-09");
        duplicate.setSourceFingerprint("another-fp");
        duplicate.setDeliveryCycle("2026-W39");
        duplicate.setDestinationNumber("+923001234567");
        duplicate.setLanguage("en");
        duplicate.setTemplateName("tpl");
        duplicate.setProviderName("mock");
        duplicate.setDeliveryStatus(WhatsAppDeliveryStatus.PENDING);

        assertThrows(DataIntegrityViolationException.class, () -> {
            deliveryRepository.saveAndFlush(duplicate);
        }, "Database unique constraint (user_id, delivery_cycle) must prevent duplicate delivery rows");

        // 6. Verify query endpoints return masked phone numbers
        List<WhatsAppDeliveryResponse> userDeliveries = deliveryService.getDeliveriesForUser(testUserId);
        assertEquals(1, userDeliveries.size());
        assertEquals("+92300***4567", userDeliveries.get(0).maskedDestinationNumber());
    }

    @Test
    void shouldPersistIndeterminateStatusOnAmbiguousProviderTimeout() {
        // Create profile with number ending in 0000 to trigger indeterminate simulation in mock client
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(testUserId);
        profile.setBusinessType("trade");
        profile.setLanguagePreference("en");
        profile.setWhatsappOptIn(true);
        profile.setWhatsappNumber("+923001230000");
        profile.setWhatsappOptedInAt(LocalDateTime.now());
        profile.setCreatedAt(LocalDateTime.now());
        profileRepository.saveAndFlush(profile);

        ScoreResult score = new ScoreResult();
        score.setUserId(testUserId);
        score.setMonth("2026-09");
        score.setCompositeScore(new BigDecimal("68.00"));
        score.setBand("STABLE");
        score.setWeakestComponent("profitability");
        score.setComponentScores(new ComponentScoresDto(
                new BigDecimal("70"), new BigDecimal("65"), new BigDecimal("70"),
                new BigDecimal("65"), new BigDecimal("70")
        ));
        score.setDataCompleteness(new BigDecimal("1.00"));
        score.setComputedAt(LocalDateTime.now());
        ScoreResult savedScore = scoreResultRepository.saveAndFlush(score);
        createdScoreIds.add(savedScore.getId());

        LocalDate targetDate = LocalDate.of(2026, 9, 23);
        Optional<WhatsAppDelivery> deliveryOpt = deliveryService.deliverWeeklySummary(testUserId, targetDate);

        assertTrue(deliveryOpt.isPresent());
        WhatsAppDelivery delivery = deliveryOpt.get();
        createdDeliveryIds.add(delivery.getId());

        assertEquals(WhatsAppDeliveryStatus.INDETERMINATE, delivery.getDeliveryStatus());
        assertNotNull(delivery.getFailureReason());
        assertTrue(delivery.getFailureReason().contains("timeout"));
        assertNull(delivery.getSentAt());
    }
}
