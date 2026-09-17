package com.app.sme_health_backend.search.service;

import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.repository.InsightRepository;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.search.dto.SearchResultResponse;
import com.app.sme_health_backend.search.ranker.SearchResultRanker;
import com.app.sme_health_backend.search.ranker.SearchResultRanker.Candidate;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SearchService {

    private static final int MAX_RESULTS_CAP = 20;

    private static final String[] URDU_MONTH_NAMES = {
            "", "جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون",
            "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر"
    };

    private final BusinessProfileRepository profileRepository;
    private final MonthlyRecordRepository recordRepository;
    private final InsightRepository insightRepository;
    private final RecommendationRepository recommendationRepository;
    private final SearchResultRanker ranker;

    public SearchService(
            BusinessProfileRepository profileRepository,
            MonthlyRecordRepository recordRepository,
            InsightRepository insightRepository,
            RecommendationRepository recommendationRepository,
            SearchResultRanker ranker
    ) {
        this.profileRepository = profileRepository;
        this.recordRepository = recordRepository;
        this.insightRepository = insightRepository;
        this.recommendationRepository = recommendationRepository;
        this.ranker = ranker;
    }

    /**
     * Executes read-only global search across user-scoped records, insights, and recommendations.
     * Blank or whitespace queries return [] immediately without querying any repository.
     */
    public List<SearchResultResponse> search(UUID userId, String query, String type) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        if (!profileRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Business profile not found for user: " + userId);
        }

        String filter = (type == null || type.isBlank()) ? "all" : type.trim().toLowerCase();

        // If filter is explicitly "document", return empty list as document module/table does not exist yet
        if ("document".equals(filter)) {
            return Collections.emptyList();
        }

        List<Candidate> candidates = new ArrayList<>();

        // 1. Monthly Records (type = "transaction")
        if ("all".equals(filter) || "transaction".equals(filter)) {
            List<MonthlyRecord> records = recordRepository.findByUserIdOrderByMonthDesc(userId);
            for (MonthlyRecord record : records) {
                candidates.add(buildRecordCandidate(record));
            }
        }

        // 2. Insights (type = "insight")
        if ("all".equals(filter) || "insight".equals(filter)) {
            List<Insight> insights = insightRepository.findByUserIdOrderByCreatedAtDesc(userId);
            for (Insight insight : insights) {
                candidates.add(buildInsightCandidate(insight));
            }
        }

        // 3. Recommendations (type = "recommendation")
        if ("all".equals(filter) || "recommendation".equals(filter)) {
            List<Recommendation> recommendations = recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId);
            for (Recommendation recommendation : recommendations) {
                candidates.add(buildRecommendationCandidate(recommendation));
            }
        }

        // 4. Rank candidates and apply the 20-result cap AFTER ranking
        return ranker.rankAndCap(candidates, query, MAX_RESULTS_CAP);
    }

    private Candidate buildRecordCandidate(MonthlyRecord record) {
        String monthStr = record.getMonth();
        String enMonthName = "";
        String enMonthShort = "";
        String urduMonthName = "";
        String yearStr = "";

        try {
            YearMonth ym = YearMonth.parse(monthStr);
            enMonthName = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            enMonthShort = ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            yearStr = String.valueOf(ym.getYear());
            int monthVal = ym.getMonthValue();
            if (monthVal >= 1 && monthVal <= 12) {
                urduMonthName = URDU_MONTH_NAMES[monthVal];
            }
        } catch (Exception ignored) {
        }

        String title = enMonthName.isEmpty()
                ? monthStr + " Monthly Record"
                : enMonthName + " " + yearStr + " Monthly Record";

        String formattedRevenue = formatPkr(record.getRevenue());
        String formattedInflow = formatPkr(record.getCashInflow());
        String formattedOutflow = formatPkr(record.getCashOutflow());
        String formattedExpenses = formatPkr(record.getOperatingExpenses());
        String formattedCashBalance = formatPkr(record.getCashBalanceEom());

        String description = "Revenue: PKR " + formattedRevenue
                + " • Inflow: PKR " + formattedInflow
                + " • Outflow: PKR " + formattedOutflow;

        SearchResultResponse response = new SearchResultResponse(
                record.getId() != null ? record.getId().toString() : "",
                title,
                description,
                "transaction",
                monthStr,
                record.getRevenue(),
                "Monthly Financial Record",
                "/records/" + (record.getId() != null ? record.getId() : monthStr),
                "Official"
        );

        List<String> primaryTerms = new ArrayList<>();
        // Month code
        primaryTerms.add(monthStr);
        if (!yearStr.isEmpty()) {
            primaryTerms.add(yearStr);
        }
        // English month names & abbreviations
        if (!enMonthName.isEmpty()) {
            primaryTerms.add(enMonthName);
            primaryTerms.add(enMonthName + " " + yearStr);
        }
        if (!enMonthShort.isEmpty()) {
            primaryTerms.add(enMonthShort);
            primaryTerms.add(enMonthShort + " " + yearStr);
        }
        // Urdu month name
        if (!urduMonthName.isEmpty()) {
            primaryTerms.add(urduMonthName);
        }
        // Financing type
        if (record.getFinancingType() != null && !record.getFinancingType().isBlank()) {
            primaryTerms.add(record.getFinancingType());
        }
        // Financial vocabulary
        primaryTerms.addAll(Arrays.asList(
                "Revenue", "revenue",
                "Inflow", "inflow",
                "Outflow", "outflow",
                "Expenses", "expenses",
                "Cash Balance", "cash balance",
                "Operating Expenses"
        ));

        List<String> searchableText = Arrays.asList(
                description,
                "Expenses: PKR " + formattedExpenses,
                "Cash Balance: PKR " + formattedCashBalance,
                "Financing: " + record.getFinancingType()
        );

        return new Candidate(response, primaryTerms, searchableText);
    }

    private Candidate buildInsightCandidate(Insight insight) {
        String category = insight.getCategory() != null ? insight.getCategory() : "Financial";
        String title = category + " Insight";

        SearchResultResponse response = new SearchResultResponse(
                insight.getId() != null ? insight.getId().toString() : "",
                title,
                insight.getText(),
                "insight",
                insight.getMonth(),
                null,
                category,
                "/health/components",
                insight.getPriority()
        );

        List<String> primaryTerms = new ArrayList<>();
        primaryTerms.add(category);
        if (insight.getMonth() != null) {
            primaryTerms.add(insight.getMonth());
        }
        if (insight.getPriority() != null) {
            primaryTerms.add(insight.getPriority());
        }
        primaryTerms.add("Insight");
        primaryTerms.add("insight");

        List<String> searchableText = Arrays.asList(
                title,
                insight.getText() != null ? insight.getText() : "",
                category
        );

        return new Candidate(response, primaryTerms, searchableText);
    }

    private Candidate buildRecommendationCandidate(Recommendation recommendation) {
        String category = recommendation.getCategory() != null ? recommendation.getCategory() : "Financial";
        String title = category + " Action Item";

        SearchResultResponse response = new SearchResultResponse(
                recommendation.getId() != null ? recommendation.getId().toString() : "",
                title,
                recommendation.getText(),
                "recommendation",
                recommendation.getMonth(),
                null,
                category,
                "/",
                recommendation.getPriority()
        );

        List<String> primaryTerms = new ArrayList<>();
        primaryTerms.add(category);
        if (recommendation.getMonth() != null) {
            primaryTerms.add(recommendation.getMonth());
        }
        if (recommendation.getPriority() != null) {
            primaryTerms.add(recommendation.getPriority());
        }
        primaryTerms.add("Recommendation");
        primaryTerms.add("recommendation");
        primaryTerms.add("Action Item");

        List<String> searchableText = Arrays.asList(
                title,
                recommendation.getText() != null ? recommendation.getText() : "",
                category
        );

        return new Candidate(response, primaryTerms, searchableText);
    }

    private String formatPkr(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return String.format(Locale.US, "%,d", amount.longValue());
    }
}
