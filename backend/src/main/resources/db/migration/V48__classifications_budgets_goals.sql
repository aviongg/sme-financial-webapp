-- V48: analysis classifications, business budgets (with revisions) and financial goals (Developer B).
--
-- Classifications are ANALYSIS METADATA over authoritative facts (ledger transactions and recognized commercial
-- documents). They never change an amount; they assign a reporting category and, for break-even, an explicit
-- FIXED/VARIABLE cost behaviour. Nothing is guessed: unclassified amounts stay visibly unclassified.

CREATE TABLE financial_classifications (
    business_id UUID NOT NULL REFERENCES businesses(id),
    source_kind VARCHAR(20) NOT NULL CHECK (source_kind IN ('TRANSACTION', 'COMMERCIAL_DOCUMENT')),
    source_id UUID NOT NULL,
    category VARCHAR(32) NOT NULL CHECK (category IN (
        'PRODUCT_SALES', 'SERVICE_REVENUE', 'OTHER_INCOME',
        'DIRECT_MATERIALS', 'DIRECT_LABOUR', 'RENT', 'SALARIES_WAGES', 'UTILITIES', 'MARKETING',
        'TRANSPORT_LOGISTICS', 'REPAIRS_MAINTENANCE', 'PROFESSIONAL_FEES', 'TAXES_LICENSES', 'FINANCE_CHARGES',
        'SUPPLIES', 'OTHER_EXPENSE')),
    cost_behavior VARCHAR(8) NULL CHECK (cost_behavior IS NULL OR cost_behavior IN ('FIXED', 'VARIABLE')),
    updated_by UUID NOT NULL REFERENCES app_users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT pk_financial_classifications PRIMARY KEY (business_id, source_kind, source_id)
);

CREATE TABLE category_cost_behaviors (
    business_id UUID NOT NULL REFERENCES businesses(id),
    category VARCHAR(32) NOT NULL,
    cost_behavior VARCHAR(8) NOT NULL CHECK (cost_behavior IN ('FIXED', 'VARIABLE')),
    updated_by UUID NOT NULL REFERENCES app_users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_category_cost_behaviors PRIMARY KEY (business_id, category)
);

CREATE TABLE business_budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    name VARCHAR(120) NOT NULL CHECK (btrim(name) <> ''),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    project_id UUID NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by UUID NOT NULL REFERENCES app_users(id),
    updated_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_business_budgets_scoped UNIQUE (business_id, id),
    CONSTRAINT chk_business_budgets_period CHECK (
        period_start <= period_end
        AND EXTRACT(DAY FROM period_start) = 1
        AND period_end = (date_trunc('month', period_end) + interval '1 month - 1 day')::date
        AND period_end <= period_start + interval '24 months'
    ),
    CONSTRAINT fk_business_budgets_project
        FOREIGN KEY (business_id, project_id) REFERENCES projects (business_id, id) ON DELETE RESTRICT
);

CREATE TABLE business_budget_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    budget_id UUID NOT NULL,
    revision_no INT NOT NULL CHECK (revision_no >= 1),
    status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'SUPERSEDED')),
    notes VARCHAR(500) NULL,
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by UUID NULL REFERENCES app_users(id),
    approved_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_business_budget_revisions UNIQUE (business_id, budget_id, revision_no),
    CONSTRAINT uq_business_budget_revisions_scoped UNIQUE (business_id, id),
    CONSTRAINT chk_business_budget_revisions_approval CHECK (
        (status = 'DRAFT' AND approved_at IS NULL AND approved_by IS NULL)
        OR (status IN ('APPROVED', 'SUPERSEDED') AND approved_at IS NOT NULL AND approved_by IS NOT NULL)
    ),
    CONSTRAINT fk_business_budget_revisions_budget
        FOREIGN KEY (business_id, budget_id) REFERENCES business_budgets (business_id, id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_business_budget_single_approved
    ON business_budget_revisions (business_id, budget_id) WHERE status = 'APPROVED';
CREATE UNIQUE INDEX uq_business_budget_single_draft
    ON business_budget_revisions (business_id, budget_id) WHERE status = 'DRAFT';

CREATE TABLE business_budget_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    revision_id UUID NOT NULL,
    line_kind VARCHAR(8) NOT NULL CHECK (line_kind IN ('REVENUE', 'EXPENSE')),
    category VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount >= 0),
    CONSTRAINT uq_business_budget_lines UNIQUE (business_id, revision_id, line_kind, category),
    CONSTRAINT fk_business_budget_lines_revision
        FOREIGN KEY (business_id, revision_id) REFERENCES business_budget_revisions (business_id, id) ON DELETE CASCADE
);

-- Approved and superseded revisions (and their lines) are frozen.
CREATE FUNCTION enforce_budget_revision_freeze() RETURNS TRIGGER AS $$
DECLARE
    revision_status VARCHAR(12);
BEGIN
    IF TG_TABLE_NAME = 'business_budget_revisions' THEN
        IF TG_OP = 'DELETE' THEN
            IF OLD.status <> 'DRAFT' THEN
                RAISE EXCEPTION 'Approved budget revisions are permanent' USING ERRCODE = '55000';
            END IF;
            RETURN OLD;
        END IF;
        IF OLD.status = 'SUPERSEDED'
           OR (OLD.status = 'APPROVED' AND NEW.status <> 'SUPERSEDED')
           OR NEW.version <= OLD.version THEN
            RAISE EXCEPTION 'Budget revision history is immutable' USING ERRCODE = '55000';
        END IF;
        RETURN NEW;
    END IF;
    SELECT status INTO revision_status FROM business_budget_revisions
    WHERE id = COALESCE(NEW.revision_id, OLD.revision_id);
    IF revision_status IS NOT NULL AND revision_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Lines of approved budget revisions are frozen' USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_budget_revisions_freeze BEFORE UPDATE OR DELETE ON business_budget_revisions
    FOR EACH ROW EXECUTE FUNCTION enforce_budget_revision_freeze();
CREATE TRIGGER trg_budget_lines_freeze BEFORE INSERT OR UPDATE OR DELETE ON business_budget_lines
    FOR EACH ROW EXECUTE FUNCTION enforce_budget_revision_freeze();

CREATE TABLE financial_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    name VARCHAR(120) NOT NULL CHECK (btrim(name) <> ''),
    metric VARCHAR(24) NOT NULL CHECK (metric IN (
        'REVENUE', 'GROSS_PROFIT', 'OPERATING_PROFIT', 'CASH_BALANCE', 'EXPENSE_CEILING', 'RECEIVABLES_CEILING')),
    target_value NUMERIC(19, 4) NOT NULL CHECK (target_value >= 0),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED', 'ARCHIVED')),
    notes VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by UUID NOT NULL REFERENCES app_users(id),
    updated_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_financial_goals_period CHECK (period_start <= period_end
        AND period_end <= period_start + interval '5 years')
);

CREATE INDEX idx_financial_goals_business ON financial_goals (business_id, status, period_end);
CREATE INDEX idx_business_budgets_business ON business_budgets (business_id, status, period_start);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE financial_classifications, category_cost_behaviors,
            business_budgets, business_budget_revisions, business_budget_lines, financial_goals TO finsight_app;
        REVOKE TRUNCATE ON TABLE financial_classifications, category_cost_behaviors, business_budgets,
            business_budget_revisions, business_budget_lines, financial_goals FROM finsight_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON TABLE financial_classifications, category_cost_behaviors, business_budgets,
            business_budget_revisions, business_budget_lines, financial_goals TO finsight_backup;
    END IF;
END $$;
