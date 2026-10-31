-- F09 Phase A: sampling / production-cost planning only.
-- F04 canonical V34 is present; no F04 domain or accounting integration.
-- All amounts in these tables are PLANNED. No actual revenue, actual cost attribution,
-- ledger event, MonthlyRecord mutation, or health-score change is performed.

CREATE TABLE sample_activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('SAMPLE', 'PRODUCTION_COST_ACTIVITY')),
    recovery_intent VARCHAR(16) NOT NULL CHECK (recovery_intent IN ('FREE', 'PAID', 'PARTIAL')),
    project_id UUID NULL,
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
    CONSTRAINT fk_sample_activities_project FOREIGN KEY (business_id, project_id)
        REFERENCES projects (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_sample_activities_scoped_identity UNIQUE (business_id, id),
    CONSTRAINT uq_sample_activities_business_code UNIQUE (business_id, code),
    CONSTRAINT chk_sample_activities_code_normalized CHECK (
        btrim(code) <> ''
        AND code = upper(btrim(code))
        AND code ~ '^[A-Z0-9][A-Z0-9._/-]{0,39}$'
    ),
    CONSTRAINT chk_sample_activities_name_nonblank CHECK (btrim(name) <> ''),
    CONSTRAINT chk_sample_activities_description_nonblank CHECK (description IS NULL OR btrim(description) <> ''),
    CONSTRAINT chk_sample_activities_dates CHECK (
        target_end_date IS NULL OR start_date IS NULL OR target_end_date >= start_date
    )
);

CREATE INDEX idx_sample_activities_business_status_updated
    ON sample_activities (business_id, status, updated_at DESC, id);
CREATE INDEX idx_sample_activities_business_kind_updated
    ON sample_activities (business_id, kind, updated_at DESC, id);

CREATE INDEX idx_sample_activities_project ON sample_activities (business_id, project_id);

