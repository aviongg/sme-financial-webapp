package com.app.sme_health_backend.scoring.repository;

import com.app.sme_health_backend.scoring.entity.ScoreResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoreResultRepository extends JpaRepository<ScoreResult, UUID> {

    Optional<ScoreResult> findByUserIdAndMonth(UUID userId, String month);

    Optional<ScoreResult> findFirstByUserIdOrderByMonthDesc(UUID userId);

    List<ScoreResult> findByUserIdOrderByMonthDesc(UUID userId);
}
