-- V52: Transaction-driven monthly financial model (product completion).
--
-- monthly_records stops being an independently user-maintained source of financial truth. Every month inside a
-- business's ledger coverage is a DERIVED projection materialised by the monthly projector (projection version
-- monthly-projection-v1) from authoritative daily records (ledger transactions/movements, obligations and
-- allocations, F04 adjustments, F08 inventory movements, C08 commission, C09 FX). Rows written before this
-- migration are kept as LEGACY_MANUAL history; the projector replaces any of them that fall inside coverage.
--
-- Genuinely unavailable financing facts (loan balance, interest expense, financing type) have no ledger authority
-- yet; they move to an explicit supplemental table and are copied into the projection, labelled as manual.
--
-- Any write to an authoritative source table marks the business's projection dirty (monotonic sequence). The
-- projector recomputes every month in coverage, persists only months whose fingerprint changed and rescores them.

ALTER TABLE monthly_records
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'LEGACY_MANUAL'
        CHECK (source IN ('LEGACY_MANUAL', 'DERIVED')),
    ADD COLUMN projection_version VARCHAR(40) NULL,
    ADD COLUMN source_fingerprint CHAR(64) NULL,
    ADD COLUMN period_complete BOOLEAN NULL,
    ADD COLUMN projected_at TIMESTAMPTZ NULL,
    ADD COLUMN coverage_notes TEXT NULL;

ALTER TABLE monthly_records ADD CONSTRAINT chk_monthly_records_derived_metadata CHECK (
    source = 'LEGACY_MANUAL'
    OR (projection_version IS NOT NULL AND source_fingerprint IS NOT NULL
        AND period_complete IS NOT NULL AND projected_at IS NOT NULL)
);

CREATE TABLE monthly_financing_inputs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    month CHAR(7) NOT NULL CHECK (month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    loan_outstanding NUMERIC(14, 2) NULL CHECK (loan_outstanding IS NULL OR loan_outstanding >= 0),
    interest_expense NUMERIC(14, 2) NULL CHECK (interest_expense IS NULL OR interest_expense >= 0),
    financing_type VARCHAR(15) NOT NULL DEFAULT 'none' CHECK (financing_type IN ('none', 'conventional', 'islamic')),
    origin VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (origin IN ('USER', 'LEGACY_MIGRATION')),
    updated_by UUID NULL REFERENCES app_users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_monthly_financing_inputs UNIQUE (business_id, month),
    CONSTRAINT chk_monthly_financing_actor CHECK (origin = 'LEGACY_MIGRATION' OR updated_by IS NOT NULL)
);

-- Preserve supplemental financing facts users already supplied in legacy monthly rows.
INSERT INTO monthly_financing_inputs (business_id, month, loan_outstanding, interest_expense, financing_type, origin,
                                      updated_at)
SELECT r.user_id, r.month, r.loan_outstanding, r.interest_expense, r.financing_type, 'LEGACY_MIGRATION', now()
FROM monthly_records r
JOIN businesses b ON b.id = r.user_id
WHERE r.loan_outstanding IS NOT NULL OR r.interest_expense IS NOT NULL OR r.financing_type <> 'none';

CREATE TABLE monthly_projection_state (
    business_id UUID PRIMARY KEY REFERENCES businesses(id) ON DELETE CASCADE,
    dirty BOOLEAN NOT NULL DEFAULT true,
    dirty_seq BIGINT NOT NULL DEFAULT 1 CHECK (dirty_seq >= 0),
    dirty_since TIMESTAMPTZ NULL DEFAULT now(),
    projected_through CHAR(7) NULL,
    last_projected_at TIMESTAMPTZ NULL,
    last_error VARCHAR(300) NULL
);

INSERT INTO monthly_projection_state (business_id) SELECT id FROM businesses;

CREATE OR REPLACE FUNCTION mark_monthly_projection_dirty() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    target UUID;
BEGIN
    IF TG_OP = 'DELETE' THEN
        -- A delete may be part of a cascading business deletion: never insert a state row for it, only flag an
        -- existing one (the state row cascades away with its business).
        UPDATE monthly_projection_state
           SET dirty = true, dirty_seq = dirty_seq + 1, dirty_since = COALESCE(dirty_since, now())
         WHERE business_id = OLD.business_id;
        RETURN NULL;
    END IF;
    target := NEW.business_id;
    IF target IS NOT NULL THEN
        INSERT INTO monthly_projection_state AS s (business_id, dirty, dirty_seq, dirty_since)
        VALUES (target, true, 1, now())
        ON CONFLICT (business_id) DO UPDATE
            SET dirty = true, dirty_seq = s.dirty_seq + 1, dirty_since = COALESCE(s.dirty_since, now());
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.business_id IS DISTINCT FROM NEW.business_id AND OLD.business_id IS NOT NULL THEN
        UPDATE monthly_projection_state
           SET dirty = true, dirty_seq = dirty_seq + 1, dirty_since = COALESCE(dirty_since, now())
         WHERE business_id = OLD.business_id;
    END IF;
    RETURN NULL;
END;
$$;

DO $$
DECLARE
    source_table TEXT;
BEGIN
    FOREACH source_table IN ARRAY ARRAY[
        'financial_accounts', 'financial_transactions', 'account_movements',
        'financial_obligations', 'obligation_payment_allocations',
        'commercial_adjustments', 'inventory_movements',
        'commission_earnings', 'commission_payouts', 'commission_advances',
        'fx_obligation_settlements', 'monthly_financing_inputs'
    ] LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_monthly_projection_dirty AFTER INSERT OR UPDATE OR DELETE ON %I '
                || 'FOR EACH ROW EXECUTE FUNCTION mark_monthly_projection_dirty()',
            source_table, source_table);
    END LOOP;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE monthly_financing_inputs TO finsight_app;
        REVOKE DELETE, TRUNCATE ON TABLE monthly_financing_inputs FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE monthly_projection_state TO finsight_app;
        REVOKE DELETE, TRUNCATE ON TABLE monthly_projection_state FROM finsight_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON TABLE monthly_financing_inputs, monthly_projection_state TO finsight_backup;
    END IF;
END $$;