CREATE TABLE sample_cost_plan_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    activity_id UUID NOT NULL,
    revision INT NOT NULL CHECK (revision > 0),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'APPROVED', 'SUPERSEDED')),
    base_currency CHAR(3) NOT NULL DEFAULT 'PKR' CHECK (base_currency = 'PKR'),
    recovery_intent VARCHAR(16) NOT NULL CHECK (recovery_intent IN ('FREE', 'PAID', 'PARTIAL')),
    planned_recovery NUMERIC(16, 4) NULL CHECK (planned_recovery >= 0 AND planned_recovery < 1000000000000),
    planned_cost NUMERIC(35, 12) NOT NULL CHECK (planned_cost >= 0 AND planned_cost < 100000000000000000000000),
    planned_contribution NUMERIC(35, 12) NULL,
    contribution_unavailable_reason VARCHAR(32) NULL
        CHECK (contribution_unavailable_reason IN ('PLANNED_RECOVERY_UNKNOWN')),
    calculation_version VARCHAR(32) NOT NULL DEFAULT 'f09-plan-v1' CHECK (calculation_version = 'f09-plan-v1'),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by UUID NOT NULL REFERENCES app_users(id),
    updated_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by UUID NULL REFERENCES app_users(id),
    approved_at TIMESTAMPTZ NULL,
    superseded_by_plan_id UUID NULL,
    superseded_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_sample_cost_plan_versions_scoped_identity UNIQUE (business_id, activity_id, id),
    CONSTRAINT uq_sample_cost_plan_versions_business_id UNIQUE (business_id, id),
    CONSTRAINT uq_sample_cost_plan_versions_revision UNIQUE (business_id, activity_id, revision),
    CONSTRAINT fk_sample_cost_plan_versions_activity
        FOREIGN KEY (business_id, activity_id) REFERENCES sample_activities (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_sample_cost_plan_versions_superseded_by
        FOREIGN KEY (business_id, activity_id, superseded_by_plan_id)
        REFERENCES sample_cost_plan_versions (business_id, activity_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_sample_plan_recovery CHECK (recovery_intent <> 'FREE' OR (planned_recovery IS NOT NULL AND planned_recovery = 0)),
    CONSTRAINT chk_sample_plan_contribution CHECK (
        (planned_recovery IS NULL AND planned_contribution IS NULL
            AND contribution_unavailable_reason IS NOT NULL AND contribution_unavailable_reason = 'PLANNED_RECOVERY_UNKNOWN')
        OR (planned_recovery IS NOT NULL AND planned_contribution IS NOT NULL
            AND planned_contribution = planned_recovery - planned_cost AND contribution_unavailable_reason IS NULL)
    ),
    CONSTRAINT chk_activity_plan_approval_state CHECK (
        (status = 'DRAFT'
            AND approved_by IS NULL
            AND approved_at IS NULL
            AND superseded_by_plan_id IS NULL
            AND superseded_at IS NULL)
        OR (status = 'APPROVED'
            AND approved_by IS NOT NULL
            AND approved_at IS NOT NULL
            AND superseded_by_plan_id IS NULL
            AND superseded_at IS NULL)
        OR (status = 'SUPERSEDED'
            AND approved_by IS NOT NULL
            AND approved_at IS NOT NULL
            AND superseded_by_plan_id IS NOT NULL
            AND superseded_by_plan_id <> id
            AND superseded_at IS NOT NULL)
    )
);

-- Enforce at most ONE active APPROVED plan per activity at a time:
CREATE UNIQUE INDEX uq_sample_cost_plan_versions_one_approved
    ON sample_cost_plan_versions (business_id, activity_id)
    WHERE status = 'APPROVED';

-- Enforce at most ONE open DRAFT plan per activity at a time:
CREATE UNIQUE INDEX uq_sample_cost_plan_versions_one_draft
    ON sample_cost_plan_versions (business_id, activity_id)
    WHERE status = 'DRAFT';

CREATE INDEX idx_sample_cost_plan_versions_activity_revision
    ON sample_cost_plan_versions (business_id, activity_id, revision DESC);

CREATE TABLE sample_cost_plan_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    activity_id UUID NOT NULL,
    plan_version_id UUID NOT NULL,
    line_number INT NOT NULL CHECK (line_number >= 1 AND line_number <= 100),
    category VARCHAR(16) NOT NULL
        CHECK (category IN ('MATERIAL', 'LABOUR', 'OVERHEAD', 'FREIGHT', 'OTHER')),
    description VARCHAR(200) NOT NULL CHECK (btrim(description) <> ''),
    quantity NUMERIC(15, 6) NOT NULL CHECK (quantity >= 0 AND quantity < 1000000000),
    unit VARCHAR(40) NOT NULL CHECK (btrim(unit) <> ''),
    unit_cost NUMERIC(18, 6) NOT NULL CHECK (unit_cost >= 0 AND unit_cost < 1000000000000),
    planned_line_cost NUMERIC(33, 12) NOT NULL
        CHECK (planned_line_cost >= 0 AND planned_line_cost = quantity * unit_cost),
    CONSTRAINT uq_sample_cost_plan_lines_scoped_identity UNIQUE (business_id, id),
    CONSTRAINT uq_sample_cost_plan_lines_order UNIQUE (business_id, plan_version_id, line_number),
    CONSTRAINT fk_sample_cost_plan_lines_plan
        FOREIGN KEY (business_id, activity_id, plan_version_id)
        REFERENCES sample_cost_plan_versions (business_id, activity_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_sample_cost_plan_lines_plan
    ON sample_cost_plan_lines (business_id, plan_version_id, line_number);
CREATE INDEX idx_sample_cost_plan_lines_activity_category
    ON sample_cost_plan_lines (business_id, activity_id, category);


-- Runtime writers cannot bypass lifecycle/history with raw SQL. Parent locks also
-- serialize direct line/plan mutations against approval and activity closure.
CREATE FUNCTION enforce_sample_planning_immutability() RETURNS TRIGGER AS $$
DECLARE
    parent_activity sample_activities%ROWTYPE;
    parent_plan sample_cost_plan_versions%ROWTYPE;
    linked_status VARCHAR(16);
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'Sample planning tables cannot be truncated' USING ERRCODE = '55000';
    END IF;
    IF TG_TABLE_NAME = 'sample_activities' THEN
        IF TG_OP = 'DELETE' THEN
            IF OLD.status <> 'DRAFT' OR EXISTS (
                SELECT 1 FROM sample_cost_plan_versions WHERE business_id = OLD.business_id
                    AND activity_id = OLD.id AND status IN ('APPROVED', 'SUPERSEDED')
            ) THEN
                RAISE EXCEPTION 'Only draft activities without immutable history can be deleted' USING ERRCODE = '55000';
            END IF;
            RETURN OLD;
        END IF;
        IF TG_OP = 'INSERT' THEN
            IF NEW.status <> 'DRAFT' OR NEW.version <> 0 THEN
                RAISE EXCEPTION 'Activities begin in DRAFT at version zero' USING ERRCODE = '55000';
            END IF;
        ELSE
            IF NEW.id IS DISTINCT FROM OLD.id OR NEW.business_id IS DISTINCT FROM OLD.business_id
                OR NEW.created_by IS DISTINCT FROM OLD.created_by OR NEW.created_at IS DISTINCT FROM OLD.created_at
                OR NEW.base_currency IS DISTINCT FROM OLD.base_currency OR NEW.version <> OLD.version + 1
                OR OLD.status = 'ARCHIVED' THEN
                RAISE EXCEPTION 'Activity identity, creation provenance, archived state and version are protected' USING ERRCODE = '55000';
            END IF;
            IF OLD.status IN ('COMPLETED', 'CANCELLED') AND (
                NEW.status <> 'ARCHIVED' OR
                (to_jsonb(NEW) - ARRAY['status','version','updated_by','updated_at']) IS DISTINCT FROM
                (to_jsonb(OLD) - ARRAY['status','version','updated_by','updated_at'])
            ) THEN
                RAISE EXCEPTION 'Closed activities can only be archived without metadata changes' USING ERRCODE = '55000';
            END IF;
            IF NEW.status <> OLD.status AND NOT (
                (OLD.status = 'DRAFT' AND NEW.status IN ('ACTIVE','CANCELLED','ARCHIVED')) OR
                (OLD.status = 'ACTIVE' AND NEW.status IN ('COMPLETED','CANCELLED','ARCHIVED')) OR
                (OLD.status IN ('COMPLETED','CANCELLED') AND NEW.status = 'ARCHIVED')
            ) THEN
                RAISE EXCEPTION 'Invalid activity lifecycle transition' USING ERRCODE = '55000';
            END IF;
            IF NEW.status IN ('COMPLETED','CANCELLED','ARCHIVED') AND EXISTS (
                SELECT 1 FROM sample_cost_plan_versions WHERE business_id = OLD.business_id
                    AND activity_id = OLD.id AND status = 'DRAFT'
            ) THEN
                RAISE EXCEPTION 'Approve or discard draft plan before closing activity' USING ERRCODE = '55000';
            END IF;
            IF NEW.recovery_intent IS DISTINCT FROM OLD.recovery_intent AND EXISTS (
                SELECT 1 FROM sample_cost_plan_versions WHERE business_id = OLD.business_id AND activity_id = OLD.id
            ) THEN
                RAISE EXCEPTION 'Recovery intent is fixed while plan revisions exist' USING ERRCODE = '55000';
            END IF;
        END IF;
        IF NEW.project_id IS NOT NULL AND (TG_OP = 'INSERT' OR NEW.project_id IS DISTINCT FROM OLD.project_id) THEN
            SELECT status INTO linked_status FROM projects WHERE business_id = NEW.business_id AND id = NEW.project_id FOR SHARE;
            IF linked_status IS NULL THEN
                RAISE EXCEPTION 'Project must belong to the same business' USING ERRCODE = '23503';
            END IF;
            IF linked_status = 'ARCHIVED' THEN
                RAISE EXCEPTION 'Archived projects cannot be newly linked' USING ERRCODE = '55000';
            END IF;
        END IF;
        RETURN NEW;
    END IF;

    SELECT * INTO parent_activity FROM sample_activities
        WHERE business_id = COALESCE(NEW.business_id, OLD.business_id)
          AND id = COALESCE(NEW.activity_id, OLD.activity_id) FOR UPDATE;
    IF parent_activity.status IS NULL OR parent_activity.status NOT IN ('DRAFT','ACTIVE') THEN
        RAISE EXCEPTION 'Plans and lines require an open planning activity' USING ERRCODE = '55000';
    END IF;

    IF TG_TABLE_NAME = 'sample_cost_plan_versions' THEN
        IF TG_OP = 'DELETE' THEN
            IF OLD.status <> 'DRAFT' THEN
                RAISE EXCEPTION 'Approved and superseded plans are permanent history' USING ERRCODE = '55000';
            END IF;
            RETURN OLD;
        END IF;
        IF NEW.recovery_intent <> parent_activity.recovery_intent THEN
            RAISE EXCEPTION 'Plan recovery intent must match the activity snapshot' USING ERRCODE = '55000';
        END IF;
        IF TG_OP = 'INSERT' THEN
            IF NEW.status <> 'DRAFT' OR NEW.version <> 0 THEN
                RAISE EXCEPTION 'Plans begin in DRAFT at version zero' USING ERRCODE = '55000';
            END IF;
        ELSE
            IF NEW.id IS DISTINCT FROM OLD.id OR NEW.business_id IS DISTINCT FROM OLD.business_id
                OR NEW.activity_id IS DISTINCT FROM OLD.activity_id OR NEW.revision IS DISTINCT FROM OLD.revision
                OR NEW.base_currency IS DISTINCT FROM OLD.base_currency OR NEW.recovery_intent IS DISTINCT FROM OLD.recovery_intent
                OR NEW.calculation_version IS DISTINCT FROM OLD.calculation_version
                OR NEW.created_by IS DISTINCT FROM OLD.created_by OR NEW.created_at IS DISTINCT FROM OLD.created_at
                OR NEW.version <> OLD.version + 1 THEN
                RAISE EXCEPTION 'Plan identity, revision, intent, creation provenance and version are protected' USING ERRCODE = '55000';
            END IF;
            IF OLD.status = 'SUPERSEDED' THEN
                RAISE EXCEPTION 'Superseded plans are immutable' USING ERRCODE = '55000';
            END IF;
            IF OLD.status = 'DRAFT' AND NEW.status NOT IN ('DRAFT','APPROVED') THEN
                RAISE EXCEPTION 'Draft plan can only remain draft or be approved' USING ERRCODE = '55000';
            END IF;
            IF OLD.status = 'APPROVED' AND (
                NEW.status <> 'SUPERSEDED' OR
                (to_jsonb(NEW) - ARRAY['status','version','updated_by','updated_at','superseded_by_plan_id','superseded_at']) IS DISTINCT FROM
                (to_jsonb(OLD) - ARRAY['status','version','updated_by','updated_at','superseded_by_plan_id','superseded_at'])
            ) THEN
                RAISE EXCEPTION 'Approved plans allow only atomic supersession' USING ERRCODE = '55000';
            END IF;
            IF OLD.status = 'DRAFT' AND NEW.status = 'APPROVED' AND (
                (to_jsonb(NEW) - ARRAY['status','version','updated_by','updated_at','approved_by','approved_at']) IS DISTINCT FROM
                (to_jsonb(OLD) - ARRAY['status','version','updated_by','updated_at','approved_by','approved_at'])
            ) THEN
                RAISE EXCEPTION 'Approval cannot simultaneously change financial content' USING ERRCODE = '55000';
            END IF;
        END IF;
        RETURN NEW;
    END IF;

    IF TG_TABLE_NAME = 'sample_cost_plan_lines' THEN
        -- Forbid moving rows between plans, including moves out of immutable history.
        IF TG_OP = 'UPDATE' AND (NEW.id IS DISTINCT FROM OLD.id
            OR NEW.business_id IS DISTINCT FROM OLD.business_id OR NEW.activity_id IS DISTINCT FROM OLD.activity_id
            OR NEW.plan_version_id IS DISTINCT FROM OLD.plan_version_id OR NEW.line_number IS DISTINCT FROM OLD.line_number) THEN
            RAISE EXCEPTION 'Plan line identity and parent cannot change' USING ERRCODE = '55000';
        END IF;
        SELECT * INTO parent_plan FROM sample_cost_plan_versions
            WHERE business_id = COALESCE(NEW.business_id, OLD.business_id)
              AND activity_id = COALESCE(NEW.activity_id, OLD.activity_id)
              AND id = COALESCE(NEW.plan_version_id, OLD.plan_version_id) FOR UPDATE;
        -- During a permitted draft-parent DELETE, ON DELETE CASCADE sees no parent.
        IF TG_OP = 'DELETE' AND parent_plan.id IS NULL THEN RETURN OLD; END IF;
        IF parent_plan.status IS DISTINCT FROM 'DRAFT' THEN
            RAISE EXCEPTION 'Only draft plan lines can change' USING ERRCODE = '55000';
        END IF;
        IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sample_activities_immutability BEFORE INSERT OR UPDATE OR DELETE ON sample_activities
    FOR EACH ROW EXECUTE FUNCTION enforce_sample_planning_immutability();
CREATE TRIGGER trg_sample_activities_no_truncate BEFORE TRUNCATE ON sample_activities
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_sample_planning_immutability();
CREATE TRIGGER trg_sample_cost_plan_versions_immutability BEFORE INSERT OR UPDATE OR DELETE ON sample_cost_plan_versions
    FOR EACH ROW EXECUTE FUNCTION enforce_sample_planning_immutability();
CREATE TRIGGER trg_sample_cost_plan_versions_no_truncate BEFORE TRUNCATE ON sample_cost_plan_versions
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_sample_planning_immutability();
CREATE TRIGGER trg_sample_cost_plan_lines_immutability BEFORE INSERT OR UPDATE OR DELETE ON sample_cost_plan_lines
    FOR EACH ROW EXECUTE FUNCTION enforce_sample_planning_immutability();
CREATE TRIGGER trg_sample_cost_plan_lines_no_truncate BEFORE TRUNCATE ON sample_cost_plan_lines
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_sample_planning_immutability();

-- Deferred final-state checks permit atomic header+line replacement and supersession,
-- while rejecting incomplete/mismatched raw SQL plans at transaction commit.
CREATE FUNCTION validate_sample_plan_consistency() RETURNS TRIGGER AS $$
DECLARE
    target_id UUID;
    target_business UUID;
    target_activity UUID;
    plan sample_cost_plan_versions%ROWTYPE;
    replacement sample_cost_plan_versions%ROWTYPE;
    line_count BIGINT;
    line_sum NUMERIC;
BEGIN
    IF TG_TABLE_NAME = 'sample_cost_plan_versions' THEN target_id := COALESCE(NEW.id,OLD.id);
    ELSE target_id := COALESCE(NEW.plan_version_id,OLD.plan_version_id); END IF;
    target_business := COALESCE(NEW.business_id,OLD.business_id);
    target_activity := COALESCE(NEW.activity_id,OLD.activity_id);
    SELECT * INTO plan FROM sample_cost_plan_versions
        WHERE business_id = target_business AND activity_id = target_activity AND id = target_id;
    IF plan.id IS NULL THEN RETURN NULL; END IF;
    SELECT count(*), COALESCE(sum(planned_line_cost),0) INTO line_count,line_sum
        FROM sample_cost_plan_lines WHERE business_id = target_business AND activity_id = target_activity AND plan_version_id = target_id;
    IF line_count < 1 OR line_count > 100 OR line_sum IS DISTINCT FROM plan.planned_cost THEN
        RAISE EXCEPTION 'Plan must contain 1..100 lines whose exact total equals planned cost' USING ERRCODE = '23514';
    END IF;
    IF plan.status = 'SUPERSEDED' THEN
        SELECT * INTO replacement FROM sample_cost_plan_versions WHERE business_id = target_business
            AND activity_id = target_activity AND id = plan.superseded_by_plan_id;
        IF replacement.status IS NULL OR replacement.status NOT IN ('APPROVED','SUPERSEDED') OR replacement.revision <= plan.revision THEN
            RAISE EXCEPTION 'Supersession requires a later approved revision in the same activity' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
CREATE CONSTRAINT TRIGGER trg_sample_plan_consistency AFTER INSERT OR UPDATE ON sample_cost_plan_versions
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_sample_plan_consistency();
CREATE CONSTRAINT TRIGGER trg_sample_line_consistency AFTER INSERT OR UPDATE OR DELETE ON sample_cost_plan_lines
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_sample_plan_consistency();

REVOKE ALL ON sample_activities, sample_cost_plan_versions, sample_cost_plan_lines FROM PUBLIC;
DO $$ BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE ALL ON sample_activities, sample_cost_plan_versions, sample_cost_plan_lines FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON sample_activities, sample_cost_plan_versions, sample_cost_plan_lines TO finsight_app;
    END IF;
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        REVOKE ALL ON sample_activities, sample_cost_plan_versions, sample_cost_plan_lines FROM finsight_backup;
        GRANT SELECT ON sample_activities, sample_cost_plan_versions, sample_cost_plan_lines TO finsight_backup;
    END IF;
END $$;
