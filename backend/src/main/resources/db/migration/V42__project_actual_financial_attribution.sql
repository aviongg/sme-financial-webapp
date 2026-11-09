-- V42: Project Actual Financial Attribution and Split-Allocation Foundation
-- Author: Google Antigravity (Sole Full-Stack Developer A Engineer & Financial Systems Architect)
-- Feature: C05A Project Actual Financial Attribution & Cost Evidence Foundation

CREATE TABLE IF NOT EXISTS project_financial_attributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL
        CHECK (source_type IN ('TRANSACTION', 'OBLIGATION_SETTLEMENT', 'COMMISSION_RECOGNITION', 'FX_DIFFERENCE')),
    source_id UUID NOT NULL,
    source_direction VARCHAR(16) NOT NULL
        CHECK (source_direction IN ('INFLOW', 'OUTFLOW')),
    source_currency VARCHAR(3) NOT NULL,
    source_eligible_amount NUMERIC(19, 4) NOT NULL
        CHECK (source_eligible_amount > 0),
    attributed_amount NUMERIC(19, 4) NOT NULL
        CHECK (attributed_amount > 0),
    attributed_currency VARCHAR(3) NOT NULL,
    cost_category VARCHAR(16) NOT NULL
        CHECK (cost_category IN ('MATERIAL', 'LABOUR', 'OVERHEAD', 'FREIGHT', 'OTHER', 'COMMISSION', 'REVENUE')),
    economic_classification VARCHAR(32) NOT NULL
        CHECK (economic_classification IN ('POSTED_CASH_ACTUAL', 'RECOGNIZED_ECONOMIC_COST', 'NONCASH_FINANCIAL_EVIDENCE')),
    source_event_date DATE NOT NULL,
    attribution_date DATE NOT NULL,
    notes VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'REVERSED', 'OFFSET')),
    reversal_reason VARCHAR(255),
    reversed_at TIMESTAMPTZ,
    reversed_by UUID REFERENCES app_users(id),
    idempotency_key VARCHAR(128),
    source_provenance VARCHAR(255),
    exchange_rate NUMERIC(19, 6),
    base_amount NUMERIC(19, 4) NOT NULL
        CHECK (base_amount > 0),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_project_attributions_project
        FOREIGN KEY (business_id, project_id) REFERENCES projects (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_proj_attribution_biz_idempotency
        UNIQUE (business_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_proj_attr_biz_proj
    ON project_financial_attributions(business_id, project_id, status);

CREATE INDEX IF NOT EXISTS idx_proj_attr_biz_source
    ON project_financial_attributions(business_id, source_type, source_id, status);

CREATE INDEX IF NOT EXISTS idx_proj_attr_biz_date
    ON project_financial_attributions(business_id, attribution_date DESC);

-- Trigger to enforce that sum of active attributions across projects cannot exceed source eligible amount
CREATE OR REPLACE FUNCTION check_project_source_allocation_limit()
RETURNS TRIGGER AS $$
DECLARE
    current_allocated NUMERIC(19, 4);
BEGIN
    IF NEW.status = 'ACTIVE' THEN
        SELECT COALESCE(SUM(attributed_amount), 0)
        INTO current_allocated
        FROM project_financial_attributions
        WHERE business_id = NEW.business_id
          AND source_type = NEW.source_type
          AND source_id = NEW.source_id
          AND status = 'ACTIVE'
          AND id <> COALESCE(NEW.id, '00000000-0000-0000-0000-000000000000'::uuid);

        IF (current_allocated + NEW.attributed_amount) > NEW.source_eligible_amount THEN
            RAISE EXCEPTION 'Total active attributed amount (%) exceeds source eligible amount (%) for source %',
                (current_allocated + NEW.attributed_amount), NEW.source_eligible_amount, NEW.source_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_chk_source_allocation_limit ON project_financial_attributions;
CREATE TRIGGER trg_chk_source_allocation_limit
BEFORE INSERT OR UPDATE ON project_financial_attributions
FOR EACH ROW
EXECUTE FUNCTION check_project_source_allocation_limit();

-- Grant least-privilege runtime DML permissions
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE DELETE ON TABLE project_financial_attributions FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE project_financial_attributions TO finsight_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON TABLE project_financial_attributions TO finsight_backup;
    END IF;
END $$;
