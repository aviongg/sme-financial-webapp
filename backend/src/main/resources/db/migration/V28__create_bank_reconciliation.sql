-- V28: Create Bank Statement Import, Provenance and Reconciliation Schema (A-F12A-BANK-RECONCILIATION-V1)
-- Author: Antigravity (Developer A Backend Coordinator)

-- 1. Ensure composite tenant unique constraints on referenced tables
ALTER TABLE financial_accounts DROP CONSTRAINT IF EXISTS uq_financial_accounts_id_business CASCADE;
ALTER TABLE financial_accounts ADD CONSTRAINT uq_financial_accounts_id_business UNIQUE (id, business_id);

ALTER TABLE account_movements DROP CONSTRAINT IF EXISTS uq_account_movements_id_business CASCADE;
ALTER TABLE account_movements ADD CONSTRAINT uq_account_movements_id_business UNIQUE (id, business_id);

-- 2. Table: bank_statement_imports
CREATE TABLE IF NOT EXISTS bank_statement_imports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    account_id UUID NOT NULL,
    source_filename VARCHAR(255),
    file_fingerprint VARCHAR(64) NOT NULL,
    mapping_config TEXT,
    total_entries INT NOT NULL DEFAULT 0,
    opening_balance NUMERIC(19, 4),
    closing_balance NUMERIC(19, 4),
    from_date DATE,
    to_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    imported_by_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_bank_statement_imports_id_business UNIQUE (id, business_id),
    CONSTRAINT fk_bank_statement_imports_account FOREIGN KEY (account_id, business_id)
        REFERENCES financial_accounts (id, business_id) ON DELETE RESTRICT,
    CONSTRAINT ck_bank_statement_imports_status CHECK (status IN ('ACTIVE', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_statement_imports_business_account
    ON bank_statement_imports (business_id, account_id, created_at DESC);

-- 3. Table: bank_statement_entries
CREATE TABLE IF NOT EXISTS bank_statement_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    import_id UUID NOT NULL,
    account_id UUID NOT NULL,
    source_row_number INT NOT NULL,
    posted_date DATE NOT NULL,
    value_date DATE,
    direction VARCHAR(16) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    description VARCHAR(500),
    bank_reference VARCHAR(255),
    external_transaction_id VARCHAR(255),
    running_balance NUMERIC(19, 4),
    source_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'UNMATCHED',
    exclusion_reason VARCHAR(255),
    linked_draft_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_bank_statement_entries_id_business UNIQUE (id, business_id),
    CONSTRAINT uq_bank_statement_entries_row UNIQUE (business_id, import_id, source_row_number),
    CONSTRAINT fk_bank_statement_entries_import FOREIGN KEY (import_id, business_id)
        REFERENCES bank_statement_imports (id, business_id) ON DELETE CASCADE,
    CONSTRAINT fk_bank_statement_entries_account FOREIGN KEY (account_id, business_id)
        REFERENCES financial_accounts (id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_bank_statement_entries_draft FOREIGN KEY (linked_draft_id, business_id)
        REFERENCES record_drafts (id, business_id) ON DELETE SET NULL,
    CONSTRAINT ck_statement_entries_direction CHECK (direction IN ('INCREASE', 'DECREASE')),
    CONSTRAINT ck_statement_entries_amount CHECK (amount > 0),
    CONSTRAINT ck_statement_entries_currency CHECK (currency = 'PKR'),
    CONSTRAINT ck_statement_entries_status CHECK (status IN ('UNMATCHED', 'MATCHED', 'EXCLUDED'))
);

CREATE INDEX IF NOT EXISTS idx_statement_entries_account_date
    ON bank_statement_entries (business_id, account_id, posted_date);

CREATE INDEX IF NOT EXISTS idx_statement_entries_import
    ON bank_statement_entries (business_id, import_id);

CREATE INDEX IF NOT EXISTS idx_statement_entries_fingerprint
    ON bank_statement_entries (business_id, account_id, source_fingerprint);

CREATE INDEX IF NOT EXISTS idx_statement_entries_draft
    ON bank_statement_entries (business_id, linked_draft_id)
    WHERE linked_draft_id IS NOT NULL;

-- 4. Table: bank_reconciliation_matches
CREATE TABLE IF NOT EXISTS bank_reconciliation_matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    statement_entry_id UUID NOT NULL,
    account_movement_id UUID NOT NULL,
    account_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    match_method VARCHAR(32) NOT NULL DEFAULT 'EXACT',
    confidence_score INT NOT NULL DEFAULT 100,
    matched_by_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    matched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    unmatched_by_user_id UUID REFERENCES app_users(id) ON DELETE RESTRICT,
    unmatched_at TIMESTAMPTZ,
    unmatch_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_reconciliation_matches_id_business UNIQUE (id, business_id),
    CONSTRAINT fk_reconciliation_statement_entry FOREIGN KEY (statement_entry_id, business_id)
        REFERENCES bank_statement_entries (id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_reconciliation_account_movement FOREIGN KEY (account_movement_id, business_id)
        REFERENCES account_movements (id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_reconciliation_account FOREIGN KEY (account_id, business_id)
        REFERENCES financial_accounts (id, business_id) ON DELETE RESTRICT,
    CONSTRAINT ck_reconciliation_matches_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_reconciliation_matches_method CHECK (match_method IN ('EXACT', 'SUGGESTED', 'MANUAL'))
);

-- Partial unique constraints to strictly enforce 1-to-1 active matching
CREATE UNIQUE INDEX IF NOT EXISTS uq_reconciliation_active_statement_entry
    ON bank_reconciliation_matches (business_id, statement_entry_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_reconciliation_active_account_movement
    ON bank_reconciliation_matches (business_id, account_movement_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_reconciliation_account_status
    ON bank_reconciliation_matches (business_id, account_id, status);

-- 5. Least-privilege runtime DML grants for finsight_app (DELETE revoked)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE bank_statement_imports TO finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE bank_statement_entries TO finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE bank_reconciliation_matches TO finsight_app;
    END IF;
END $$;
