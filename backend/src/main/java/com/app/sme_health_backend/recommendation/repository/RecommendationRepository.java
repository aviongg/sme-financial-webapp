package com.app.sme_health_backend.recommendation.repository;

import com.app.sme_health_backend.recommendation.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecommendationRepository
        extends JpaRepository<Recommendation, UUID> {

    List<Recommendation> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Recommendation> findByUserIdAndMonthOrderByCreatedAtDesc(UUID userId, String month);
}
