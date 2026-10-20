package com.app.sme_health_backend.reconciliation.repository;

import com.app.sme_health_backend.reconciliation.entity.BankStatementImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankStatementImportRepository extends JpaRepository<BankStatementImport, UUID> {

    Optional<BankStatementImport> findByIdAndBusinessId(UUID id, UUID businessId);

    List<BankStatementImport> findByBusinessIdAndAccountIdOrderByCreatedAtDesc(UUID businessId, UUID accountId);

    Optional<BankStatementImport> findByBusinessIdAndAccountIdAndFileFingerprint(
            UUID businessId,
            UUID accountId,
            String fileFingerprint
    );
}
