package com.app.sme_health_backend.documents.processing;

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
    public DocumentProcessingWorker(DocumentDraftProcessor processor) {
        this.processor = Objects.requireNonNull(processor);
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
            // A database/audit failure may roll back the transition; do not claim or audit a failed row.
            result = Optional.empty();
        }
        return result;
    }
}
