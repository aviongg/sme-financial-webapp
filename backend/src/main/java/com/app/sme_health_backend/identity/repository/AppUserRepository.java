package com.app.sme_health_backend.identity.repository;

import com.app.sme_health_backend.identity.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);

    // NO KEY UPDATE serializes security mutations while allowing an isolated audit
    // transaction to reference this user's primary key without blocking on its FK.
    @org.springframework.data.jpa.repository.Query(value = "SELECT u.* FROM app_users u WHERE u.id = :id FOR NO KEY UPDATE", nativeQuery = true)
    Optional<AppUser> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
