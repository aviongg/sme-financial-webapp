package com.app.sme_health_backend.scoring.service;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.scoring.calculator.*;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.repository.ScoreResultRepository;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
public class ScoringService {

    private static final BigDecimal WEIGHT_CASHFLOW = new BigDecimal("0.30");
    private static final BigDecimal WEIGHT_PROFITABILITY = new BigDecimal("0.25");
    private static final BigDecimal WEIGHT_REPAYMENT = new BigDecimal("0.20");
    private static final BigDecimal WEIGHT_TREND = new BigDecimal("0.15");
    private static final BigDecimal WEIGHT_COMPLIANCE = new BigDecimal("0.10");

    private static final BigDecimal BAND_STRONG_THRESHOLD = new BigDecimal("80.00");
    private static final BigDecimal BAND_STABLE_THRESHOLD = new BigDecimal("60.00");
    private static final BigDecimal BAND_NEEDS_ATTENTION_THRESHOLD = new BigDecimal("40.00");

    private static final List<String> COMPONENT_PRIORITY = List.of(
            "cashflow",
            "profitability",
            "repayment",
            "trend",
            "compliance"
    );

    private final BusinessProfileRepository businessProfileRepository;
    private final MonthlyRecordRepository monthlyRecordRepository;
    private final ScoreResultRepository scoreResultRepository;

    private final CashFlowStabilityCalculator cashFlowStabilityCalculator;
    private final ProfitabilityEfficiencyCalculator profitabilityEfficiencyCalculator;
    private final RepaymentCalculator repaymentCalculator;
    private final TrendCalculator trendCalculator;
    private final ComplianceCalculator complianceCalculator;

    public ScoringService(
            BusinessProfileRepository businessProfileRepository,
            MonthlyRecordRepository monthlyRecordRepository,
            ScoreResultRepository scoreResultRepository,
            CashFlowStabilityCalculator cashFlowStabilityCalculator,
            ProfitabilityEfficiencyCalculator profitabilityEfficiencyCalculator,
            RepaymentCalculator repaymentCalculator,
            TrendCalculator trendCalculator,
            ComplianceCalculator complianceCalculator
    ) {
        this.businessProfileRepository = businessProfileRepository;
        this.monthlyRecordRepository = monthlyRecordRepository;
        this.scoreResultRepository = scoreResultRepository;
        this.cashFlowStabilityCalculator = cashFlowStabilityCalculator;
        this.profitabilityEfficiencyCalculator = profitabilityEfficiencyCalculator;
        this.repaymentCalculator = repaymentCalculator;
        this.trendCalculator = trendCalculator;
        this.complianceCalculator = complianceCalculator;
    }

