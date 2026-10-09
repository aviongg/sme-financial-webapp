package com.app.sme_health_backend.documents.processing;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class DocumentProcessingWorker {
    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingWorker.class);
    private final DocumentDraftProcessor processor;
    private final SecurityAuditService audit;
    public DocumentProcessingWorker(DocumentDraftProcessor processor, SecurityAuditService audit) {
        this.processor = Objects.requireNonNull(processor);
        this.audit = audit;
    }

    // CallerRunsPolicy can execute on an afterCommit thread whose completed transaction
    // is still bound. Suspend it, allowing each draft-store/audit operation its own transaction.
    // A remote OCR call is never held inside a database transaction.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<DocumentStatus> process(UUID documentId) {
        Optional<DocumentStatus> result;
        try {
            result = processor.process(documentId);
        } catch (Exception exception) {
            log.error("Background OCR processing failed for document {}; type={}", documentId, exception.getClass().getSimpleName());
            result = Optional.of(DocumentStatus.failed);
        }
        if (result.orElse(null) == DocumentStatus.failed && audit != null) {
            audit.logSystemFailure(AuditEventType.OCR_PROCESSING_FAILED, null, "document", documentId.toString(),
                    "OCR processing failed", Map.of("documentId", documentId.toString()));
        }
        return result;
    }
}
