package com.app.sme_health_backend.insight.repository;

import com.app.sme_health_backend.insight.entity.Insight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InsightRepository extends JpaRepository<Insight, UUID> {

    List<Insight> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Insight> findByUserIdAndMonthOrderByCreatedAtDesc(
            UUID userId,
            String month
    );

    void deleteByUserIdAndMonth(UUID userId, String month);
}
