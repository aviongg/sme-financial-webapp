package com.app.sme_health_backend.profile.repository;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, UUID> {
    // Hibernate's PostgreSQL PESSIMISTIC_WRITE uses FOR NO KEY UPDATE, which permits
    // FK inserts. FOR UPDATE also blocks insertion of a previously missing score
    // while a dashboard assembles its current/previous score and advice snapshot.
    @Query(value = "select * from business_profiles where user_id = :userId for update", nativeQuery = true)
    Optional<BusinessProfile> findByUserIdForUpdate(@Param("userId") UUID userId);
}
