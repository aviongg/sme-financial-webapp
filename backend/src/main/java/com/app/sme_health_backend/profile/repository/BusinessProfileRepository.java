package com.app.sme_health_backend.profile.repository;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, UUID> {
}