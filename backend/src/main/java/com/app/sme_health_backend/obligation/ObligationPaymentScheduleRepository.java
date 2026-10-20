package com.app.sme_health_backend.obligation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ObligationPaymentScheduleRepository extends JpaRepository<ObligationPaymentSchedule, UUID> {

    List<ObligationPaymentSchedule> findByBusinessIdAndObligationIdOrderByInstallmentNumberAsc(UUID businessId, UUID obligationId);

    List<ObligationPaymentSchedule> findByBusinessIdAndExpectedDateBetweenOrderByExpectedDateAsc(UUID businessId, LocalDate startDate, LocalDate endDate);

    List<ObligationPaymentSchedule> findByBusinessId(UUID businessId);

    void deleteByBusinessIdAndObligationId(UUID businessId, UUID obligationId);
}