    @Transactional
    public ScoreResult calculateAndSaveScore(UUID userId, String month) {
        validateInputs(userId, month);

        BusinessProfile profile = businessProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Business profile not found for this user"));

        MonthlyRecord targetRecord = monthlyRecordRepository.findByUserIdAndMonth(userId, month)
                .orElseThrow(() -> new ResourceNotFoundException("Monthly record not found for user and month: " + month));

        // Load all records up to target month in descending chronological order
        List<MonthlyRecord> allRecords = monthlyRecordRepository.findByUserIdOrderByMonthDesc(userId);
        List<MonthlyRecord> history = allRecords.stream()
                .filter(r -> r.getMonth().compareTo(month) <= 0)
                .sorted(Comparator.comparing(MonthlyRecord::getMonth).reversed())
                .toList();

        // Calculate the five components
        BigDecimal cashflow = cashFlowStabilityCalculator.calculate(history);
        BigDecimal profitability = profitabilityEfficiencyCalculator.calculate(targetRecord, profile);
        BigDecimal repayment = repaymentCalculator.calculate(profile);
        BigDecimal trend = trendCalculator.calculate(history);
        BigDecimal compliance = complianceCalculator.calculate(profile);

        ComponentScoresDto componentScores = new ComponentScoresDto(
                cashflow,
                profitability,
                repayment,
                trend,
                compliance
        );

        // Calculate available weight and composite score
        Map<String, BigDecimal> scoreMap = componentScores.toMap();
        Map<String, BigDecimal> weightMap = Map.of(
                "cashflow", WEIGHT_CASHFLOW,
                "profitability", WEIGHT_PROFITABILITY,
                "repayment", WEIGHT_REPAYMENT,
                "trend", WEIGHT_TREND,
                "compliance", WEIGHT_COMPLIANCE
        );

        BigDecimal availableWeight = BigDecimal.ZERO;
        BigDecimal weightedTotal = BigDecimal.ZERO;
        Map<String, BigDecimal> validScores = new LinkedHashMap<>();

        for (Map.Entry<String, BigDecimal> entry : scoreMap.entrySet()) {
            String component = entry.getKey();
            BigDecimal score = entry.getValue();
            if (score != null) {
                BigDecimal weight = weightMap.get(component);
                availableWeight = availableWeight.add(weight);
                weightedTotal = weightedTotal.add(score.multiply(weight));
                validScores.put(component, score);
            }
        }

        if (availableWeight.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Insufficient financial data to calculate financial health score");
        }

        BigDecimal compositeScore = weightedTotal.divide(availableWeight, 2, RoundingMode.HALF_UP);
        if (compositeScore.compareTo(BigDecimal.ZERO) < 0) {
            compositeScore = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else if (compositeScore.compareTo(new BigDecimal("100.00")) > 0) {
            compositeScore = new BigDecimal("100.00");
        }

        String band = determineBand(compositeScore);
        String weakestComponent = determineWeakestComponent(validScores);
        BigDecimal dataCompleteness = availableWeight.setScale(2, RoundingMode.HALF_UP);

        ScoreResult result = scoreResultRepository.findByUserIdAndMonth(userId, month)
                .orElseGet(() -> {
                    ScoreResult newResult = new ScoreResult();
                    newResult.setUserId(userId);
                    newResult.setMonth(month);
                    return newResult;
                });

        result.setCompositeScore(compositeScore);
        result.setBand(band);
        result.setComponentScores(componentScores);
        result.setWeakestComponent(weakestComponent);
        result.setDataCompleteness(dataCompleteness);
        result.setComputedAt(LocalDateTime.now());

        return scoreResultRepository.save(result);
    }

    @Transactional(readOnly = true)
    public Optional<ScoreResult> getScore(UUID userId, String month) {
        validateInputs(userId, month);
        return scoreResultRepository.findByUserIdAndMonth(userId, month);
    }

    @Transactional(readOnly = true)
    public Optional<ScoreResult> getLatestScore(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        return scoreResultRepository.findFirstByUserIdOrderByMonthDesc(userId);
    }

    private void validateInputs(UUID userId, String month) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (month == null || month.isBlank()) {
            throw new IllegalArgumentException("Month is required");
        }
        try {
            YearMonth.parse(month);
        } catch (Exception e) {
            throw new IllegalArgumentException("Month must be in YYYY-MM format");
        }
    }

    public String determineBand(BigDecimal compositeScore) {
        if (compositeScore == null) {
            return "At Risk";
        }
        if (compositeScore.compareTo(BAND_STRONG_THRESHOLD) >= 0) {
            return "Strong";
        }
        if (compositeScore.compareTo(BAND_STABLE_THRESHOLD) >= 0) {
            return "Stable";
        }
        if (compositeScore.compareTo(BAND_NEEDS_ATTENTION_THRESHOLD) >= 0) {
            return "Needs Attention";
        }
        return "At Risk";
    }

    public String determineWeakestComponent(Map<String, BigDecimal> validScores) {
        if (validScores == null || validScores.isEmpty()) {
            return "none";
        }

        BigDecimal minScore = Collections.min(validScores.values());

        for (String component : COMPONENT_PRIORITY) {
            BigDecimal score = validScores.get(component);
            if (score != null && score.compareTo(minScore) == 0) {
                return component;
            }
        }

        return "none";
    }
}
