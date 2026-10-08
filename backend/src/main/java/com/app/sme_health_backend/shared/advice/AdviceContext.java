package com.app.sme_health_backend.shared.advice;

import com.app.sme_health_backend.scoring.entity.ScoreResult;

/** previousScore is absent when the exact preceding calendar month has no persisted score. */
public record AdviceContext(
        ScoreResult score,
        ScoreResult previousScore,
        String language,
        String sourceVersion
) {
}
