package com.app.sme_health_backend.commission.repository;

import com.app.sme_health_backend.commission.entity.CommissionRule;
import com.app.sme_health_backend.commission.model.CommissionRuleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommissionRuleRepository extends JpaRepository<CommissionRule, UUID> {

    List<CommissionRule> findByBusinessId(UUID businessId);

    List<CommissionRule> findByBusinessIdAndStatus(UUID businessId, CommissionRuleStatus status);

    List<CommissionRule> findByBusinessIdAndSalespersonId(UUID businessId, UUID salespersonId);

    Optional<CommissionRule> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndRuleNameIgnoreCase(UUID businessId, String ruleName);
}
