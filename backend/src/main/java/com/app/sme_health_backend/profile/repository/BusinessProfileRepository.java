package com.app.sme_health_backend.profile.repository;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, UUID> {

    @Query(value = "SELECT * FROM business_profiles WHERE user_id = :userId FOR UPDATE", nativeQuery = true)
    Optional<BusinessProfile> findByUserIdForUpdate(@Param("userId") UUID userId);

    List<BusinessProfile> findByWhatsappOptInTrueAndWhatsappNumberIsNotNull();
}