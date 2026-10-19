-- V25: Create Counterparties Schema and Linkages (A-F03-COUNTERPARTIES)
-- Author: Antigravity (Developer A Backend Coordinator)

-- 1. Create counterparties table
CREATE TABLE IF NOT EXISTS counterparties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    display_name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    email VARCHAR(255),
    phone VARCHAR(50),
    address VARCHAR(500),
    reference_code VARCHAR(100),
    notes VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_counterparties_id_business UNIQUE (id, business_id),
    CONSTRAINT ck_counterparties_type CHECK (type IN ('CUSTOMER', 'SUPPLIER', 'BOTH')),
    CONSTRAINT ck_counterparties_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_counterparties_business_status
    ON counterparties (business_id, status);

CREATE INDEX IF NOT EXISTS idx_counterparties_business_type
    ON counterparties (business_id, type);

CREATE INDEX IF NOT EXISTS idx_counterparties_business_name
    ON counterparties (business_id, display_name);

CREATE INDEX IF NOT EXISTS idx_counterparties_business_lower_name
    ON counterparties (business_id, LOWER(display_name));

-- 2. Add counterparty_id to financial_transactions
ALTER TABLE financial_transactions
    ADD COLUMN IF NOT EXISTS counterparty_id UUID;

ALTER TABLE financial_transactions
    DROP CONSTRAINT IF EXISTS fk_fin_tx_counterparty;

ALTER TABLE financial_transactions
    ADD CONSTRAINT fk_fin_tx_counterparty
    FOREIGN KEY (counterparty_id, business_id)
    REFERENCES counterparties (id, business_id)
    ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_fin_tx_counterparty
    ON financial_transactions (business_id, counterparty_id);

-- 3. Add counterparty_id to record_drafts (composite tenant foreign key with ON DELETE RESTRICT)
ALTER TABLE record_drafts
    ADD COLUMN IF NOT EXISTS counterparty_id UUID;

ALTER TABLE record_drafts
    DROP CONSTRAINT IF EXISTS fk_record_drafts_counterparty;

ALTER TABLE record_drafts
    ADD CONSTRAINT fk_record_drafts_counterparty
    FOREIGN KEY (counterparty_id, business_id)
    REFERENCES counterparties (id, business_id)
    ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_record_drafts_counterparty
    ON record_drafts (business_id, counterparty_id);

-- 4. Runtime role privileges for finsight_app (Least privilege: SELECT, INSERT, UPDATE; physical DELETE denied)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE DELETE ON TABLE counterparties FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE counterparties TO finsight_app;
    END IF;
END $$;
