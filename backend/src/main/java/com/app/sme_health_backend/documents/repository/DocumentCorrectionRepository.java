package com.app.sme_health_backend.documents.repository;

import com.app.sme_health_backend.documents.entity.DocumentCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DocumentCorrectionRepository extends JpaRepository<DocumentCorrection, UUID> {
    List<DocumentCorrection> findByBusinessIdAndDocumentIdOrderByCorrectedAtAsc(UUID businessId, UUID documentId);
}
