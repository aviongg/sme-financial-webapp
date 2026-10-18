-- V23: Add Transaction Reversals and Immutability Constraints
-- Author: Antigravity (Developer A Backend Coordinator)
-- Scope: A-F01-REVERSALS Safe Correction of Recorded Transactions

-- 1. Add reversed_transaction_id and reversal_reason columns
ALTER TABLE financial_transactions
    ADD COLUMN IF NOT EXISTS reversed_transaction_id UUID REFERENCES financial_transactions(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS reversal_reason VARCHAR(255);

-- 2. Prevent self-reference
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS chk_tx_reversal_not_self;
ALTER TABLE financial_transactions ADD CONSTRAINT chk_tx_reversal_not_self
    CHECK (reversed_transaction_id IS NULL OR reversed_transaction_id <> id);

-- 3. Enforce reason presence when reversal reference is present
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS chk_tx_reversal_reason;
ALTER TABLE financial_transactions ADD CONSTRAINT chk_tx_reversal_reason
    CHECK ((reversed_transaction_id IS NOT NULL AND reversal_reason IS NOT NULL) OR (reversed_transaction_id IS NULL));

-- 4. Enforce at most one reversal per original transaction
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS uq_fin_tx_reversed_transaction_id;
ALTER TABLE financial_transactions ADD CONSTRAINT uq_fin_tx_reversed_transaction_id
    UNIQUE (reversed_transaction_id);

-- 5. Prevent cross-business reversals: ensure (reversed_transaction_id, business_id) belongs to same business
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS uq_fin_tx_id_business CASCADE;
ALTER TABLE financial_transactions ADD CONSTRAINT uq_fin_tx_id_business
    UNIQUE (id, business_id);

ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS fk_fin_tx_reversed_tx_business;
ALTER TABLE financial_transactions ADD CONSTRAINT fk_fin_tx_reversed_tx_business
    FOREIGN KEY (reversed_transaction_id, business_id) REFERENCES financial_transactions (id, business_id) ON DELETE RESTRICT;

-- 6. Index on reversed_transaction_id for lookup efficiency
CREATE INDEX IF NOT EXISTS idx_fin_tx_reversed_tx
    ON financial_transactions (reversed_transaction_id)
    WHERE reversed_transaction_id IS NOT NULL;

-- 7. Runtime privileges
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT ON TABLE financial_transactions TO finsight_app;
        GRANT SELECT, INSERT ON TABLE account_movements TO finsight_app;
    END IF;
END $$;
