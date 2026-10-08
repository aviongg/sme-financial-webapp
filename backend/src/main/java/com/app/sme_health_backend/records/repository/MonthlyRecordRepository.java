package com.app.sme_health_backend.records.repository;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonthlyRecordRepository extends JpaRepository<MonthlyRecord, UUID> {

    Optional<MonthlyRecord> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM MonthlyRecord r WHERE r.id = :id AND r.userId = :userId")
    Optional<MonthlyRecord> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    Optional<MonthlyRecord> findByUserIdAndMonth(UUID userId, String month);

    List<MonthlyRecord> findByUserIdOrderByMonthDesc(UUID userId);

    List<MonthlyRecord> findByUserIdOrderByMonthAsc(UUID userId);

    List<MonthlyRecord> findTop6ByUserIdOrderByMonthDesc(UUID userId);
}