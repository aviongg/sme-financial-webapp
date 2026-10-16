CREATE TABLE IF NOT EXISTS record_drafts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    account_id UUID REFERENCES financial_accounts(id) ON DELETE SET NULL,
    event_type VARCHAR(50),
    effective_date DATE,
    amount_input VARCHAR(100),
    description VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    recorded_transaction_id UUID REFERENCES financial_transactions(id) ON DELETE SET NULL,
    created_by_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    updated_by_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_record_draft_status CHECK (status IN ('DRAFT', 'RECORDED', 'DISCARDED')),
    CONSTRAINT chk_record_draft_event_type CHECK (event_type IS NULL OR event_type IN ('DIRECT_SALE_RECEIPT', 'OPERATING_EXPENSE_PAYMENT')),
    CONSTRAINT chk_record_draft_recorded_tx CHECK (
        (status = 'RECORDED' AND recorded_transaction_id IS NOT NULL) OR
        (status != 'RECORDED' AND recorded_transaction_id IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_record_drafts_business_status
    ON record_drafts (business_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_record_drafts_account
    ON record_drafts (account_id) WHERE account_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_record_drafts_recorded_tx
    ON record_drafts (recorded_transaction_id) WHERE recorded_transaction_id IS NOT NULL;

GRANT SELECT, INSERT, UPDATE, DELETE ON record_drafts TO finsight_app;
