package com.app.sme_health_backend.records.repository;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonthlyRecordRepository extends JpaRepository<MonthlyRecord, UUID> {

    Optional<MonthlyRecord> findByUserIdAndMonth(UUID userId, String month);

    List<MonthlyRecord> findByUserIdOrderByMonthDesc(UUID userId);
}