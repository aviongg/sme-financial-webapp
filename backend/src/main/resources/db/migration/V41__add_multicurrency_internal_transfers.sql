-- V41: Add Multicurrency Internal Transfers and Relax Ledger Currency Constraints
-- Author: Google Antigravity (Sole Full-Stack Developer A Engineer & Financial Systems Architect)
-- Feature: F11E Cross-Currency Internal Account Transfers

-- 1. Relax financial_transactions currency constraint to support multi-currency postings
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS chk_tx_currency;
ALTER TABLE financial_transactions ADD CONSTRAINT chk_tx_currency
    CHECK (currency IN ('PKR', 'USD', 'EUR', 'GBP', 'AED', 'SAR', 'JPY', 'KWD', 'BHD', 'OMR'));

-- 2. Relax account_movements currency constraint to support multi-currency postings
ALTER TABLE account_movements DROP CONSTRAINT IF EXISTS chk_mov_currency;
ALTER TABLE account_movements ADD CONSTRAINT chk_mov_currency
    CHECK (currency IN ('PKR', 'USD', 'EUR', 'GBP', 'AED', 'SAR', 'JPY', 'KWD', 'BHD', 'OMR'));

-- 3. Create fx_internal_transfers table
CREATE TABLE IF NOT EXISTS fx_internal_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    source_account_id UUID NOT NULL REFERENCES financial_accounts(id) ON DELETE RESTRICT,
    destination_account_id UUID NOT NULL REFERENCES financial_accounts(id) ON DELETE RESTRICT,
    source_currency VARCHAR(3) NOT NULL,
    destination_currency VARCHAR(3) NOT NULL,
    source_amount NUMERIC(19, 4) NOT NULL
        CHECK (source_amount > 0),
    exchange_rate NUMERIC(19, 6) NOT NULL
        CHECK (exchange_rate > 0),
    rate_convention VARCHAR(32) NOT NULL DEFAULT 'BASE_TO_QUOTE',
    base_currency VARCHAR(3) NOT NULL,
    quote_currency VARCHAR(3) NOT NULL,
    rate_source VARCHAR(64) NOT NULL DEFAULT 'MANUAL_USER_RATE',
    rate_timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    converted_destination_principal NUMERIC(19, 4) NOT NULL
        CHECK (converted_destination_principal > 0),
    fee_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000
        CHECK (fee_amount >= 0),
    fee_currency VARCHAR(3) NOT NULL,
    fee_placement VARCHAR(32) NOT NULL DEFAULT 'DESTINATION_DEDUCTED'
        CHECK (fee_placement IN ('DESTINATION_DEDUCTED', 'SOURCE_CHARGED', 'NO_FEE')),
    net_destination_amount NUMERIC(19, 4) NOT NULL
        CHECK (net_destination_amount > 0),
    total_source_amount NUMERIC(19, 4) NOT NULL
        CHECK (total_source_amount > 0),
    financial_transaction_id UUID NOT NULL REFERENCES financial_transactions(id) ON DELETE RESTRICT,
    source_movement_id UUID NOT NULL REFERENCES account_movements(id) ON DELETE RESTRICT,
    destination_movement_id UUID NOT NULL REFERENCES account_movements(id) ON DELETE RESTRICT,
    transfer_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SETTLED'
        CHECK (status IN ('SETTLED', 'REVERSED')),
    reversal_transaction_id UUID REFERENCES financial_transactions(id) ON DELETE SET NULL,
    reversal_source_movement_id UUID REFERENCES account_movements(id) ON DELETE SET NULL,
    reversal_destination_movement_id UUID REFERENCES account_movements(id) ON DELETE SET NULL,
    reversal_reason VARCHAR(500),
    reversed_at TIMESTAMPTZ,
    notes VARCHAR(1000),
    idempotency_key VARCHAR(100),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_fx_transfer_tx UNIQUE (financial_transaction_id),
    CONSTRAINT uq_fx_transfer_biz_idempotency UNIQUE (business_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_fx_transfers_biz_status ON fx_internal_transfers(business_id, status);
CREATE INDEX IF NOT EXISTS idx_fx_transfers_biz_date ON fx_internal_transfers(business_id, transfer_date DESC);
CREATE INDEX IF NOT EXISTS idx_fx_transfers_source_acct ON fx_internal_transfers(source_account_id);
CREATE INDEX IF NOT EXISTS idx_fx_transfers_dest_acct ON fx_internal_transfers(destination_account_id);
CREATE INDEX IF NOT EXISTS idx_fx_transfers_reversal_tx ON fx_internal_transfers(reversal_transaction_id);

-- 4. Grant least-privilege runtime DML permissions to finsight_app and finsight_backup
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE DELETE ON TABLE fx_internal_transfers FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE fx_internal_transfers TO finsight_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON TABLE fx_internal_transfers TO finsight_backup;
    END IF;
END $$;
