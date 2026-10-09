package com.app.sme_health_backend.documents.config;

import com.app.sme_health_backend.documents.ocr.OcrClient;
import com.app.sme_health_backend.documents.ocr.OcrClientException;
import com.app.sme_health_backend.documents.processing.DocumentDraftProcessor;
import com.app.sme_health_backend.documents.processing.DocumentDraftStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentProcessingConfiguration {

    @Bean
    @ConditionalOnMissingBean(DocumentDraftProcessor.class)
    public DocumentDraftProcessor documentDraftProcessor(
            @Autowired(required = false) OcrClient ocrClient,
            DocumentDraftStore documentDraftStore
    ) {
        OcrClient client = ocrClient != null ? ocrClient : request -> {
            throw new OcrClientException(OcrClientException.Reason.unavailable);
        };
        return new DocumentDraftProcessor(client, documentDraftStore);
    }
}
