-- Feature 12: Add document upload metadata and query performance indices
ALTER TABLE uploaded_documents
    ADD COLUMN IF NOT EXISTS original_filename VARCHAR(255),
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT,
    ADD COLUMN IF NOT EXISTS storage_path VARCHAR(500),
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(100),
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP;


CREATE INDEX IF NOT EXISTS idx_uploaded_documents_user_id
    ON uploaded_documents(user_id);

CREATE INDEX IF NOT EXISTS idx_uploaded_documents_user_status
    ON uploaded_documents(user_id, processing_status);

CREATE INDEX IF NOT EXISTS idx_uploaded_documents_user_month
    ON uploaded_documents(user_id, linked_month);
