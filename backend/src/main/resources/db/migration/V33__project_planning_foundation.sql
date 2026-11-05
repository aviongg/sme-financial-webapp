-- F05 Phase A: Project, Job and Order Planning + Budgeting Foundation.
-- All amounts in these tables are PLANNED. No actual revenue, actual cost attribution,
-- ledger event, MonthlyRecord mutation, or health-score change is performed.

CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('PROJECT', 'JOB', 'ORDER')),
    description VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'ARCHIVED')),
    start_date DATE NULL,
    target_end_date DATE NULL,
    base_currency CHAR(3) NOT NULL DEFAULT 'PKR' CHECK (base_currency = 'PKR'),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by UUID NOT NULL REFERENCES app_users(id),
    updated_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_projects_scoped_identity UNIQUE (business_id, id),
    CONSTRAINT uq_projects_business_code UNIQUE (business_id, code),
    CONSTRAINT chk_projects_code_normalized CHECK (
        btrim(code) <> ''
        AND code = upper(btrim(code))
        AND code ~ '^[A-Z0-9][A-Z0-9._/-]{0,39}$'
    ),
    CONSTRAINT chk_projects_name_nonblank CHECK (btrim(name) <> ''),
    CONSTRAINT chk_projects_description_nonblank CHECK (description IS NULL OR btrim(description) <> ''),
    CONSTRAINT chk_projects_dates CHECK (
        target_end_date IS NULL OR start_date IS NULL OR target_end_date >= start_date
    )
);

CREATE INDEX idx_projects_business_status_updated
    ON projects (business_id, status, updated_at DESC, id);
CREATE INDEX idx_projects_business_kind_updated
    ON projects (business_id, kind, updated_at DESC, id);

