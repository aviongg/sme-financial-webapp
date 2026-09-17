package com.app.sme_health_backend.search;

import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.repository.InsightRepository;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.search.dto.SearchResultResponse;
import com.app.sme_health_backend.search.ranker.SearchResultRanker;
import com.app.sme_health_backend.search.service.SearchService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTests {

    @Mock
    private BusinessProfileRepository profileRepository;

    @Mock
    private MonthlyRecordRepository recordRepository;

    @Mock
    private InsightRepository insightRepository;

    @Mock
    private RecommendationRepository recommendationRepository;

    private SearchResultRanker ranker;
    private SearchService searchService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        ranker = new SearchResultRanker();
        searchService = new SearchService(
                profileRepository,
                recordRepository,
                insightRepository,
                recommendationRepository,
                ranker
        );
        userId = UUID.randomUUID();
    }

    private MonthlyRecord createMonthlyRecord(UUID id, UUID uId, String month, BigDecimal revenue, String financing) {
        MonthlyRecord record = new MonthlyRecord();
        record.setId(id);
        record.setUserId(uId);
        record.setMonth(month);
        record.setRevenue(revenue);
        record.setCashInflow(revenue.multiply(new BigDecimal("0.9")));
        record.setCashOutflow(revenue.multiply(new BigDecimal("0.7")));
        record.setOperatingExpenses(revenue.multiply(new BigDecimal("0.3")));
        record.setCashBalanceEom(revenue.multiply(new BigDecimal("0.5")));
        record.setFinancingType(financing);
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }

    private Insight createInsight(UUID id, UUID uId, String month, String text, String category, String priority) {
        Insight insight = new Insight();
        org.springframework.test.util.ReflectionTestUtils.setField(insight, "id", id);
        insight.setUserId(uId);
        insight.setMonth(month);
        insight.setText(text);
        insight.setCategory(category);
        insight.setPriority(priority);
        insight.setCreatedAt(LocalDateTime.now());
        return insight;
    }

    private Recommendation createRecommendation(UUID id, UUID uId, String month, String text, String category, String priority) {
        Recommendation rec = new Recommendation();
        org.springframework.test.util.ReflectionTestUtils.setField(rec, "id", id);
        rec.setUserId(uId);
        rec.setMonth(month);
        rec.setText(text);
        rec.setCategory(category);
        rec.setPriority(priority);
        rec.setCreatedAt(LocalDateTime.now());
        return rec;
    }

    @Test
    @DisplayName("Blank or whitespace query returns empty list immediately without querying repositories")
    void shouldReturnEmptyListImmediatelyForBlankQuery() {
        List<SearchResultResponse> resultsNull = searchService.search(userId, null, "all");
        List<SearchResultResponse> resultsEmpty = searchService.search(userId, "", "all");
        List<SearchResultResponse> resultsWhitespace = searchService.search(userId, "   ", "all");

        assertTrue(resultsNull.isEmpty());
        assertTrue(resultsEmpty.isEmpty());
        assertTrue(resultsWhitespace.isEmpty());

        verifyNoInteractions(profileRepository);
        verifyNoInteractions(recordRepository);
        verifyNoInteractions(insightRepository);
        verifyNoInteractions(recommendationRepository);
    }

    @Test
    @DisplayName("Missing business profile throws 404 ResourceNotFoundException")
    void shouldThrowResourceNotFoundWhenProfileMissing() {
        when(profileRepository.existsById(userId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                searchService.search(userId, "August", "all")
        );

        verify(profileRepository).existsById(userId);
        verifyNoInteractions(recordRepository);
    }

    @Test
    @DisplayName("Finds monthly records by English month name (e.g., 'August')")
    void shouldFindMonthlyRecordsByEnglishMonth() {
        when(profileRepository.existsById(userId)).thenReturn(true);
        MonthlyRecord augRecord = createMonthlyRecord(UUID.randomUUID(), userId, "2026-08", new BigDecimal("2100000"), "none");
        when(recordRepository.findByUserIdOrderByMonthDesc(userId)).thenReturn(List.of(augRecord));
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        List<SearchResultResponse> results = searchService.search(userId, "August", "all");

        assertFalse(results.isEmpty());
        assertEquals("transaction", results.get(0).getType());
        assertEquals("2026-08", results.get(0).getDate());
        assertTrue(results.get(0).getTitle().contains("August 2026"));
    }

    @Test
    @DisplayName("Finds monthly records by month code (e.g., '2026-08')")
    void shouldFindMonthlyRecordsByMonthCode() {
        when(profileRepository.existsById(userId)).thenReturn(true);
        MonthlyRecord augRecord = createMonthlyRecord(UUID.randomUUID(), userId, "2026-08", new BigDecimal("2100000"), "none");
        when(recordRepository.findByUserIdOrderByMonthDesc(userId)).thenReturn(List.of(augRecord));
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        List<SearchResultResponse> results = searchService.search(userId, "2026-08", "all");

        assertFalse(results.isEmpty());
        assertEquals("2026-08", results.get(0).getDate());
    }

    @Test
    @DisplayName("Finds monthly records by Urdu month name (e.g., 'اگست')")
    void shouldFindMonthlyRecordsByUrduMonth() {
        when(profileRepository.existsById(userId)).thenReturn(true);
        MonthlyRecord augRecord = createMonthlyRecord(UUID.randomUUID(), userId, "2026-08", new BigDecimal("2100000"), "none");
        when(recordRepository.findByUserIdOrderByMonthDesc(userId)).thenReturn(List.of(augRecord));
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        List<SearchResultResponse> results = searchService.search(userId, "اگست", "all");

        assertFalse(results.isEmpty());
        assertEquals("2026-08", results.get(0).getDate());
    }

    @Test
    @DisplayName("Finds monthly records by financial terms (e.g., 'Revenue', 'Inflow', 'Cash Balance')")
    void shouldFindMonthlyRecordsByFinancialTerms() {
        when(profileRepository.existsById(userId)).thenReturn(true);
        MonthlyRecord record = createMonthlyRecord(UUID.randomUUID(), userId, "2026-08", new BigDecimal("2100000"), "islamic");
        when(recordRepository.findByUserIdOrderByMonthDesc(userId)).thenReturn(List.of(record));
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        List<SearchResultResponse> resultsRev = searchService.search(userId, "Revenue", "all");
        List<SearchResultResponse> resultsCash = searchService.search(userId, "Cash Balance", "all");
        List<SearchResultResponse> resultsFin = searchService.search(userId, "islamic", "all");

        assertFalse(resultsRev.isEmpty());
        assertFalse(resultsCash.isEmpty());
        assertFalse(resultsFin.isEmpty());
    }

    @Test
    @DisplayName("Finds diagnostic insights by keyword and category")
    void shouldFindInsightsByKeywordAndCategory() {
        when(profileRepository.existsById(userId)).thenReturn(true);
        when(recordRepository.findByUserIdOrderByMonthDesc(userId)).thenReturn(List.of());
        Insight insight = createInsight(
                UUID.randomUUID(),
                userId,
                "2026-08",
                "Current cash reserve supports 42 days of routine operational expenses.",
                "Liquidity Analysis",
                "Stable"
        );
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(insight));
        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        List<SearchResultResponse> resultsKeyword = searchService.search(userId, "reserve", "all");
        List<SearchResultResponse> resultsCategory = searchService.search(userId, "Liquidity", "all");

        assertFalse(resultsKeyword.isEmpty());
        assertEquals("insight", resultsKeyword.get(0).getType());
        assertEquals("Liquidity Analysis", resultsKeyword.get(0).getCategory());

        assertFalse(resultsCategory.isEmpty());
        assertEquals("insight", resultsCategory.get(0).getType());
    }

    @Test
    @DisplayName("Finds recommendations by action keyword and category")
    void shouldFindRecommendationsByActionKeyword() {
        when(profileRepository.existsById(userId)).thenReturn(true);
        when(recordRepository.findByUserIdOrderByMonthDesc(userId)).thenReturn(List.of());
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        Recommendation rec = createRecommendation(
                UUID.randomUUID(),
                userId,
                "2026-08",
                "Offer a 1.5% prompt-payment discount to wholesale buyers paying within 10 days.",
                "Working Capital",
                "High"
        );
        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(rec));

        List<SearchResultResponse> results = searchService.search(userId, "discount", "all");

        assertFalse(results.isEmpty());
        assertEquals("recommendation", results.get(0).getType());
        assertTrue(results.get(0).getDescription().contains("prompt-payment discount"));
    }

    @Test
    @DisplayName("Filters strictly by type (transaction, insight, recommendation, document)")
    void shouldFilterStrictlyByType() {
        when(profileRepository.existsById(userId)).thenReturn(true);

        MonthlyRecord record = createMonthlyRecord(UUID.randomUUID(), userId, "2026-08", new BigDecimal("1000000"), "none");
        Insight insight = createInsight(UUID.randomUUID(), userId, "2026-08", "Shared matching query text in insight", "General", "Medium");
        Recommendation rec = createRecommendation(UUID.randomUUID(), userId, "2026-08", "Shared matching query text in rec", "General", "Medium");

        // When type = transaction
        when(recordRepository.findByUserIdOrderByMonthDesc(userId)).thenReturn(List.of(record));
        List<SearchResultResponse> transResults = searchService.search(userId, "2026-08", "transaction");
        assertFalse(transResults.isEmpty());
        assertTrue(transResults.stream().allMatch(r -> "transaction".equals(r.getType())));
        verifyNoInteractions(insightRepository);
        verifyNoInteractions(recommendationRepository);

        // When type = insight
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(insight));
        List<SearchResultResponse> insightResults = searchService.search(userId, "Shared", "insight");
        assertFalse(insightResults.isEmpty());
        assertTrue(insightResults.stream().allMatch(r -> "insight".equals(r.getType())));

        // When type = recommendation
        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(rec));
        List<SearchResultResponse> recResults = searchService.search(userId, "Shared", "recommendation");
        assertFalse(recResults.isEmpty());
        assertTrue(recResults.stream().allMatch(r -> "recommendation".equals(r.getType())));

        // When type = document -> returns empty list gracefully
        List<SearchResultResponse> docResults = searchService.search(userId, "Invoice", "document");
        assertTrue(docResults.isEmpty());
    }

    @Test
    @DisplayName("Enforces strict user isolation — never returns records of another user")
    void shouldEnforceStrictUserIsolation() {
        when(profileRepository.existsById(userId)).thenReturn(true);

        // Only records belonging to userId are queried and returned by repository
        UUID otherUserId = UUID.randomUUID();
        MonthlyRecord userRecord = createMonthlyRecord(UUID.randomUUID(), userId, "2026-08", new BigDecimal("1000000"), "none");
        when(recordRepository.findByUserIdOrderByMonthDesc(userId)).thenReturn(List.of(userRecord));
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        List<SearchResultResponse> results = searchService.search(userId, "2026-08", "all");

        assertEquals(1, results.size());
        assertEquals(userRecord.getId().toString(), results.get(0).getId());

        // Verify repository was called ONLY with target userId
        verify(recordRepository).findByUserIdOrderByMonthDesc(userId);
        verify(recordRepository, never()).findByUserIdOrderByMonthDesc(otherUserId);
    }

    @Test
    @DisplayName("Search is strictly read-only — zero saves, deletes, or mutations occur")
    void shouldBeStrictlyReadOnly() {
        when(profileRepository.existsById(userId)).thenReturn(true);
        MonthlyRecord record = createMonthlyRecord(UUID.randomUUID(), userId, "2026-08", new BigDecimal("1000000"), "none");
        when(recordRepository.findByUserIdOrderByMonthDesc(userId)).thenReturn(List.of(record));
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        searchService.search(userId, "August", "all");

        verify(recordRepository, never()).save(any());
        verify(recordRepository, never()).delete(any());
        verify(insightRepository, never()).save(any());
        verify(insightRepository, never()).delete(any());
        verify(recommendationRepository, never()).save(any());
        verify(recommendationRepository, never()).delete(any());
    }
}
