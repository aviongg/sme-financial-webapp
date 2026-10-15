-- V20: Financial Transactions and Account Movements Schema (Wave 1 Bounded Posting & Event Ledger)
-- Author: Antigravity (Developer A Backend)
-- Approved as SULEMAN_AUTHORIZED pending Fatima notification. Collision check confirmed clean against remote origin/main and origin/dev/fatima (both at V17).

CREATE TABLE IF NOT EXISTS financial_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES financial_accounts(id) ON DELETE RESTRICT,
    event_type VARCHAR(64) NOT NULL,
    movement_direction VARCHAR(16) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    base_amount_pkr NUMERIC(19, 4) NOT NULL,
    effective_date DATE NOT NULL,
    description VARCHAR(255),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_tx_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_tx_base_amount_positive CHECK (base_amount_pkr > 0),
    CONSTRAINT chk_tx_movement_direction CHECK (movement_direction IN ('INCREASE', 'DECREASE')),
    CONSTRAINT chk_tx_event_type CHECK (event_type IN ('DIRECT_SALE_RECEIPT', 'OPERATING_EXPENSE_PAYMENT')),
    CONSTRAINT chk_tx_currency CHECK (currency = 'PKR')
);

CREATE INDEX IF NOT EXISTS idx_fin_tx_business_date
    ON financial_transactions (business_id, effective_date DESC, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_fin_tx_account_date
    ON financial_transactions (account_id, effective_date DESC, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_fin_tx_created_by
    ON financial_transactions (created_by_user_id);

CREATE TABLE IF NOT EXISTS account_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    transaction_id UUID NOT NULL REFERENCES financial_transactions(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES financial_accounts(id) ON DELETE RESTRICT,
    movement_direction VARCHAR(16) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    effective_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_mov_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_mov_direction CHECK (movement_direction IN ('INCREASE', 'DECREASE')),
    CONSTRAINT chk_mov_currency CHECK (currency = 'PKR')
);

CREATE INDEX IF NOT EXISTS idx_acc_mov_account_date
    ON account_movements (account_id, effective_date ASC, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_acc_mov_business_date
    ON account_movements (business_id, effective_date ASC);

CREATE INDEX IF NOT EXISTS idx_acc_mov_transaction
    ON account_movements (transaction_id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT ON TABLE financial_transactions TO finsight_app;
        GRANT SELECT, INSERT ON TABLE account_movements TO finsight_app;
    END IF;
END $$;
