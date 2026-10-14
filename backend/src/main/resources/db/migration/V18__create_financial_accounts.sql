-- V18: Financial Accounts Schema (Wave 1 Bounded Read / Persistence Foundation)
-- Author: Antigravity (Developer A Backend)
-- Approved as SULEMAN_AUTHORIZED pending Fatima notification. Collision check confirmed clean against remote origin/main and origin/dev/fatima (both at V17).

CREATE TABLE IF NOT EXISTS financial_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    account_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL
        CHECK (account_type IN ('CASH', 'BANK', 'WALLET')),
    currency VARCHAR(3) NOT NULL,
    currency_decimals INT NOT NULL,
    opening_balance NUMERIC(19, 4) NOT NULL,
    opening_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    institution_name VARCHAR(150),
    account_identifier VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_financial_accounts_business_name UNIQUE (business_id, account_name)
);

CREATE INDEX IF NOT EXISTS idx_financial_accounts_business_status
    ON financial_accounts (business_id, status);

CREATE INDEX IF NOT EXISTS idx_financial_accounts_business_order
    ON financial_accounts (business_id, created_at ASC, id ASC);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON financial_accounts TO finsight_app;
    END IF;
END $$;
