package com.app.sme_health_backend.identity.repository;

import com.app.sme_health_backend.identity.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
}
