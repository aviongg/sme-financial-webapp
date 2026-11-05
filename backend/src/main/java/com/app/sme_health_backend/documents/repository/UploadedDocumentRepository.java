package com.app.sme_health_backend.documents.repository;

import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, UUID> {

    Optional<UploadedDocument> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM UploadedDocument d WHERE d.id = :id AND d.userId = :userId")
    Optional<UploadedDocument> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    List<UploadedDocument> findByUserIdOrderByUploadTimestampDesc(UUID userId);

    List<UploadedDocument> findByUserIdAndProcessingStatusOrderByUploadTimestampDesc(UUID userId, DocumentStatus status);

    List<UploadedDocument> findByUserIdAndLinkedMonthOrderByUploadTimestampDesc(UUID userId, String linkedMonth);

    List<UploadedDocument> findByUserIdAndProcessingStatusAndLinkedMonthOrderByUploadTimestampDesc(
            UUID userId, DocumentStatus status, String linkedMonth);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UploadedDocument d SET d.processingStatus = :targetStatus, d.processingStartedAt = :now, d.failureReason = null WHERE d.id = :id AND d.processingStatus = :expectedStatus")
    int claimStatus(
            @Param("id") UUID id,
            @Param("expectedStatus") DocumentStatus expectedStatus,
            @Param("targetStatus") DocumentStatus targetStatus,
            @Param("now") java.time.LocalDateTime now
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UploadedDocument d SET d.processingStatus = :targetStatus, d.failureReason = :failureReason, d.processingStartedAt = null WHERE d.processingStatus = :expectedStatus AND (d.processingStartedAt IS NULL OR d.processingStartedAt < :cutoff)")
    int recoverStaleProcessing(
            @Param("expectedStatus") DocumentStatus expectedStatus,
            @Param("targetStatus") DocumentStatus targetStatus,
            @Param("failureReason") String failureReason,
            @Param("cutoff") java.time.LocalDateTime cutoff
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UploadedDocument d SET d.processingStatus = :targetStatus WHERE d.id = :id AND d.processingStatus = :expectedStatus")
    int updateStatusIfStatus(
            @Param("id") UUID id,
            @Param("expectedStatus") DocumentStatus expectedStatus,
            @Param("targetStatus") DocumentStatus targetStatus
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UploadedDocument d SET d.processingStatus = :targetStatus, d.extractedData = :extractedData, d.processingStartedAt = null, d.failureReason = null WHERE d.id = :id AND d.processingStatus = :expectedStatus AND d.processingStartedAt = :expectedStartedAt AND d.extractedData IS NULL AND d.reviewedData IS NULL")
    int saveDraftIfStatus(
            @Param("id") UUID id,
            @Param("expectedStatus") DocumentStatus expectedStatus,
            @Param("targetStatus") DocumentStatus targetStatus,
            @Param("extractedData") String extractedData,
            @Param("expectedStartedAt") java.time.LocalDateTime expectedStartedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UploadedDocument d SET d.processingStatus = :targetStatus, d.failureReason = :failureReason, d.processingStartedAt = null WHERE d.id = :id AND d.processingStatus = :expectedStatus AND d.processingStartedAt = :expectedStartedAt AND d.extractedData IS NULL AND d.reviewedData IS NULL")
    int failIfStatus(
            @Param("id") UUID id,
            @Param("expectedStatus") DocumentStatus expectedStatus,
            @Param("targetStatus") DocumentStatus targetStatus,
            @Param("failureReason") String failureReason,
            @Param("expectedStartedAt") java.time.LocalDateTime expectedStartedAt
    );
}
