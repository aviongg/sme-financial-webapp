-- F06 Phase B: persisted planning estimates, revision history and exact-fit conversion lineage into F05.
-- All amounts are PLANNED. No ledger event, cash, revenue/expense recognition, receivable, payable,
-- invoice/bill, inventory, COGS, actual project figure, MonthlyRecord or health-score change is made.
-- Conversion only creates an F05 DRAFT planning budget through the F05 service plus one lineage row here.
--
-- Numeric capacities are derived from the verified F06 Phase A accepted domain (f06-preview-v1), never
-- narrowed to F05. Every input is a plain decimal with 1..12 integer and 0..12 fractional digits, so
-- every input value is < 10^12 with scale <= 12:
--   quantity, unit_cost, pricing_value, saleable_quantity      NUMERIC(24, 12)
--   line planned_cost = quantity * unit_cost (exact)           < 10^24, scale <= 24  -> NUMERIC(48, 24)
--   total_planned_cost = sum of <= 100 lines (exact)           < 10^26, scale <= 24  -> NUMERIC(50, 24)
--   planned_batch_price: PROPOSED_PRICE = value                < 10^12, scale <= 12
--                        TARGET_MARGIN  = C / (1 - m), m < 1 so 1 - m >= 10^-12, division at scale 24
--                                                             < 10^38, scale  = 24
--                        TARGET_MARKUP  = C * (1 + u) (exact) < 10^39, scale <= 36  -> NUMERIC(75, 36)
--   planned_contribution = P - C (exact, may be negative)      |x| < 10^39, scale <= 36 -> NUMERIC(75, 36)
--   planned_margin = (P - C) / P, P > 0 (P >= 10^-24)           -10^38 < x <= 1, scale 24 -> NUMERIC(63, 24)
--   planned_markup = (P - C) / C, C > 0 (C >= 10^-24)           -1 <= x < 10^63, scale 24 -> NUMERIC(87, 24)
--   planned_per_unit_price = P / q, q > 0 (q >= 10^-12)        < 10^51, scale 24      -> NUMERIC(75, 24)
-- Division uses scale 24 HALF_EVEN as CALCULATION precision only (f06-preview-v1). It is not an accounting,
-- settlement or currency rounding policy; P10 remains open. No stored value is ever quantized by these columns.

CREATE TABLE estimates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    base_currency CHAR(3) NOT NULL DEFAULT 'PKR' CHECK (base_currency = 'PKR'),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    updated_by_user_id UUID NOT NULL REFERENCES app_users(id),
    archived_by_user_id UUID NULL REFERENCES app_users(id),
    archived_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_estimates_scoped_identity UNIQUE (id, business_id),
    CONSTRAINT uq_estimates_business_code UNIQUE (business_id, code),
    CONSTRAINT chk_estimates_code_normalized CHECK (
        btrim(code) <> ''
        AND code = upper(btrim(code))
        AND code ~ '^[A-Z0-9][A-Z0-9._/-]{0,39}$'
    ),
    CONSTRAINT chk_estimates_name_nonblank CHECK (btrim(name) <> ''),
    CONSTRAINT chk_estimates_description_nonblank CHECK (description IS NULL OR btrim(description) <> ''),
    CONSTRAINT chk_estimates_archive_state CHECK (
        (status = 'ACTIVE' AND archived_by_user_id IS NULL AND archived_at IS NULL)
        OR (status = 'ARCHIVED' AND archived_by_user_id IS NOT NULL AND archived_at IS NOT NULL)
    )
);

CREATE INDEX idx_estimates_business_status_updated
    ON estimates (business_id, status, updated_at DESC, id);

