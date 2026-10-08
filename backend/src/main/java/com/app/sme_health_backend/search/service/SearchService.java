package com.app.sme_health_backend.search.service;

import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.i18n.TranslationService;
import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.repository.InsightRepository;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.search.dto.SearchResultResponse;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.repository.ScoreResultRepository;
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
import java.util.Map;
import java.util.Set;
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
    private final UploadedDocumentRepository documentRepository;
    private final ScoreResultRepository scoreRepository;
    private final TranslationService translations;

    public SearchService(
            BusinessProfileRepository profileRepository,
            MonthlyRecordRepository recordRepository,
            InsightRepository insightRepository,
            RecommendationRepository recommendationRepository,
            SearchResultRanker ranker,
            UploadedDocumentRepository documentRepository,
            ScoreResultRepository scoreRepository,
            TranslationService translations
    ) {
        this.profileRepository = profileRepository;
        this.recordRepository = recordRepository;
        this.insightRepository = insightRepository;
        this.recommendationRepository = recommendationRepository;
        this.ranker = ranker;
        this.documentRepository = documentRepository;
        this.scoreRepository = scoreRepository;
        this.translations = translations;
    }

    /**
     * Read-only search across the active business's persisted resources. Original
     * filenames are decrypted through their existing converter; OCR text is never searched.
     * Blank or whitespace queries return [] immediately without querying any repository.
     */
    public List<SearchResultResponse> search(UUID userId, String query, String type, boolean includeDocuments) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String filter = (type == null || type.isBlank()) ? "all" : type.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("all", "transaction", "insight", "recommendation", "document", "score").contains(filter)) {
            throw new IllegalArgumentException("Unsupported search type");
        }
        if ("document".equals(filter) && !includeDocuments) {
            throw new org.springframework.security.access.AccessDeniedException("Document access is not permitted");
        }
        var profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Business profile not found"));
        String language = translations.resolveLanguage(profile.getLanguagePreference());

        List<Candidate> candidates = new ArrayList<>();

        // 1. Monthly Records (type = "transaction")
        if ("all".equals(filter) || "transaction".equals(filter)) {
            List<MonthlyRecord> records = recordRepository.findByUserIdOrderByMonthDesc(userId);
            for (MonthlyRecord record : records) {
                candidates.add(buildRecordCandidate(record, language));
            }
        }

        // 2. Insights (type = "insight")
        if ("all".equals(filter) || "insight".equals(filter)) {
            List<Insight> insights = insightRepository.findByUserIdOrderByCreatedAtDesc(userId);
            for (Insight insight : insights) {
                candidates.add(buildInsightCandidate(insight, language));
            }
        }

        // 3. Recommendations (type = "recommendation")
        if ("all".equals(filter) || "recommendation".equals(filter)) {
            List<Recommendation> recommendations = recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId);
            for (Recommendation recommendation : recommendations) {
                candidates.add(buildRecommendationCandidate(recommendation, language));
            }
        }

        if (includeDocuments && ("all".equals(filter) || "document".equals(filter))) {
            for (UploadedDocument document : documentRepository.findByUserIdOrderByUploadTimestampDesc(userId)) {
                candidates.add(buildDocumentCandidate(document, language));
            }
        }
        if ("all".equals(filter) || "score".equals(filter)) {
            for (ScoreResult score : scoreRepository.findByUserIdOrderByMonthDesc(userId)) {
                candidates.add(buildScoreCandidate(score, language));
            }
        }

        // Rank all permitted resource types together before applying the result cap.
        return ranker.rankAndCap(candidates, query, MAX_RESULTS_CAP);
    }

    private Candidate buildRecordCandidate(MonthlyRecord record, String language) {
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

        String monthLabel = "ur".equals(language) && !urduMonthName.isEmpty()
                ? urduMonthName + " " + yearStr : enMonthName.isEmpty() ? monthStr : enMonthName + " " + yearStr;
        String title = translations.translate(language, "search.record.title", Map.of("month", monthLabel));

        String formattedRevenue = formatPkr(record.getRevenue());
        String formattedInflow = formatPkr(record.getCashInflow());
        String formattedOutflow = formatPkr(record.getCashOutflow());
        String formattedExpenses = formatPkr(record.getOperatingExpenses());
        String formattedCashBalance = formatPkr(record.getCashBalanceEom());

        String description = translations.translate(language, "search.record.description", Map.of(
                "revenue", formattedRevenue, "inflow", formattedInflow, "outflow", formattedOutflow));

        SearchResultResponse response = new SearchResultResponse(
                record.getId() != null ? record.getId().toString() : "",
                title,
                description,
                "transaction",
                monthStr,
                record.getRevenue(),
                translations.translate(language, "search.record.category"),
                "/records/" + monthStr,
                translations.translate(language, "search.record.badge")
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

    private Candidate buildInsightCandidate(Insight insight, String language) {
        String category = insight.getCategory() != null ? insight.getCategory() : "Financial";
        String title = translations.translate(language, "search.insight.title", Map.of("category", category));

        SearchResultResponse response = new SearchResultResponse(
                insight.getId() != null ? insight.getId().toString() : "",
                title,
                insight.getText(),
                "insight",
                insight.getMonth(),
                null,
                category,
                "/health/components?month=" + insight.getMonth(),
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

    private Candidate buildRecommendationCandidate(Recommendation recommendation, String language) {
        String category = recommendation.getCategory() != null ? recommendation.getCategory() : "Financial";
        String title = translations.translate(language, "search.recommendation.title", Map.of("category", category));

        SearchResultResponse response = new SearchResultResponse(
                recommendation.getId() != null ? recommendation.getId().toString() : "",
                title,
                recommendation.getText(),
                "recommendation",
                recommendation.getMonth(),
                null,
                category,
                "/health/components?month=" + recommendation.getMonth(),
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

    private Candidate buildDocumentCandidate(UploadedDocument document, String language) {
        String type = document.getDocumentTypeHint() == null ? "unknown" : document.getDocumentTypeHint();
        String status = document.getProcessingStatus().name();
        String typeLabel = translatedOrRaw(language, "document.type.", type);
        String statusLabel = translatedOrRaw(language, "document.status.", status);
        String title = document.getOriginalFilename() == null || document.getOriginalFilename().isBlank()
                ? translations.translate(language, "search.document.title") : document.getOriginalFilename();
        String date = document.getLinkedMonth() != null ? document.getLinkedMonth()
                : document.getUploadTimestamp() == null ? null : document.getUploadTimestamp().toLocalDate().toString();
        var response = new SearchResultResponse(document.getId().toString(), title,
                translations.translate(language, "search.document.description", Map.of("type", typeLabel, "status", statusLabel)),
                "document", date, null, typeLabel, "/upload/" + document.getId(), statusLabel);
        return new Candidate(response, Arrays.asList(title, type, status, typeLabel, statusLabel, document.getLinkedMonth(),
                "document", "دستاویز"), List.of(response.getDescription()));
    }

    private Candidate buildScoreCandidate(ScoreResult score, String language) {
        String band = translatedOrRaw(language, "band.", score.getBand());
        String title = translations.translate(language, "search.score.title", Map.of("month", score.getMonth()));
        String description = translations.translate(language, "search.score.description", Map.of(
                "score", score.getCompositeScore().toPlainString(), "band", band));
        var response = new SearchResultResponse(score.getId().toString(), title, description, "score", score.getMonth(),
                null, translations.translate(language, "search.score.category"),
                "/health/components?month=" + score.getMonth(), band);
        return new Candidate(response, Arrays.asList(score.getMonth(), score.getBand(), band, "score", "health",
                "financial health", "اسکور", "مالی صحت"), List.of(description));
    }

    private String translatedOrRaw(String language, String prefix, String value) {
        String key = prefix + value;
        return translations.hasKey(key) ? translations.translate(language, key) : value;
    }

    private String formatPkr(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return String.format(Locale.US, "%,d", amount.longValue());
    }
}
