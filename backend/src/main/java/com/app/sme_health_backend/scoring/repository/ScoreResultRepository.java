package com.app.sme_health_backend.scoring.repository;

import com.app.sme_health_backend.scoring.entity.ScoreResult;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoreResultRepository extends JpaRepository<ScoreResult, UUID> {

    Optional<ScoreResult> findByUserIdAndMonth(UUID userId, String month);

    Optional<ScoreResult> findFirstByUserIdOrderByMonthDesc(UUID userId);

    List<ScoreResult> findByUserIdOrderByMonthDesc(UUID userId);

    List<ScoreResult> findTop12ByUserIdOrderByMonthDesc(UUID userId);

    /** Read-only: the persisted score of the latest month in an inclusive range (no recalculation). */
    Optional<ScoreResult> findFirstByUserIdAndMonthBetweenOrderByMonthDesc(UUID userId, String fromMonth, String toMonth);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT s FROM ScoreResult s WHERE s.userId = :userId AND s.month = :month")
    Optional<ScoreResult> findByUserIdAndMonthLocked(@Param("userId") UUID userId, @Param("month") String month);

    @Query(value = "SELECT * FROM score_results WHERE user_id = :userId ORDER BY month DESC LIMIT 1 FOR SHARE", nativeQuery = true)
    Optional<ScoreResult> findLatestLocked(@Param("userId") UUID userId);
}