CREATE TABLE project_budget_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    project_id UUID NOT NULL,
    revision INT NOT NULL CHECK (revision > 0),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'APPROVED', 'SUPERSEDED')),
    base_currency CHAR(3) NOT NULL DEFAULT 'PKR' CHECK (base_currency = 'PKR'),
    planned_revenue NUMERIC(16, 4) NOT NULL CHECK (planned_revenue >= 0),
    planned_cost NUMERIC(35, 12) NOT NULL CHECK (planned_cost >= 0),
    planned_margin NUMERIC(35, 12) NOT NULL,
    planned_margin_pct NUMERIC(39, 12) NULL,
    margin_unavailable_reason VARCHAR(32) NULL
        CHECK (margin_unavailable_reason IN ('ZERO_PLANNED_REVENUE')),
    calculation_version VARCHAR(32) NOT NULL DEFAULT 'f05-plan-v1',
    source_estimate_ref VARCHAR(120) NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by UUID NOT NULL REFERENCES app_users(id),
    updated_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by UUID NULL REFERENCES app_users(id),
    approved_at TIMESTAMPTZ NULL,
    superseded_by_budget_id UUID NULL,
    superseded_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_project_budget_versions_scoped_identity UNIQUE (business_id, project_id, id),
    CONSTRAINT uq_project_budget_versions_business_id UNIQUE (business_id, id),
    CONSTRAINT uq_project_budget_versions_revision UNIQUE (business_id, project_id, revision),
    CONSTRAINT fk_project_budget_versions_project
        FOREIGN KEY (business_id, project_id) REFERENCES projects (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_budget_versions_superseded_by
        FOREIGN KEY (business_id, project_id, superseded_by_budget_id)
        REFERENCES project_budget_versions (business_id, project_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_project_budget_margin_equation
        CHECK (planned_margin = planned_revenue - planned_cost),
    CONSTRAINT chk_project_budget_zero_revenue_margin_pct CHECK (
        (planned_revenue = 0 AND planned_margin_pct IS NULL AND margin_unavailable_reason = 'ZERO_PLANNED_REVENUE')
        OR (planned_revenue > 0 AND planned_margin_pct IS NOT NULL AND margin_unavailable_reason IS NULL)
    ),
    CONSTRAINT chk_project_budget_approval_state CHECK (
        (status = 'DRAFT'
            AND approved_by IS NULL
            AND approved_at IS NULL
            AND superseded_by_budget_id IS NULL
            AND superseded_at IS NULL)
        OR (status = 'APPROVED'
            AND approved_by IS NOT NULL
            AND approved_at IS NOT NULL
            AND superseded_by_budget_id IS NULL
            AND superseded_at IS NULL)
        OR (status = 'SUPERSEDED'
            AND approved_by IS NOT NULL
            AND approved_at IS NOT NULL
            AND superseded_by_budget_id IS NOT NULL
            AND superseded_by_budget_id <> id
            AND superseded_at IS NOT NULL)
    )
);

-- Enforce at most ONE active APPROVED budget per project at a time:
CREATE UNIQUE INDEX uq_project_budget_versions_one_approved
    ON project_budget_versions (business_id, project_id)
    WHERE status = 'APPROVED';

-- Enforce at most ONE open DRAFT budget per project at a time:
CREATE UNIQUE INDEX uq_project_budget_versions_one_draft
    ON project_budget_versions (business_id, project_id)
    WHERE status = 'DRAFT';

CREATE INDEX idx_project_budget_versions_project_revision
    ON project_budget_versions (business_id, project_id, revision DESC);

CREATE TABLE project_budget_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    project_id UUID NOT NULL,
    budget_version_id UUID NOT NULL,
    line_number INT NOT NULL CHECK (line_number >= 1 AND line_number <= 100),
    category VARCHAR(16) NOT NULL
        CHECK (category IN ('MATERIAL', 'LABOUR', 'OVERHEAD', 'FREIGHT', 'OTHER')),
    description VARCHAR(200) NOT NULL CHECK (btrim(description) <> ''),
    quantity NUMERIC(15, 6) NOT NULL CHECK (quantity >= 0),
    unit VARCHAR(40) NOT NULL CHECK (btrim(unit) <> ''),
    unit_cost NUMERIC(18, 6) NOT NULL CHECK (unit_cost >= 0),
    planned_line_cost NUMERIC(33, 12) NOT NULL
        CHECK (planned_line_cost >= 0 AND planned_line_cost = quantity * unit_cost),
    CONSTRAINT uq_project_budget_lines_order UNIQUE (business_id, budget_version_id, line_number),
    CONSTRAINT fk_project_budget_lines_budget
        FOREIGN KEY (business_id, project_id, budget_version_id)
        REFERENCES project_budget_versions (business_id, project_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_project_budget_lines_budget
    ON project_budget_lines (business_id, budget_version_id, line_number);
CREATE INDEX idx_project_budget_lines_project_category
    ON project_budget_lines (business_id, project_id, category);

-- Intentional history & draft-cleanup trigger:
-- 1. TRUNCATE is forbidden on all three tables.
-- 2. Projects can only be deleted when status = 'DRAFT' AND no APPROVED/SUPERSEDED budget version exists;
--    non-DRAFT projects or projects with approved history must use CANCELLED or ARCHIVED.
-- 3. DRAFT budget versions may be updated or deleted; APPROVED and SUPERSEDED budget versions can never be deleted.
-- 4. APPROVED budget versions are immutable except for the atomic transition status: 'APPROVED' -> 'SUPERSEDED'.
-- 5. SUPERSEDED budget versions are completely immutable.
-- 6. Budget lines belonging to an APPROVED or SUPERSEDED budget version cannot be inserted, updated, or deleted.
CREATE FUNCTION enforce_project_planning_immutability() RETURNS TRIGGER AS $$
DECLARE
    parent_status VARCHAR(16);
    has_immutable_history BOOLEAN;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'Project planning tables cannot be truncated'
            USING ERRCODE = '55000';
    END IF;

    IF TG_TABLE_NAME = 'projects' THEN
        IF TG_OP = 'DELETE' THEN
            IF OLD.status <> 'DRAFT' THEN
                RAISE EXCEPTION 'Only DRAFT projects can be deleted; transition non-draft projects to CANCELLED or ARCHIVED'
                    USING ERRCODE = '55000';
            END IF;
            SELECT EXISTS (
                SELECT 1
                FROM project_budget_versions
                WHERE business_id = OLD.business_id
                  AND project_id = OLD.id
                  AND status IN ('APPROVED', 'SUPERSEDED')
            ) INTO has_immutable_history;
            IF has_immutable_history THEN
                RAISE EXCEPTION 'Projects with approved budget history cannot be deleted; transition to CANCELLED or ARCHIVED'
                    USING ERRCODE = '55000';
            END IF;
            RETURN OLD;
        END IF;

        IF TG_OP = 'UPDATE' AND (
            NEW.id IS DISTINCT FROM OLD.id
            OR NEW.business_id IS DISTINCT FROM OLD.business_id
            OR NEW.created_by IS DISTINCT FROM OLD.created_by
            OR NEW.created_at IS DISTINCT FROM OLD.created_at
            OR NEW.version <= OLD.version
            OR OLD.status = 'ARCHIVED'
        ) THEN
            RAISE EXCEPTION 'Project identity, creation provenance, archived state, and monotonic version are immutable'
                USING ERRCODE = '55000';
        END IF;
        RETURN NEW;
    END IF;

    IF TG_TABLE_NAME = 'project_budget_versions' THEN
        IF TG_OP = 'DELETE' THEN
            IF OLD.status <> 'DRAFT' THEN
                RAISE EXCEPTION 'Approved and superseded project budget versions are permanent history and cannot be deleted'
                    USING ERRCODE = '55000';
            END IF;
            RETURN OLD;
        END IF;

        IF TG_OP = 'UPDATE' THEN
            IF OLD.status = 'SUPERSEDED' THEN
                RAISE EXCEPTION 'Superseded project budget versions are immutable'
                    USING ERRCODE = '55000';
            END IF;
            IF OLD.status = 'APPROVED' THEN
                IF NEW.status <> 'SUPERSEDED'
                   OR NEW.id IS DISTINCT FROM OLD.id
                   OR NEW.business_id IS DISTINCT FROM OLD.business_id
                   OR NEW.project_id IS DISTINCT FROM OLD.project_id
                   OR NEW.revision IS DISTINCT FROM OLD.revision
                   OR NEW.base_currency IS DISTINCT FROM OLD.base_currency
                   OR NEW.planned_revenue IS DISTINCT FROM OLD.planned_revenue
                   OR NEW.planned_cost IS DISTINCT FROM OLD.planned_cost
                   OR NEW.planned_margin IS DISTINCT FROM OLD.planned_margin
                   OR NEW.planned_margin_pct IS DISTINCT FROM OLD.planned_margin_pct
                   OR NEW.margin_unavailable_reason IS DISTINCT FROM OLD.margin_unavailable_reason
                   OR NEW.calculation_version IS DISTINCT FROM OLD.calculation_version
                   OR NEW.source_estimate_ref IS DISTINCT FROM OLD.source_estimate_ref
                   OR NEW.created_by IS DISTINCT FROM OLD.created_by
                   OR NEW.created_at IS DISTINCT FROM OLD.created_at
                   OR NEW.approved_by IS DISTINCT FROM OLD.approved_by
                   OR NEW.approved_at IS DISTINCT FROM OLD.approved_at
                   OR NEW.version <> OLD.version + 1 THEN
                    RAISE EXCEPTION 'Approved project budget versions are immutable except for atomic supersession'
                        USING ERRCODE = '55000';
                END IF;
            END IF;
        END IF;
        RETURN NEW;
    END IF;

    IF TG_TABLE_NAME = 'project_budget_lines' THEN
        -- Check the original parent too: a move to a DRAFT must not remove a line
        -- from APPROVED or SUPERSEDED history.
        IF TG_OP = 'UPDATE' THEN
            SELECT status INTO parent_status
            FROM project_budget_versions
            WHERE business_id = OLD.business_id
              AND project_id = OLD.project_id
              AND id = OLD.budget_version_id;
            IF parent_status IS DISTINCT FROM 'DRAFT' THEN
                RAISE EXCEPTION 'Lines of an approved or superseded budget version are immutable'
                    USING ERRCODE = '55000';
            END IF;
        END IF;

        SELECT status INTO parent_status
        FROM project_budget_versions
        WHERE business_id = COALESCE(NEW.business_id, OLD.business_id)
          AND project_id = COALESCE(NEW.project_id, OLD.project_id)
          AND id = COALESCE(NEW.budget_version_id, OLD.budget_version_id);
        IF TG_OP IN ('INSERT', 'UPDATE') AND parent_status IS DISTINCT FROM 'DRAFT' THEN
            RAISE EXCEPTION 'Lines of an approved or superseded budget version are immutable'
                USING ERRCODE = '55000';
        END IF;
        IF TG_OP = 'DELETE' AND parent_status IN ('APPROVED', 'SUPERSEDED') THEN
            RAISE EXCEPTION 'Lines of an approved or superseded budget version cannot be deleted'
                USING ERRCODE = '55000';
        END IF;
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        RETURN NEW;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_projects_immutability
    BEFORE UPDATE OR DELETE ON projects
    FOR EACH ROW EXECUTE FUNCTION enforce_project_planning_immutability();
CREATE TRIGGER trg_projects_no_truncate
    BEFORE TRUNCATE ON projects
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_project_planning_immutability();

CREATE TRIGGER trg_project_budget_versions_immutability
    BEFORE UPDATE OR DELETE ON project_budget_versions
    FOR EACH ROW EXECUTE FUNCTION enforce_project_planning_immutability();
CREATE TRIGGER trg_project_budget_versions_no_truncate
    BEFORE TRUNCATE ON project_budget_versions
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_project_planning_immutability();

CREATE TRIGGER trg_project_budget_lines_immutability
    BEFORE INSERT OR UPDATE OR DELETE ON project_budget_lines
    FOR EACH ROW EXECUTE FUNCTION enforce_project_planning_immutability();
CREATE TRIGGER trg_project_budget_lines_no_truncate
    BEFORE TRUNCATE ON project_budget_lines
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_project_planning_immutability();

-- Least-privilege runtime and backup grants (trigger enforces draft-only deletion):
REVOKE ALL ON projects, project_budget_versions, project_budget_lines FROM PUBLIC;
DO $$ BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE ALL ON projects, project_budget_versions, project_budget_lines FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON projects, project_budget_versions, project_budget_lines TO finsight_app;
    END IF;
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        REVOKE ALL ON projects, project_budget_versions, project_budget_lines FROM finsight_backup;
        GRANT SELECT ON projects, project_budget_versions, project_budget_lines TO finsight_backup;
    END IF;
END $$;
