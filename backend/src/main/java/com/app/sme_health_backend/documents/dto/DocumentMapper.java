package com.app.sme_health_backend.documents.dto;

import com.app.sme_health_backend.documents.entity.UploadedDocument;

public final class DocumentMapper {

    private DocumentMapper() {}

    public static DocumentResponse toResponse(UploadedDocument doc) {
        if (doc == null) {
            return null;
        }
        return new DocumentResponse(
                doc.getId(),
                doc.getUserId(),
                doc.getFileUrl(),
                doc.getOriginalFilename(),
                doc.getContentType(),
                doc.getFileSizeBytes(),
                doc.getUploadTimestamp(),
                doc.getProcessingStatus(),
                doc.getDocumentTypeHint(),
                doc.getExtractedData(),
                doc.getConfirmedData(),
                doc.getLinkedMonth(),
                doc.getFailureReason(),
                doc.getConfirmedAt()
        );
    }
}
