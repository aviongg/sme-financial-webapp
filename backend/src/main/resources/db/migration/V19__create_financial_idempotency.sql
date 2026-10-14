-- V19: Financial Idempotency Records
-- Author: Antigravity (Developer A Backend)
-- Approved as SULEMAN_AUTHORIZED pending Fatima notification. Collision check confirmed clean against remote origin/main and origin/dev/fatima (both at V17).

CREATE TABLE IF NOT EXISTS financial_idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    operation VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    resource_id UUID,
    response_status INT NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_financial_idempotency UNIQUE (business_id, operation, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_financial_idempotency_lookup
    ON financial_idempotency_records (business_id, operation, idempotency_key);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON financial_idempotency_records TO finsight_app;
    END IF;
END $$;