CREATE TABLE estimate_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    estimate_id UUID NOT NULL,
    revision INT NOT NULL CHECK (revision > 0),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'SUPERSEDED')),
    -- Canonical f06-preview-v1 inputs (the PricingPreviewRequest assumptions; lines are in estimate_lines).
    pricing_mode VARCHAR(16) NOT NULL CHECK (pricing_mode IN ('PROPOSED_PRICE', 'TARGET_MARGIN', 'TARGET_MARKUP')),
    pricing_value NUMERIC(24, 12) NOT NULL CHECK (pricing_value >= 0),
    saleable_quantity NUMERIC(24, 12) NULL CHECK (saleable_quantity IS NULL OR saleable_quantity >= 0),
    -- Backend-authoritative f06-preview-v1 outputs (group subtotals are derived from the stored lines).
    total_planned_cost NUMERIC(50, 24) NOT NULL CHECK (total_planned_cost >= 0),
    planned_batch_price NUMERIC(75, 36) NOT NULL CHECK (planned_batch_price >= 0),
    planned_contribution NUMERIC(75, 36) NOT NULL,
    planned_margin NUMERIC(63, 24) NULL,
    planned_markup NUMERIC(87, 24) NULL,
    planned_per_unit_price NUMERIC(75, 24) NULL,
    planned_margin_unavailable_reason VARCHAR(16) NULL
        CHECK (planned_margin_unavailable_reason IN ('ZERO_PRICE')),
    planned_markup_unavailable_reason VARCHAR(16) NULL
        CHECK (planned_markup_unavailable_reason IN ('ZERO_COST')),
    planned_per_unit_price_unavailable_reason VARCHAR(32) NULL
        CHECK (planned_per_unit_price_unavailable_reason IN ('SALEABLE_QUANTITY_UNKNOWN', 'ZERO_SALEABLE_QUANTITY')),
    calculation_version VARCHAR(32) NOT NULL DEFAULT 'f06-preview-v1'
        CHECK (calculation_version = 'f06-preview-v1'),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    updated_by_user_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by_user_id UUID NULL REFERENCES app_users(id),
    approved_at TIMESTAMPTZ NULL,
    superseded_by_version_id UUID NULL,
    superseded_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_estimate_versions_scoped_identity UNIQUE (id, business_id),
    CONSTRAINT uq_estimate_versions_estimate_identity UNIQUE (business_id, estimate_id, id),
    CONSTRAINT uq_estimate_versions_revision UNIQUE (business_id, estimate_id, revision),
    CONSTRAINT fk_estimate_versions_estimate
        FOREIGN KEY (estimate_id, business_id) REFERENCES estimates (id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_estimate_versions_superseded_by
        FOREIGN KEY (business_id, estimate_id, superseded_by_version_id)
        REFERENCES estimate_versions (business_id, estimate_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_estimate_versions_target_margin_range
        CHECK (pricing_mode <> 'TARGET_MARGIN' OR pricing_value < 1),
    -- Exact identities the database can verify without division. TARGET_MARGIN uses scale-24 division and is
    -- verified by the application calculator (f06-preview-v1) rather than by PostgreSQL's own division scale.
    CONSTRAINT chk_estimate_versions_proposed_price
        CHECK (pricing_mode <> 'PROPOSED_PRICE' OR planned_batch_price = pricing_value),
    CONSTRAINT chk_estimate_versions_target_markup
        CHECK (pricing_mode <> 'TARGET_MARKUP' OR planned_batch_price = total_planned_cost * (1 + pricing_value)),
    CONSTRAINT chk_estimate_versions_contribution
        CHECK (planned_contribution = planned_batch_price - total_planned_cost),
    CONSTRAINT chk_estimate_versions_margin_availability CHECK (
        (planned_batch_price = 0 AND planned_margin IS NULL AND planned_margin_unavailable_reason = 'ZERO_PRICE')
        OR (planned_batch_price > 0 AND planned_margin IS NOT NULL AND planned_margin_unavailable_reason IS NULL)
    ),
    CONSTRAINT chk_estimate_versions_markup_availability CHECK (
        (total_planned_cost = 0 AND planned_markup IS NULL AND planned_markup_unavailable_reason = 'ZERO_COST')
        OR (total_planned_cost > 0 AND planned_markup IS NOT NULL AND planned_markup_unavailable_reason IS NULL)
    ),
    CONSTRAINT chk_estimate_versions_per_unit_availability CHECK (
        (saleable_quantity IS NULL AND planned_per_unit_price IS NULL
            AND planned_per_unit_price_unavailable_reason = 'SALEABLE_QUANTITY_UNKNOWN')
        OR (saleable_quantity = 0 AND planned_per_unit_price IS NULL
            AND planned_per_unit_price_unavailable_reason = 'ZERO_SALEABLE_QUANTITY')
        OR (saleable_quantity > 0 AND planned_per_unit_price IS NOT NULL
            AND planned_per_unit_price_unavailable_reason IS NULL)
    ),
    CONSTRAINT chk_estimate_versions_approval_state CHECK (
        (status = 'DRAFT'
            AND approved_by_user_id IS NULL AND approved_at IS NULL
            AND superseded_by_version_id IS NULL AND superseded_at IS NULL)
        OR (status = 'APPROVED'
            AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
            AND superseded_by_version_id IS NULL AND superseded_at IS NULL)
        OR (status = 'SUPERSEDED'
            AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL
            AND superseded_by_version_id IS NOT NULL AND superseded_by_version_id <> id
            AND superseded_at IS NOT NULL)
    )
);

-- At most ONE open DRAFT and ONE active APPROVED revision per estimate.
CREATE UNIQUE INDEX uq_estimate_versions_one_draft
    ON estimate_versions (business_id, estimate_id) WHERE status = 'DRAFT';
CREATE UNIQUE INDEX uq_estimate_versions_one_approved
    ON estimate_versions (business_id, estimate_id) WHERE status = 'APPROVED';
CREATE INDEX idx_estimate_versions_estimate_revision
    ON estimate_versions (business_id, estimate_id, revision DESC);

CREATE TABLE estimate_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    estimate_id UUID NOT NULL,
    estimate_version_id UUID NOT NULL,
    line_number INT NOT NULL CHECK (line_number >= 1 AND line_number <= 100),
    cost_group VARCHAR(16) NOT NULL CHECK (cost_group IN ('MATERIAL', 'LABOUR', 'OVERHEAD', 'FREIGHT', 'OTHER')),
    description VARCHAR(200) NOT NULL,
    quantity NUMERIC(24, 12) NOT NULL CHECK (quantity >= 0),
    unit VARCHAR(40) NOT NULL,
    unit_cost NUMERIC(24, 12) NOT NULL CHECK (unit_cost >= 0),
    -- Exact product; no MathContext or currency rounding (f06-preview-v1).
    planned_cost NUMERIC(48, 24) NOT NULL CHECK (planned_cost >= 0 AND planned_cost = quantity * unit_cost),
    CONSTRAINT uq_estimate_lines_order UNIQUE (business_id, estimate_version_id, line_number),
    -- Phase A stores stripped, nonblank text without C0/C1 control characters.
    CONSTRAINT chk_estimate_lines_description CHECK (
        btrim(description) <> '' AND description = btrim(description) AND description !~ '[\x01-\x1f\x7f-\x9f]'
    ),
    CONSTRAINT chk_estimate_lines_unit CHECK (
        btrim(unit) <> '' AND unit = btrim(unit) AND unit !~ '[\x01-\x1f\x7f-\x9f]'
    ),
    CONSTRAINT fk_estimate_lines_version
        FOREIGN KEY (business_id, estimate_id, estimate_version_id)
        REFERENCES estimate_versions (business_id, estimate_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_estimate_lines_version
    ON estimate_lines (business_id, estimate_version_id, line_number);

-- Immutable conversion lineage. One APPROVED estimate revision converts at most once (Phase B).
-- F05 remains the authority for its own rows: the target project and DRAFT budget are not foreign keys,
-- so F05 can still discard its draft or delete its draft project under its existing V33 rules. Their
-- same-business existence is verified when the lineage row is written.
CREATE TABLE estimate_project_conversions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    estimate_id UUID NOT NULL,
    estimate_version_id UUID NOT NULL,
    project_id UUID NOT NULL,
    project_budget_version_id UUID NOT NULL,
    conversion_contract_version VARCHAR(32) NOT NULL DEFAULT 'f06-f05-exact-v1'
        CHECK (conversion_contract_version = 'f06-f05-exact-v1'),
    source_calculation_version VARCHAR(32) NOT NULL CHECK (source_calculation_version = 'f06-preview-v1'),
    target_calculation_version VARCHAR(32) NOT NULL CHECK (target_calculation_version = 'f05-plan-v1'),
    converted_by_user_id UUID NOT NULL REFERENCES app_users(id),
    converted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_estimate_project_conversions_source UNIQUE (business_id, estimate_version_id),
    CONSTRAINT uq_estimate_project_conversions_target UNIQUE (business_id, project_budget_version_id),
    CONSTRAINT fk_estimate_project_conversions_version
        FOREIGN KEY (business_id, estimate_id, estimate_version_id)
        REFERENCES estimate_versions (business_id, estimate_id, id) ON DELETE RESTRICT
);

CREATE INDEX idx_estimate_project_conversions_estimate
    ON estimate_project_conversions (business_id, estimate_id, converted_at DESC);

-- 1. TRUNCATE is forbidden on every estimate table.
-- 2. Estimates are created ACTIVE. ARCHIVED is terminal; archiving requires no open DRAFT revision.
--    Deletion is allowed only for an ACTIVE estimate without APPROVED/SUPERSEDED history.
-- 3. Identity, currency, creation provenance and the monotonic version of every row are immutable.
-- 4. Revisions are created DRAFT under an ACTIVE estimate. DRAFT content may change while the estimate is ACTIVE.
--    DRAFT -> APPROVED requires stored lines whose exact sum equals total_planned_cost.
-- 5. APPROVED content is immutable except for the atomic APPROVED -> SUPERSEDED transition; SUPERSEDED is final.
--    Only DRAFT revisions can be deleted.
-- 6. Lines can change only while both their original and new parent revision are DRAFT.
-- 7. Conversion lineage is insert-only, from an APPROVED revision into a same-business F05 DRAFT budget.
CREATE FUNCTION enforce_estimate_planning_immutability() RETURNS TRIGGER AS $$
DECLARE
    parent_status VARCHAR(16);
    estimate_status VARCHAR(12);
    has_rows BOOLEAN;
    line_count INT;
    line_sum NUMERIC;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'Estimate planning tables cannot be truncated'
            USING ERRCODE = '55000';
    END IF;

    IF TG_TABLE_NAME = 'estimates' THEN
        IF TG_OP = 'INSERT' THEN
            IF NEW.status <> 'ACTIVE' THEN
                RAISE EXCEPTION 'Estimates must be created ACTIVE'
                    USING ERRCODE = '55000';
            END IF;
            RETURN NEW;
        END IF;

        IF TG_OP = 'DELETE' THEN
            IF OLD.status <> 'ACTIVE' THEN
                RAISE EXCEPTION 'Archived estimates are permanent and cannot be deleted'
                    USING ERRCODE = '55000';
            END IF;
            SELECT EXISTS (
                SELECT 1 FROM estimate_versions
                WHERE business_id = OLD.business_id AND estimate_id = OLD.id AND status IN ('APPROVED', 'SUPERSEDED')
            ) INTO has_rows;
            IF has_rows THEN
                RAISE EXCEPTION 'Estimates with approved revision history cannot be deleted; archive them instead'
                    USING ERRCODE = '55000';
            END IF;
            RETURN OLD;
        END IF;

        IF NEW.id IS DISTINCT FROM OLD.id
           OR NEW.business_id IS DISTINCT FROM OLD.business_id
           OR NEW.base_currency IS DISTINCT FROM OLD.base_currency
           OR NEW.created_by_user_id IS DISTINCT FROM OLD.created_by_user_id
           OR NEW.created_at IS DISTINCT FROM OLD.created_at
           OR NEW.version <= OLD.version
           OR OLD.status = 'ARCHIVED' THEN
            RAISE EXCEPTION 'Estimate identity, currency, creation provenance, archived state and monotonic version are immutable'
                USING ERRCODE = '55000';
        END IF;
        IF NEW.status = 'ARCHIVED' THEN
            SELECT EXISTS (
                SELECT 1 FROM estimate_versions
                WHERE business_id = OLD.business_id AND estimate_id = OLD.id AND status = 'DRAFT'
            ) INTO has_rows;
            IF has_rows THEN
                RAISE EXCEPTION 'Approve or discard the open draft revision before archiving this estimate'
                    USING ERRCODE = '55000';
            END IF;
        END IF;
        RETURN NEW;
    END IF;

    IF TG_TABLE_NAME = 'estimate_versions' THEN
        IF TG_OP = 'DELETE' THEN
            IF OLD.status <> 'DRAFT' THEN
                RAISE EXCEPTION 'Approved and superseded estimate revisions are permanent history and cannot be deleted'
                    USING ERRCODE = '55000';
            END IF;
            RETURN OLD;
        END IF;

        SELECT status INTO estimate_status
        FROM estimates WHERE id = NEW.estimate_id AND business_id = NEW.business_id;

        IF TG_OP = 'INSERT' THEN
            IF NEW.status <> 'DRAFT' OR estimate_status IS DISTINCT FROM 'ACTIVE' THEN
                RAISE EXCEPTION 'Estimate revisions must be created as DRAFT under an active estimate'
                    USING ERRCODE = '55000';
            END IF;
            RETURN NEW;
        END IF;

        IF NEW.id IS DISTINCT FROM OLD.id
           OR NEW.business_id IS DISTINCT FROM OLD.business_id
           OR NEW.estimate_id IS DISTINCT FROM OLD.estimate_id
           OR NEW.revision IS DISTINCT FROM OLD.revision
           OR NEW.created_by_user_id IS DISTINCT FROM OLD.created_by_user_id
           OR NEW.created_at IS DISTINCT FROM OLD.created_at
           OR NEW.version <= OLD.version THEN
            RAISE EXCEPTION 'Estimate revision identity, creation provenance and monotonic version are immutable'
                USING ERRCODE = '55000';
        END IF;

        IF OLD.status = 'SUPERSEDED' THEN
            RAISE EXCEPTION 'Superseded estimate revisions are immutable'
                USING ERRCODE = '55000';
        END IF;

        IF OLD.status = 'APPROVED' THEN
            IF NEW.status <> 'SUPERSEDED'
               OR NEW.pricing_mode IS DISTINCT FROM OLD.pricing_mode
               OR NEW.pricing_value IS DISTINCT FROM OLD.pricing_value
               OR NEW.saleable_quantity IS DISTINCT FROM OLD.saleable_quantity
               OR NEW.total_planned_cost IS DISTINCT FROM OLD.total_planned_cost
               OR NEW.planned_batch_price IS DISTINCT FROM OLD.planned_batch_price
               OR NEW.planned_contribution IS DISTINCT FROM OLD.planned_contribution
               OR NEW.planned_margin IS DISTINCT FROM OLD.planned_margin
               OR NEW.planned_markup IS DISTINCT FROM OLD.planned_markup
               OR NEW.planned_per_unit_price IS DISTINCT FROM OLD.planned_per_unit_price
               OR NEW.planned_margin_unavailable_reason IS DISTINCT FROM OLD.planned_margin_unavailable_reason
               OR NEW.planned_markup_unavailable_reason IS DISTINCT FROM OLD.planned_markup_unavailable_reason
               OR NEW.planned_per_unit_price_unavailable_reason IS DISTINCT FROM OLD.planned_per_unit_price_unavailable_reason
               OR NEW.calculation_version IS DISTINCT FROM OLD.calculation_version
               OR NEW.approved_by_user_id IS DISTINCT FROM OLD.approved_by_user_id
               OR NEW.approved_at IS DISTINCT FROM OLD.approved_at
               OR NEW.version <> OLD.version + 1 THEN
                RAISE EXCEPTION 'Approved estimate revisions are immutable except for atomic supersession'
                    USING ERRCODE = '55000';
            END IF;
            RETURN NEW;
        END IF;

        -- OLD.status = 'DRAFT'
        IF estimate_status IS DISTINCT FROM 'ACTIVE' THEN
            RAISE EXCEPTION 'Revisions of an archived estimate cannot change'
                USING ERRCODE = '55000';
        END IF;
        IF NEW.status = 'SUPERSEDED' THEN
            RAISE EXCEPTION 'A draft estimate revision cannot be superseded; approve or delete it'
                USING ERRCODE = '55000';
        END IF;
        IF NEW.status = 'APPROVED' THEN
            SELECT count(*), sum(planned_cost) INTO line_count, line_sum
            FROM estimate_lines
            WHERE business_id = NEW.business_id AND estimate_version_id = NEW.id;
            IF line_count = 0 OR line_sum IS DISTINCT FROM NEW.total_planned_cost THEN
                RAISE EXCEPTION 'An estimate revision can be approved only when its stored lines sum exactly to its total planned cost'
                    USING ERRCODE = '55000';
            END IF;
        END IF;
        RETURN NEW;
    END IF;

    IF TG_TABLE_NAME = 'estimate_lines' THEN
        -- Check the original parent too: moving a line into a DRAFT must not remove it from history.
        IF TG_OP IN ('UPDATE', 'DELETE') THEN
            SELECT status INTO parent_status
            FROM estimate_versions
            WHERE business_id = OLD.business_id AND estimate_id = OLD.estimate_id AND id = OLD.estimate_version_id;
            -- A missing parent is only possible during the cascade from an allowed DRAFT deletion.
            IF parent_status IN ('APPROVED', 'SUPERSEDED') THEN
                RAISE EXCEPTION 'Lines of approved or superseded estimate revisions are immutable'
                    USING ERRCODE = '55000';
            END IF;
        END IF;
        IF TG_OP IN ('INSERT', 'UPDATE') THEN
            SELECT status INTO parent_status
            FROM estimate_versions
            WHERE business_id = NEW.business_id AND estimate_id = NEW.estimate_id AND id = NEW.estimate_version_id;
            IF parent_status IS DISTINCT FROM 'DRAFT' THEN
                RAISE EXCEPTION 'Lines can be changed only on draft estimate revisions'
                    USING ERRCODE = '55000';
            END IF;
            RETURN NEW;
        END IF;
        RETURN OLD;
    END IF;

    IF TG_TABLE_NAME = 'estimate_project_conversions' THEN
        IF TG_OP <> 'INSERT' THEN
            RAISE EXCEPTION 'Estimate conversion lineage is immutable'
                USING ERRCODE = '55000';
        END IF;
        SELECT status INTO parent_status
        FROM estimate_versions
        WHERE business_id = NEW.business_id AND estimate_id = NEW.estimate_id AND id = NEW.estimate_version_id;
        IF parent_status IS DISTINCT FROM 'APPROVED' THEN
            RAISE EXCEPTION 'Only an approved estimate revision can be converted'
                USING ERRCODE = '55000';
        END IF;
        SELECT EXISTS (
            SELECT 1 FROM project_budget_versions
            WHERE business_id = NEW.business_id AND project_id = NEW.project_id
              AND id = NEW.project_budget_version_id AND status = 'DRAFT'
        ) INTO has_rows;
        IF NOT has_rows THEN
            RAISE EXCEPTION 'Conversion lineage must reference a same-business F05 draft planning budget'
                USING ERRCODE = '55000';
        END IF;
        RETURN NEW;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_estimates_immutability
    BEFORE INSERT OR UPDATE OR DELETE ON estimates
    FOR EACH ROW EXECUTE FUNCTION enforce_estimate_planning_immutability();
CREATE TRIGGER trg_estimates_no_truncate
    BEFORE TRUNCATE ON estimates
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_estimate_planning_immutability();

CREATE TRIGGER trg_estimate_versions_immutability
    BEFORE INSERT OR UPDATE OR DELETE ON estimate_versions
    FOR EACH ROW EXECUTE FUNCTION enforce_estimate_planning_immutability();
CREATE TRIGGER trg_estimate_versions_no_truncate
    BEFORE TRUNCATE ON estimate_versions
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_estimate_planning_immutability();

CREATE TRIGGER trg_estimate_lines_immutability
    BEFORE INSERT OR UPDATE OR DELETE ON estimate_lines
    FOR EACH ROW EXECUTE FUNCTION enforce_estimate_planning_immutability();
CREATE TRIGGER trg_estimate_lines_no_truncate
    BEFORE TRUNCATE ON estimate_lines
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_estimate_planning_immutability();

CREATE TRIGGER trg_estimate_project_conversions_immutability
    BEFORE INSERT OR UPDATE OR DELETE ON estimate_project_conversions
    FOR EACH ROW EXECUTE FUNCTION enforce_estimate_planning_immutability();
CREATE TRIGGER trg_estimate_project_conversions_no_truncate
    BEFORE TRUNCATE ON estimate_project_conversions
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_estimate_planning_immutability();

-- Least-privilege runtime and backup grants. Draft-only deletion is enforced by the trigger;
-- conversion lineage is insert-only, so the runtime role has no UPDATE or DELETE on it.
REVOKE ALL ON estimates, estimate_versions, estimate_lines, estimate_project_conversions FROM PUBLIC;
DO $$ BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE ALL ON estimates, estimate_versions, estimate_lines, estimate_project_conversions FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON estimates, estimate_versions, estimate_lines TO finsight_app;
        GRANT SELECT, INSERT ON estimate_project_conversions TO finsight_app;
    END IF;
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        REVOKE ALL ON estimates, estimate_versions, estimate_lines, estimate_project_conversions FROM finsight_backup;
        GRANT SELECT ON estimates, estimate_versions, estimate_lines, estimate_project_conversions TO finsight_backup;
    END IF;
END $$;
