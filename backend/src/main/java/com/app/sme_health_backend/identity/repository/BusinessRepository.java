package com.app.sme_health_backend.identity.repository;

import com.app.sme_health_backend.identity.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
    @org.springframework.data.jpa.repository.Query(value = "SELECT b.* FROM businesses b WHERE b.id = :id FOR NO KEY UPDATE", nativeQuery = true)
    java.util.Optional<Business> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
