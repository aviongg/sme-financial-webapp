-- V40: Create FX Recognition Evidence and Reconciliation Schema
-- Author: Google Antigravity (Sole Full-Stack Developer A Engineer & Financial Systems Architect)
-- Feature: F11C Realized FX Differences, Bank Fee Reconciliation & Settlement Accounting Evidence

-- 1. Create fx_obligation_recognition_evidence table
CREATE TABLE IF NOT EXISTS fx_obligation_recognition_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    obligation_id UUID NOT NULL REFERENCES financial_obligations(id) ON DELETE RESTRICT,
    native_currency VARCHAR(3) NOT NULL,
    native_amount NUMERIC(19, 4) NOT NULL
        CHECK (native_amount > 0),
    functional_currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    recognition_rate NUMERIC(19, 6) NOT NULL
        CHECK (recognition_rate > 0),
    rate_convention VARCHAR(32) NOT NULL DEFAULT 'BASE_TO_QUOTE',
    book_value NUMERIC(19, 4) NOT NULL
        CHECK (book_value > 0),
    recognition_date DATE NOT NULL,
    recognition_source VARCHAR(64) NOT NULL DEFAULT 'MANUAL_DECLARATION',
    evidence_provenance VARCHAR(64) NOT NULL DEFAULT 'VERIFIED_AT_CREATION',
    notes VARCHAR(1000),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_fx_recognition_obligation UNIQUE (obligation_id),
    CONSTRAINT uq_fx_recognition_id_biz UNIQUE (id, business_id)
);

CREATE INDEX IF NOT EXISTS idx_fx_recognition_biz_ob ON fx_obligation_recognition_evidence(business_id, obligation_id);
CREATE INDEX IF NOT EXISTS idx_fx_recognition_biz_date ON fx_obligation_recognition_evidence(business_id, recognition_date);

-- 2. Add reconciliation columns to fx_obligation_settlements
ALTER TABLE fx_obligation_settlements ADD COLUMN IF NOT EXISTS allocated_book_value NUMERIC(19, 4);
ALTER TABLE fx_obligation_settlements ADD COLUMN IF NOT EXISTS realized_fx_difference NUMERIC(19, 4);
ALTER TABLE fx_obligation_settlements ADD COLUMN IF NOT EXISTS realized_fx_impact VARCHAR(16)
    CHECK (realized_fx_impact IN ('GAIN', 'LOSS', 'NEUTRAL') OR realized_fx_impact IS NULL);
ALTER TABLE fx_obligation_settlements ADD COLUMN IF NOT EXISTS recognition_evidence_status VARCHAR(32) NOT NULL DEFAULT 'UNAVAILABLE'
    CHECK (recognition_evidence_status IN ('AVAILABLE', 'UNAVAILABLE', 'EXCLUDED'));
ALTER TABLE fx_obligation_settlements ADD COLUMN IF NOT EXISTS missing_evidence_reason VARCHAR(255);

-- 3. Grant least-privilege runtime DML permissions
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE DELETE ON TABLE fx_obligation_recognition_evidence FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE fx_obligation_recognition_evidence TO finsight_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON TABLE fx_obligation_recognition_evidence TO finsight_backup;
    END IF;
END $$;

