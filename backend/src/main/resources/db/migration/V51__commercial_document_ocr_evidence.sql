-- V51: F04 <-> OCR evidence provenance (Developer B). Links a reviewed (confirmed) uploaded document to an invoice
-- or bill as supporting evidence. Linking NEVER posts, recognizes or changes any amount; OCR extractions stay
-- immutable and corrections stay append-only in their own tables. Unlinking is compensating (status change).

CREATE TABLE commercial_document_evidence_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    document_id UUID NOT NULL,
    uploaded_document_id UUID NOT NULL REFERENCES uploaded_documents(id) ON DELETE RESTRICT,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'UNLINKED')),
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    unlinked_by UUID NULL REFERENCES app_users(id),
    unlinked_at TIMESTAMPTZ NULL,
    unlink_reason VARCHAR(300) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_commercial_evidence_unlink CHECK (
        (status = 'ACTIVE' AND unlinked_at IS NULL AND unlinked_by IS NULL AND unlink_reason IS NULL)
        OR (status = 'UNLINKED' AND unlinked_at IS NOT NULL AND unlinked_by IS NOT NULL AND btrim(unlink_reason) <> '')
    ),
    CONSTRAINT fk_commercial_evidence_document
        FOREIGN KEY (document_id, business_id) REFERENCES commercial_documents (id, business_id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_commercial_evidence_active
    ON commercial_document_evidence_links (business_id, document_id, uploaded_document_id) WHERE status = 'ACTIVE';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE commercial_document_evidence_links TO finsight_app;
        REVOKE DELETE, TRUNCATE ON TABLE commercial_document_evidence_links FROM finsight_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON TABLE commercial_document_evidence_links TO finsight_backup;
    END IF;
END $$;
