package com.app.sme_health_backend.shared.advice;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.score.dto.ScoreResult;
import com.app.sme_health_backend.score.repository.ScoreResultReader;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Shared locked snapshot for insight and recommendation generation. */
@Service
@Transactional
public class AdviceContextService {

    // Bump whenever advice rules or translation wording changes to regenerate existing output.
    private static final String RULES_VERSION = "phase1-advice-v1";

    private final ScoreResultReader scoreResultReader;
    private final BusinessProfileRepository businessProfileRepository;

    public AdviceContextService(ScoreResultReader scoreResultReader,
                                BusinessProfileRepository businessProfileRepository) {
        this.scoreResultReader = scoreResultReader;
        this.businessProfileRepository = businessProfileRepository;
    }

    public Optional<AdviceContext> latest(UUID userId) {
        BusinessProfile profile = lockProfile(userId);
        return scoreResultReader.findLatest(userId).map(score -> context(score, profile));
    }

    public Optional<AdviceContext> forMonth(UUID userId, String month) {
        ScoreResult.validateMonth(month);
        BusinessProfile profile = lockProfile(userId);
        return scoreResultReader.findByUserIdAndMonth(userId, month).map(score -> context(score, profile));
    }

    /** Explicit generation is allowed only for the current persisted snapshot of that month. */
    public AdviceContext forScore(ScoreResult supplied) {
        if (supplied == null) {
            throw new IllegalArgumentException("Score result is required");
        }
        supplied.validate();
        BusinessProfile profile = lockProfile(supplied.getUserId());
        ScoreResult persisted = scoreResultReader.findByUserIdAndMonth(supplied.getUserId(), supplied.getMonth())
                .orElseThrow(() -> new IllegalArgumentException("Score must be persisted before generating advice"));
        persisted.validate();
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
        score.validate();
        String previousMonth = ScoreResult.validateMonth(score.getMonth()).minusMonths(1).toString();
        ScoreResult previous = scoreResultReader.findByUserIdAndMonth(score.getUserId(), previousMonth).orElse(null);
        if (previous != null) {
            previous.validate();
        }
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

    private static String canonicalScore(ScoreResult score) {
        StringBuilder source = new StringBuilder();
        append(source, score.getUserId().toString());
        append(source, score.getMonth());
        append(source, decimal(score.getCompositeScore()));
        append(source, score.getBand());
        for (String key : ScoreResult.COMPONENT_KEYS) {
            append(source, key);
            append(source, decimal(score.getComponentScores().get(key)));
        }
        append(source, score.getWeakestComponent());
        append(source, decimal(score.getDataCompleteness()));
        append(source, score.getComputedAt().toString());
        return source.toString();
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static void append(StringBuilder output, String value) {
        // Length-prefixing avoids collisions between nulls, delimiters, and adjacent fields.
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
