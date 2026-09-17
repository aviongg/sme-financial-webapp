package com.app.sme_health_backend.search;

import com.app.sme_health_backend.search.dto.SearchResultResponse;
import com.app.sme_health_backend.search.ranker.SearchResultRanker;
import com.app.sme_health_backend.search.ranker.SearchResultRanker.Candidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchResultRankerTests {

    private SearchResultRanker ranker;

    @BeforeEach
    void setUp() {
        ranker = new SearchResultRanker();
    }

    private Candidate createCandidate(String id, String title, String category, String month, List<String> primaryTerms, List<String> text) {
        SearchResultResponse response = new SearchResultResponse(
                id,
                title,
                "Description for " + title,
                "transaction",
                month,
                new BigDecimal("100000"),
                category,
                "/records/" + id,
                "Official"
        );
        return new Candidate(response, primaryTerms, text);
    }

    @Test
    @DisplayName("Exact match (100) ranks above prefix match (50)")
    void shouldRankExactAbovePrefix() {
        // Candidate 1: exact match for "Revenue"
        Candidate exactCandidate = createCandidate(
                "c-exact",
                "August Monthly Record",
                "Monthly Financial Record",
                "2026-08",
                List.of("Revenue", "2026-08"),
                List.of("Details text")
        );

        // Candidate 2: prefix match for "Revenue" (term "Revenues" starts with "revenue")
        Candidate prefixCandidate = createCandidate(
                "c-prefix",
                "July Monthly Record",
                "Monthly Financial Record",
                "2026-07",
                List.of("RevenuesStream", "2026-07"),
                List.of("Details text")
        );

        List<SearchResultResponse> results = ranker.rankAndCap(
                List.of(prefixCandidate, exactCandidate),
                "Revenue",
                20
        );

        assertEquals(2, results.size());
        assertEquals("c-exact", results.get(0).getId());
        assertEquals("c-prefix", results.get(1).getId());
    }

    @Test
    @DisplayName("Prefix match (50) ranks above substring match (25)")
    void shouldRankPrefixAboveSubstring() {
        // Candidate 1: prefix match ("Receivables" starts with "rec")
        Candidate prefixCandidate = createCandidate(
                "c-prefix",
                "Working Capital",
                "Receivables",
                "2026-08",
                List.of("Receivables"),
                List.of("Trade receivables lag")
        );

        // Candidate 2: substring match (term "PrepaymentsAndDirect" contains "rec")
        Candidate substringCandidate = createCandidate(
                "c-substring",
                "Cash Flow",
                "Cash",
                "2026-07",
                List.of("PrepaymentsAndDirect"),
                List.of("Direct reconciliation")
        );

        List<SearchResultResponse> results = ranker.rankAndCap(
                List.of(substringCandidate, prefixCandidate),
                "rec",
                20
        );

        assertEquals(2, results.size());
        assertEquals("c-prefix", results.get(0).getId());
        assertEquals("c-substring", results.get(1).getId());
    }

    @Test
    @DisplayName("Should cap results at maximum 20")
    void shouldCapResultsAtMaximum20() {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 1; i <= 35; i++) {
            candidates.add(createCandidate(
                    String.format("id-%02d", i),
                    "Record " + i,
                    "General",
                    "2026-08",
                    List.of("MatchTerm"),
                    List.of("Details")
            ));
        }

        List<SearchResultResponse> results = ranker.rankAndCap(candidates, "MatchTerm", 20);

        assertEquals(20, results.size());
    }

    @Test
    @DisplayName("Ranking occurs before the 20-result cap is applied")
    void shouldRankBeforeApplyingCap() {
        List<Candidate> candidates = new ArrayList<>();

        // 25 candidates with substring match only (Score 25)
        for (int i = 1; i <= 25; i++) {
            candidates.add(createCandidate(
                    String.format("sub-%02d", i),
                    "Record " + i,
                    "General",
                    "2026-01",
                    List.of("SomeGeneralItem"),
                    List.of("Contains targetkeyword here")
            ));
        }

        // 1 candidate with EXACT match (Score 100), placed at the end of the list
        Candidate exactCandidate = createCandidate(
                "exact-top",
                "Important Exact Record",
                "General",
                "2026-08",
                List.of("targetkeyword"),
                List.of("Exact match")
        );
        candidates.add(exactCandidate);

        // If cap was applied before ranking, exactCandidate might be truncated away.
        // Because ranking occurs BEFORE cap, exactCandidate MUST be item #0 in the top 20.
        List<SearchResultResponse> results = ranker.rankAndCap(candidates, "targetkeyword", 20);

        assertEquals(20, results.size());
        assertEquals("exact-top", results.get(0).getId());
    }

    @Test
    @DisplayName("Tie-break 1: Date descending when rank scores tie")
    void shouldTieBreakByDateDescending() {
        Candidate older = createCandidate(
                "c-older",
                "Older Record",
                "General",
                "2026-05",
                List.of("SharedTerm"),
                List.of("Details")
        );

        Candidate newer = createCandidate(
                "c-newer",
                "Newer Record",
                "General",
                "2026-08",
                List.of("SharedTerm"),
                List.of("Details")
        );

        List<SearchResultResponse> results = ranker.rankAndCap(
                List.of(older, newer),
                "SharedTerm",
                20
        );

        assertEquals(2, results.size());
        assertEquals("c-newer", results.get(0).getId());
        assertEquals("c-older", results.get(1).getId());
    }

    @Test
    @DisplayName("Tie-break 2: Stable ID ascending when rank score and date tie")
    void shouldTieBreakByStableIdAscending() {
        Candidate itemB = createCandidate(
                "id-b",
                "Record B",
                "General",
                "2026-08",
                List.of("SharedTerm"),
                List.of("Details")
        );

        Candidate itemA = createCandidate(
                "id-a",
                "Record A",
                "General",
                "2026-08",
                List.of("SharedTerm"),
                List.of("Details")
        );

        List<SearchResultResponse> results = ranker.rankAndCap(
                List.of(itemB, itemA),
                "SharedTerm",
                20
        );

        assertEquals(2, results.size());
        assertEquals("id-a", results.get(0).getId());
        assertEquals("id-b", results.get(1).getId());
    }
}
