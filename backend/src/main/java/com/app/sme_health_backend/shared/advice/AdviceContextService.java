package com.app.sme_health_backend.shared.advice;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.repository.ScoreResultRepository;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Shared locked snapshot for insight and recommendation generation using canonical ScoreResult. */
@Service
@Transactional
public class AdviceContextService {

    // Bump whenever advice rules or translation wording changes to regenerate existing output.
    private static final String RULES_VERSION = "phase1-advice-v1";
    private static final List<String> COMPONENT_KEYS = List.of(
            "cashflow", "profitability", "repayment", "trend", "compliance"
    );

    private final ScoreResultRepository scoreResultRepository;
    private final BusinessProfileRepository businessProfileRepository;

    public AdviceContextService(ScoreResultRepository scoreResultRepository,
                                BusinessProfileRepository businessProfileRepository) {
        this.scoreResultRepository = scoreResultRepository;
        this.businessProfileRepository = businessProfileRepository;
    }

    public Optional<AdviceContext> latest(UUID userId) {
        BusinessProfile profile = lockProfile(userId);
        return scoreResultRepository.findLatestLocked(userId).map(score -> context(score, profile));
    }

    public Optional<AdviceContext> forMonth(UUID userId, String month) {
        validateMonth(month);
        BusinessProfile profile = lockProfile(userId);
        return scoreResultRepository.findByUserIdAndMonthLocked(userId, month).map(score -> context(score, profile));
    }

    /** Explicit generation is allowed only for the current persisted snapshot of that month. */
    public AdviceContext forScore(ScoreResult supplied) {
        if (supplied == null) {
            throw new IllegalArgumentException("Score result is required");
        }
        if (supplied.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        validateMonth(supplied.getMonth());
        BusinessProfile profile = lockProfile(supplied.getUserId());
        ScoreResult persisted = scoreResultRepository.findByUserIdAndMonthLocked(supplied.getUserId(), supplied.getMonth())
                .orElseThrow(() -> new IllegalArgumentException("Score must be persisted before generating advice"));

        if (!canonicalScore(supplied).equals(canonicalScore(persisted))) {
            throw new IllegalArgumentException("Score snapshot is stale; reload the persisted score before generating advice");
        }
        return context(persisted, profile);
    }

    private BusinessProfile lockProfile(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        return businessProfileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Business profile not found for this user"));
    }

    private AdviceContext context(ScoreResult score, BusinessProfile profile) {
        String previousMonth = validateMonth(score.getMonth()).minusMonths(1).toString();
        ScoreResult previous = scoreResultRepository.findByUserIdAndMonthLocked(score.getUserId(), previousMonth).orElse(null);

        String language = profile.getLanguagePreference();
        if (!"en".equals(language) && !"ur".equals(language)) {
            throw new IllegalStateException("Persisted language preference must be en or ur");
        }
        StringBuilder source = new StringBuilder();
        append(source, RULES_VERSION);
        append(source, language);
        append(source, canonicalScore(score));
        append(source, previous == null ? null : canonicalScore(previous));
        return new AdviceContext(score, previous, language, sha256(source.toString()));
    }

    public static YearMonth validateMonth(String month) {
        if (month == null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("Month must be in YYYY-MM format");
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Month must be in YYYY-MM format", exception);
        }
    }

    private static String canonicalScore(ScoreResult score) {
        StringBuilder source = new StringBuilder();
        append(source, score.getUserId().toString());
        append(source, score.getMonth());
        append(source, decimal(score.getCompositeScore()));
        append(source, score.getBand());
        Map<String, BigDecimal> components = score.getComponentScores() != null
                ? score.getComponentScores().toMap()
                : Map.of();
        for (String key : COMPONENT_KEYS) {
            append(source, key);
            append(source, decimal(components.get(key)));
        }
        append(source, score.getWeakestComponent());
        append(source, decimal(score.getDataCompleteness()));
        append(source, score.getComputedAt() != null ? score.getComputedAt().toString() : null);
        return source.toString();
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static void append(StringBuilder output, String value) {
        output.append(value == null ? "-1:" : value.length() + ":" + value);
    }

    private static String sha256(String source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
