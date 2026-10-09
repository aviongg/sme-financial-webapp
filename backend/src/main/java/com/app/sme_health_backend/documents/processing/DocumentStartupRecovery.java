package com.app.sme_health_backend.documents.processing;

import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Startup recovery routine to prevent documents from remaining permanently stuck in
 * 'processing' status following an unexpected application crash or restart.
 */
@Component
public class DocumentStartupRecovery {

    private static final Logger log = LoggerFactory.getLogger(DocumentStartupRecovery.class);

    private final UploadedDocumentRepository repository;

    public DocumentStartupRecovery(UploadedDocumentRepository repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverStaleProcessingDocuments() {
        int recovered = repository.resetAllByStatus(
                DocumentStatus.processing,
                DocumentStatus.failed,
                "processing_interrupted"
        );
        if (recovered > 0) {
            log.warn("DocumentStartupRecovery: Recovered {} interrupted document(s) stuck in 'processing' status across restart.", recovered);
        }
    }
}
