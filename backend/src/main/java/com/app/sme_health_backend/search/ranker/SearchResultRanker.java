package com.app.sme_health_backend.search.ranker;

import com.app.sme_health_backend.search.dto.SearchResultResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Component
public class SearchResultRanker {

    public static final int SCORE_EXACT = 100;
    public static final int SCORE_PREFIX = 50;
    public static final int SCORE_SUBSTRING = 25;
    public static final int SCORE_NONE = 0;

    public static class Candidate {
        private final SearchResultResponse response;
        private final List<String> primaryTerms;
        private final List<String> searchableText;

        public Candidate(
                SearchResultResponse response,
                List<String> primaryTerms,
                List<String> searchableText
        ) {
            this.response = response;
            this.primaryTerms = primaryTerms != null ? primaryTerms : Collections.emptyList();
            this.searchableText = searchableText != null ? searchableText : Collections.emptyList();
        }

        public SearchResultResponse getResponse() {
            return response;
        }

        public List<String> getPrimaryTerms() {
            return primaryTerms;
        }

        public List<String> getSearchableText() {
            return searchableText;
        }
    }

    private static class ScoredCandidate {
        private final Candidate candidate;
        private final int score;

        public ScoredCandidate(Candidate candidate, int score) {
            this.candidate = candidate;
            this.score = score;
        }

        public Candidate getCandidate() {
            return candidate;
        }

        public int getScore() {
            return score;
        }
    }

    /**
     * Calculates the relevance score for a given candidate against the query.
     * Exact = 100, Prefix = 50, Substring = 25, None = 0.
     */
    public int calculateScore(Candidate candidate, String query) {
        if (candidate == null || query == null) {
            return SCORE_NONE;
        }
        String q = query.trim().toLowerCase();
        if (q.isEmpty()) {
            return SCORE_NONE;
        }

        // 1. Check Exact match (100)
        for (String term : candidate.getPrimaryTerms()) {
            if (term != null && term.trim().equalsIgnoreCase(q)) {
                return SCORE_EXACT;
            }
        }
        SearchResultResponse res = candidate.getResponse();
        if (res != null) {
            if (res.getTitle() != null && res.getTitle().trim().equalsIgnoreCase(q)) {
                return SCORE_EXACT;
            }
            if (res.getCategory() != null && res.getCategory().trim().equalsIgnoreCase(q)) {
                return SCORE_EXACT;
            }
            if (res.getDate() != null && res.getDate().trim().equalsIgnoreCase(q)) {
                return SCORE_EXACT;
            }
            if (res.getBadge() != null && res.getBadge().trim().equalsIgnoreCase(q)) {
                return SCORE_EXACT;
            }
        }

        // Check if query exactly equals any individual token in primary terms or searchable text
        for (String text : candidate.getSearchableText()) {
            if (text != null) {
                String[] tokens = text.toLowerCase().split("[\\s\\p{Punct}]+");
                for (String token : tokens) {
                    if (token.equals(q)) {
                        return SCORE_EXACT;
                    }
                }
            }
        }

        // 2. Check Prefix match (50)
        for (String term : candidate.getPrimaryTerms()) {
            if (term != null && term.trim().toLowerCase().startsWith(q)) {
                return SCORE_PREFIX;
            }
        }
        if (res != null) {
            if (res.getTitle() != null && res.getTitle().trim().toLowerCase().startsWith(q)) {
                return SCORE_PREFIX;
            }
            if (res.getCategory() != null && res.getCategory().trim().toLowerCase().startsWith(q)) {
                return SCORE_PREFIX;
            }
            if (res.getDate() != null && res.getDate().trim().toLowerCase().startsWith(q)) {
                return SCORE_PREFIX;
            }
        }
        for (String text : candidate.getSearchableText()) {
            if (text != null) {
                String[] tokens = text.toLowerCase().split("[\\s\\p{Punct}]+");
                for (String token : tokens) {
                    if (token.startsWith(q)) {
                        return SCORE_PREFIX;
                    }
                }
            }
        }

        // 3. Check Substring match (25)
        for (String term : candidate.getPrimaryTerms()) {
            if (term != null && term.toLowerCase().contains(q)) {
                return SCORE_SUBSTRING;
            }
        }
        if (res != null) {
            if (res.getTitle() != null && res.getTitle().toLowerCase().contains(q)) {
                return SCORE_SUBSTRING;
            }
            if (res.getDescription() != null && res.getDescription().toLowerCase().contains(q)) {
                return SCORE_SUBSTRING;
            }
            if (res.getCategory() != null && res.getCategory().toLowerCase().contains(q)) {
                return SCORE_SUBSTRING;
            }
            if (res.getDate() != null && res.getDate().toLowerCase().contains(q)) {
                return SCORE_SUBSTRING;
            }
        }
        for (String text : candidate.getSearchableText()) {
            if (text != null && text.toLowerCase().contains(q)) {
                return SCORE_SUBSTRING;
            }
        }

        return SCORE_NONE;
    }

    /**
     * Ranks candidates and applies capping.
     * Ranking occurs BEFORE applying the result limit (cap).
     * Tie breakers:
     * 1. rank descending (100 > 50 > 25)
     * 2. date descending (nulls last)
     * 3. ID ascending / stable
     */
    public List<SearchResultResponse> rankAndCap(List<Candidate> candidates, String query, int limit) {
        if (candidates == null || candidates.isEmpty() || query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<ScoredCandidate> scoredList = new ArrayList<>();
        for (Candidate candidate : candidates) {
            int score = calculateScore(candidate, query);
            if (score > SCORE_NONE) {
                scoredList.add(new ScoredCandidate(candidate, score));
            }
        }

        // Comparator with the three required tie-breakers:
        // 1. rank score descending
        // 2. date descending
        // 3. ID ascending (stable)
        Comparator<ScoredCandidate> comparator = Comparator
                .<ScoredCandidate>comparingInt(ScoredCandidate::getScore).reversed()
                .thenComparing(
                        sc -> sc.getCandidate().getResponse().getDate(),
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        sc -> sc.getCandidate().getResponse().getId(),
                        Comparator.nullsLast(Comparator.naturalOrder())
                );

        scoredList.sort(comparator);

        int maxResults = Math.max(0, limit);
        return scoredList.stream()
                .limit(maxResults)
                .map(sc -> sc.getCandidate().getResponse())
                .toList();
    }
}
