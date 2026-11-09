-- V44: Project OCR Evidence Links & Financial Attribution Integration
-- Developer A Wave 4 Slice C05C

CREATE TABLE IF NOT EXISTS project_ocr_evidence_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    document_id UUID NOT NULL,
    evidence_status VARCHAR(32) NOT NULL DEFAULT 'EXTRACTED_UNREVIEWED'
        CHECK (evidence_status IN ('EXTRACTED_UNREVIEWED', 'REVIEWED_UNMATCHED', 'MATCHED_TO_POSTED_SOURCE', 'ATTRIBUTED', 'INVALIDATED', 'REVERSED')),
    financial_source_type VARCHAR(32)
        CHECK (financial_source_type IN ('TRANSACTION', 'OBLIGATION_SETTLEMENT', 'COMMISSION_RECOGNITION', 'FX_DIFFERENCE')),
    financial_source_id UUID,
    attribution_id UUID REFERENCES project_financial_attributions(id) ON DELETE SET NULL,
    match_method VARCHAR(32) NOT NULL DEFAULT 'UNMATCHED'
        CHECK (match_method IN ('DIRECT_ID', 'REFERENCE_MATCH', 'MANUAL_ASSISTED', 'UNMATCHED')),
    ocr_amount NUMERIC(19, 4) NOT NULL CHECK (ocr_amount >= 0),
    ocr_currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    ocr_date DATE,
    counterparty_name VARCHAR(255),
    category VARCHAR(100),
    document_type VARCHAR(64),
    confidence NUMERIC(5, 4),
    discrepancy_amount NUMERIC(19, 4),
    discrepancy_notes VARCHAR(500),
    link_provenance VARCHAR(255),
    notes VARCHAR(500),
    confirmed_by_user_id UUID REFERENCES app_users(id),
    confirmed_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    invalidated_by UUID REFERENCES app_users(id),
    invalidation_reason VARCHAR(255),
    idempotency_key VARCHAR(128),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_proj_ocr_ev_project
        FOREIGN KEY (business_id, project_id) REFERENCES projects(business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_proj_ocr_ev_document
        FOREIGN KEY (business_id, document_id) REFERENCES uploaded_documents(user_id, id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_proj_ocr_ev_proj
    ON project_ocr_evidence_links (business_id, project_id, evidence_status);

CREATE INDEX IF NOT EXISTS idx_proj_ocr_ev_doc
    ON project_ocr_evidence_links (business_id, document_id, evidence_status);

CREATE INDEX IF NOT EXISTS idx_proj_ocr_ev_attr
    ON project_ocr_evidence_links (business_id, attribution_id);

CREATE INDEX IF NOT EXISTS idx_proj_ocr_ev_source
    ON project_ocr_evidence_links (business_id, financial_source_type, financial_source_id);

-- Least-privilege runtime role grants
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE DELETE ON TABLE project_ocr_evidence_links FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE project_ocr_evidence_links TO finsight_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON TABLE project_ocr_evidence_links TO finsight_backup;
    END IF;
END $$;
