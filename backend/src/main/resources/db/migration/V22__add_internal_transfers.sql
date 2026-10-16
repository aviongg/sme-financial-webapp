-- V22: Add Internal Transfers and Dual Movement Constraints
-- Author: Antigravity (Developer A Backend Coordinator)
-- Scope: F01 PKR Account Transfers

-- 1. Update event_type CHECK constraint on financial_transactions
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS chk_tx_event_type;
ALTER TABLE financial_transactions ADD CONSTRAINT chk_tx_event_type
    CHECK (event_type IN ('DIRECT_SALE_RECEIPT', 'OPERATING_EXPENSE_PAYMENT', 'INTERNAL_TRANSFER'));

-- 2. Add destination_account_id to financial_transactions
ALTER TABLE financial_transactions
    ADD COLUMN IF NOT EXISTS destination_account_id UUID REFERENCES financial_accounts(id) ON DELETE RESTRICT;

-- 3. Constraint on transfer accounts:
-- For INTERNAL_TRANSFER, destination_account_id must be non-null and distinct from account_id (source).
-- For non-transfer transactions, destination_account_id must be null.
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS chk_tx_transfer_accounts;
ALTER TABLE financial_transactions ADD CONSTRAINT chk_tx_transfer_accounts CHECK (
    (event_type = 'INTERNAL_TRANSFER' AND destination_account_id IS NOT NULL AND destination_account_id <> account_id) OR
    (event_type <> 'INTERNAL_TRANSFER' AND destination_account_id IS NULL)
);

-- 4. Index on destination_account_id for fast queries when filtering by participating destination account
CREATE INDEX IF NOT EXISTS idx_fin_tx_dest_account_date
    ON financial_transactions (destination_account_id, effective_date DESC, created_at DESC, id DESC)
    WHERE destination_account_id IS NOT NULL;

-- 5. Re-affirm permissions for runtime role
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT ON TABLE financial_transactions TO finsight_app;
        GRANT SELECT, INSERT ON TABLE account_movements TO finsight_app;
    END IF;
END $$;
