-- V49: F06 estimate -> commercial document lineage (Developer B).
-- An approved estimate revision can seed a DRAFT customer invoice (a non-binding quotation until finalized).
-- The draft is created through the normal F04 path (financial_effect = NONE); this table only records lineage.

CREATE TABLE estimate_commercial_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    estimate_id UUID NOT NULL,
    estimate_version_id UUID NOT NULL,
    document_id UUID NOT NULL,
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_estimate_commercial_links_document UNIQUE (business_id, document_id),
    CONSTRAINT fk_estimate_commercial_links_version
        FOREIGN KEY (business_id, estimate_id, estimate_version_id)
        REFERENCES estimate_versions (business_id, estimate_id, id) ON DELETE RESTRICT,
    -- Drafts may be deleted in F04; lineage then disappears with them (no dangling reference).
    CONSTRAINT fk_estimate_commercial_links_document
        FOREIGN KEY (document_id, business_id) REFERENCES commercial_documents (id, business_id) ON DELETE CASCADE
);

CREATE INDEX idx_estimate_commercial_links_estimate ON estimate_commercial_links (business_id, estimate_id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT ON TABLE estimate_commercial_links TO finsight_app;
        REVOKE UPDATE, TRUNCATE ON TABLE estimate_commercial_links FROM finsight_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON TABLE estimate_commercial_links TO finsight_backup;
    END IF;
END $$;
