-- V39: Create Cross-Currency Settlements and Locked FX Evidence Foundation
-- Author: Google Antigravity (Sole Full-Stack Developer A Engineer & Financial Systems Architect)
-- Feature: F11B Cross-Currency Settlements, Account Transfers & Authoritative FX Posting

-- 1. Relax financial_obligations currency constraint to support multi-currency obligations
ALTER TABLE financial_obligations DROP CONSTRAINT IF EXISTS ck_financial_obligations_currency;
ALTER TABLE financial_obligations ADD CONSTRAINT ck_financial_obligations_currency
    CHECK (currency IN ('PKR', 'USD', 'EUR', 'GBP', 'AED', 'SAR', 'JPY', 'KWD', 'BHD', 'OMR'));

-- 2. Create fx_obligation_settlements table
CREATE TABLE IF NOT EXISTS fx_obligation_settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    obligation_id UUID NOT NULL REFERENCES financial_obligations(id) ON DELETE RESTRICT,
    financial_transaction_id UUID NOT NULL REFERENCES financial_transactions(id) ON DELETE RESTRICT,
    allocation_id UUID NOT NULL REFERENCES obligation_payment_allocations(id) ON DELETE RESTRICT,
    settlement_direction VARCHAR(32) NOT NULL
        CHECK (settlement_direction IN ('PAYABLE_SETTLEMENT', 'RECEIVABLE_COLLECTION')),
    obligation_currency VARCHAR(3) NOT NULL,
    obligation_amount NUMERIC(19, 4) NOT NULL
        CHECK (obligation_amount > 0),
    account_id UUID NOT NULL REFERENCES financial_accounts(id) ON DELETE RESTRICT,
    account_currency VARCHAR(3) NOT NULL,
    converted_principal NUMERIC(19, 4) NOT NULL
        CHECK (converted_principal > 0),
    fee_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000
        CHECK (fee_amount >= 0),
    fee_currency VARCHAR(3) NOT NULL,
    total_account_movement NUMERIC(19, 4) NOT NULL
        CHECK (total_account_movement > 0),
    exchange_rate NUMERIC(19, 6) NOT NULL
        CHECK (exchange_rate > 0),
    rate_convention VARCHAR(32) NOT NULL DEFAULT 'BASE_TO_QUOTE',
    base_currency VARCHAR(3) NOT NULL,
    quote_currency VARCHAR(3) NOT NULL,
    rate_source VARCHAR(64) NOT NULL DEFAULT 'MANUAL_USER_RATE',
    rate_timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    settlement_date DATE NOT NULL,
    effective_rate NUMERIC(19, 6) NOT NULL
        CHECK (effective_rate > 0),
    status VARCHAR(32) NOT NULL DEFAULT 'SETTLED'
        CHECK (status IN ('SETTLED', 'REVERSED')),
    reversal_transaction_id UUID REFERENCES financial_transactions(id) ON DELETE SET NULL,
    reversal_reason VARCHAR(500),
    reversed_at TIMESTAMPTZ,
    notes VARCHAR(1000),
    idempotency_key VARCHAR(100),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_fx_settlement_allocation UNIQUE (allocation_id),
    CONSTRAINT uq_fx_settlement_tx UNIQUE (financial_transaction_id),
    CONSTRAINT uq_fx_settlement_biz_idempotency UNIQUE (business_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_fx_settlements_biz_ob ON fx_obligation_settlements(business_id, obligation_id, status);
CREATE INDEX IF NOT EXISTS idx_fx_settlements_biz_tx ON fx_obligation_settlements(business_id, financial_transaction_id);
CREATE INDEX IF NOT EXISTS idx_fx_settlements_biz_date ON fx_obligation_settlements(business_id, settlement_date);
CREATE INDEX IF NOT EXISTS idx_fx_settlements_reversal_tx ON fx_obligation_settlements(reversal_transaction_id);

-- 2. Grant least-privilege runtime DML permissions to finsight_app and finsight_backup
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE DELETE ON TABLE fx_obligation_settlements FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE fx_obligation_settlements TO finsight_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON TABLE fx_obligation_settlements TO finsight_backup;
    END IF;
END $$;
