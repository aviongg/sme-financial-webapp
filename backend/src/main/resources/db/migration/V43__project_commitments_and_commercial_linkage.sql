-- V43: Project Financial Commitments, Commercial Document Linkage & Multicurrency Provenance
-- Developer A Wave 4 Slice C05B

-- 1. Explicitly ensure finsight_app has no DELETE privileges on project_financial_attributions
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE DELETE ON TABLE project_financial_attributions FROM finsight_app;
    END IF;
END $$;

-- 2. Add Multicurrency Provenance columns to project_financial_attributions
ALTER TABLE project_financial_attributions
    ADD COLUMN IF NOT EXISTS rate_source VARCHAR(64) NOT NULL DEFAULT 'DECLARED_USER_ASSUMPTION',
    ADD COLUMN IF NOT EXISTS rate_convention VARCHAR(64) NOT NULL DEFAULT 'MULTIPLY_SOURCE_TO_BASE',
    ADD COLUMN IF NOT EXISTS rate_date DATE,
    ADD COLUMN IF NOT EXISTS rate_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Commercial Document Linkage Table (F04 commercial invoices/bills linked to F05 projects)
-- Commercial documents have NON-POSTING semantics; this provides commercial visibility without financial ledger mutation.
CREATE TABLE IF NOT EXISTS project_commercial_document_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_kind VARCHAR(16) NOT NULL CHECK (document_kind IN ('INVOICE', 'BILL')),
    document_number VARCHAR(32),
    counterparty_name VARCHAR(255),
    document_date DATE,
    due_date DATE,
    document_total NUMERIC(19, 4) NOT NULL CHECK (document_total >= 0),
    allocated_amount NUMERIC(19, 4) NOT NULL CHECK (allocated_amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    link_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (link_status IN ('ACTIVE', 'UNLINKED')),
    link_provenance VARCHAR(255),
    notes VARCHAR(500),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    unlinked_at TIMESTAMPTZ,
    unlinked_by UUID REFERENCES app_users(id),
    unlink_reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_proj_comm_doc_project
        FOREIGN KEY (business_id, project_id) REFERENCES projects(business_id, id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_proj_comm_doc_proj
    ON project_commercial_document_links (business_id, project_id, link_status);

CREATE INDEX IF NOT EXISTS idx_proj_comm_doc_doc
    ON project_commercial_document_links (business_id, document_id, link_status);

-- 4. Financial Obligation Linkage Table (C03 receivables and payables linked to F05 projects)
CREATE TABLE IF NOT EXISTS project_financial_obligation_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    obligation_id UUID NOT NULL REFERENCES financial_obligations(id) ON DELETE RESTRICT,
    direction VARCHAR(16) NOT NULL CHECK (direction IN ('RECEIVABLE', 'PAYABLE')),
    original_amount NUMERIC(19, 4) NOT NULL CHECK (original_amount > 0),
    allocated_amount NUMERIC(19, 4) NOT NULL CHECK (allocated_amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    link_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (link_status IN ('ACTIVE', 'UNLINKED')),
    link_provenance VARCHAR(255),
    notes VARCHAR(500),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    unlinked_at TIMESTAMPTZ,
    unlinked_by UUID REFERENCES app_users(id),
    unlink_reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_proj_fin_ob_project
        FOREIGN KEY (business_id, project_id) REFERENCES projects(business_id, id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_proj_fin_ob_proj
    ON project_financial_obligation_links (business_id, project_id, link_status);

CREATE INDEX IF NOT EXISTS idx_proj_fin_ob_ob
    ON project_financial_obligation_links (business_id, obligation_id, link_status);

-- 5. Trigger to enforce that sum of active allocated amounts for an obligation across projects does not exceed original_amount
CREATE OR REPLACE FUNCTION check_project_obligation_allocation_limit()
RETURNS TRIGGER AS $$
DECLARE
    current_allocated NUMERIC(19, 4);
    ob_orig_amount NUMERIC(19, 4);
BEGIN
    IF NEW.link_status = 'ACTIVE' THEN
        SELECT original_amount INTO ob_orig_amount
        FROM financial_obligations
        WHERE id = NEW.obligation_id AND business_id = NEW.business_id;

        IF ob_orig_amount IS NULL THEN
            RAISE EXCEPTION 'Financial obligation % does not exist in business %', NEW.obligation_id, NEW.business_id;
        END IF;

        SELECT COALESCE(SUM(allocated_amount), 0)
        INTO current_allocated
        FROM project_financial_obligation_links
        WHERE business_id = NEW.business_id
          AND obligation_id = NEW.obligation_id
          AND link_status = 'ACTIVE'
          AND id <> COALESCE(NEW.id, '00000000-0000-0000-0000-000000000000'::uuid);

        IF (current_allocated + NEW.allocated_amount) > ob_orig_amount THEN
            RAISE EXCEPTION 'Total active allocated amount (%) exceeds financial obligation original amount (%) for obligation %',
                (current_allocated + NEW.allocated_amount), ob_orig_amount, NEW.obligation_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_chk_project_obligation_limit ON project_financial_obligation_links;
CREATE TRIGGER trg_chk_project_obligation_limit
BEFORE INSERT OR UPDATE ON project_financial_obligation_links
FOR EACH ROW
EXECUTE FUNCTION check_project_obligation_allocation_limit();

-- 6. Role permissions (least-privilege runtime role grants)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE DELETE ON TABLE project_commercial_document_links FROM finsight_app;
        REVOKE DELETE ON TABLE project_financial_obligation_links FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE project_commercial_document_links TO finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE project_financial_obligation_links TO finsight_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON TABLE project_commercial_document_links TO finsight_backup;
        GRANT SELECT ON TABLE project_financial_obligation_links TO finsight_backup;
    END IF;
END $$;
