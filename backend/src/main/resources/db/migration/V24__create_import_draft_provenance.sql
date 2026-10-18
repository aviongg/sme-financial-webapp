-- V24: Create Import Batches and Import Draft Provenance Schema
-- Author: Antigravity (Developer A Backend Coordinator)
-- Scope: A-F12A-IMPORT-DRAFTS Persistent Spreadsheet Import to Record Drafts

-- 1. Ensure composite unique constraint on record_drafts for strict tenant foreign key
ALTER TABLE record_drafts DROP CONSTRAINT IF EXISTS uq_record_drafts_id_business CASCADE;
ALTER TABLE record_drafts ADD CONSTRAINT uq_record_drafts_id_business UNIQUE (id, business_id);

-- 2. Create import_batches table
CREATE TABLE IF NOT EXISTS import_batches (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    source_label VARCHAR(255) NOT NULL,
    total_rows_imported INTEGER NOT NULL DEFAULT 0,
    created_by_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_import_batches_id_business UNIQUE (id, business_id)
);

CREATE INDEX IF NOT EXISTS idx_import_batches_business_created
    ON import_batches (business_id, created_at DESC);

-- 3. Create import_batch_rows table for row-level provenance and draft linkages
CREATE TABLE IF NOT EXISTS import_batch_rows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    batch_id UUID NOT NULL,
    source_row_id VARCHAR(100) NOT NULL,
    source_row_number INTEGER NOT NULL,
    source_reference VARCHAR(255),
    raw_amount_input VARCHAR(100),
    payload_hash VARCHAR(64) NOT NULL,
    draft_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_import_batch_rows_unique UNIQUE (business_id, batch_id, source_row_id),
    CONSTRAINT uq_import_batch_rows_draft UNIQUE (business_id, draft_id),
    CONSTRAINT fk_import_batch_rows_batch FOREIGN KEY (batch_id, business_id)
        REFERENCES import_batches (id, business_id) ON DELETE CASCADE,
    CONSTRAINT fk_import_batch_rows_draft FOREIGN KEY (draft_id, business_id)
        REFERENCES record_drafts (id, business_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_import_batch_rows_batch
    ON import_batch_rows (business_id, batch_id);

CREATE INDEX IF NOT EXISTS idx_import_batch_rows_draft
    ON import_batch_rows (business_id, draft_id);

-- 4. Runtime role privileges for finsight_app
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE import_batches TO finsight_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE import_batch_rows TO finsight_app;
    END IF;
END $$;
