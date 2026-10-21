-- V29: Create Recurring Transactions Schema (A-F12A-RECURRING-OPERATIONS-V1)
-- Author: Antigravity (Developer A Full Stack Coordinator)

-- 1. Create recurring_rules table
CREATE TABLE IF NOT EXISTS recurring_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    event_type VARCHAR(50) NOT NULL,
    account_id UUID NOT NULL,
    counterparty_id UUID,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    frequency VARCHAR(20) NOT NULL,
    anchor_day_of_month INTEGER NOT NULL,
    anchor_month INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    next_due_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_by_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    updated_by_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_recurring_rules_id_business UNIQUE (id, business_id),
    CONSTRAINT fk_recurring_rules_account FOREIGN KEY (account_id, business_id)
        REFERENCES financial_accounts (id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_recurring_rules_counterparty FOREIGN KEY (counterparty_id, business_id)
        REFERENCES counterparties (id, business_id) ON DELETE RESTRICT,
    CONSTRAINT ck_recurring_rules_event_type CHECK (event_type IN ('DIRECT_SALE_RECEIPT', 'OPERATING_EXPENSE_PAYMENT')),
    CONSTRAINT ck_recurring_rules_currency CHECK (currency = 'PKR'),
    CONSTRAINT ck_recurring_rules_amount CHECK (amount > 0 AND amount <= 999999999999.99),
    CONSTRAINT ck_recurring_rules_frequency CHECK (frequency IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'ANNUALLY')),
    CONSTRAINT ck_recurring_rules_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT ck_recurring_rules_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_recurring_rules_anchor_day CHECK (anchor_day_of_month >= 1 AND anchor_day_of_month <= 31),
    CONSTRAINT ck_recurring_rules_anchor_month CHECK (anchor_month >= 1 AND anchor_month <= 12)
);

CREATE INDEX IF NOT EXISTS idx_recurring_rules_business_status_due
    ON recurring_rules (business_id, status, next_due_date);

CREATE INDEX IF NOT EXISTS idx_recurring_rules_account
    ON recurring_rules (business_id, account_id);

CREATE INDEX IF NOT EXISTS idx_recurring_rules_counterparty
    ON recurring_rules (business_id, counterparty_id)
    WHERE counterparty_id IS NOT NULL;

-- 2. Create recurring_rule_occurrences table
CREATE TABLE IF NOT EXISTS recurring_rule_occurrences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    recurring_rule_id UUID NOT NULL,
    rule_version BIGINT NOT NULL,
    scheduled_date DATE NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    account_id UUID NOT NULL,
    counterparty_id UUID,
    description VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    draft_id UUID,
    posted_transaction_id UUID,
    generated_at TIMESTAMP WITH TIME ZONE,
    posted_at TIMESTAMP WITH TIME ZONE,
    skipped_at TIMESTAMP WITH TIME ZONE,
    skipped_reason VARCHAR(255),
    reversed_at TIMESTAMP WITH TIME ZONE,
    created_by_user_id UUID REFERENCES app_users(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_recurring_rule_occurrences_id_business UNIQUE (id, business_id),
    CONSTRAINT uq_recurring_rule_occurrence_schedule UNIQUE (business_id, recurring_rule_id, scheduled_date),
    CONSTRAINT fk_recurring_occurrences_rule FOREIGN KEY (recurring_rule_id, business_id)
        REFERENCES recurring_rules (id, business_id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_occurrences_account FOREIGN KEY (account_id, business_id)
        REFERENCES financial_accounts (id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_recurring_occurrences_draft FOREIGN KEY (draft_id, business_id)
        REFERENCES record_drafts (id, business_id) ON DELETE SET NULL,
    CONSTRAINT fk_recurring_occurrences_tx FOREIGN KEY (posted_transaction_id, business_id)
        REFERENCES financial_transactions (id, business_id) ON DELETE RESTRICT,
    CONSTRAINT ck_recurring_occurrences_status CHECK (status IN ('SCHEDULED', 'DRAFT_READY', 'POSTED', 'SKIPPED', 'REVERSED')),
    CONSTRAINT ck_recurring_occurrences_event_type CHECK (event_type IN ('DIRECT_SALE_RECEIPT', 'OPERATING_EXPENSE_PAYMENT')),
    CONSTRAINT ck_recurring_occurrences_amount CHECK (amount > 0 AND amount <= 999999999999.99)
);

CREATE INDEX IF NOT EXISTS idx_recurring_occurrences_rule_date
    ON recurring_rule_occurrences (business_id, recurring_rule_id, scheduled_date DESC);

CREATE INDEX IF NOT EXISTS idx_recurring_occurrences_status
    ON recurring_rule_occurrences (business_id, status);

CREATE INDEX IF NOT EXISTS idx_recurring_occurrences_draft
    ON recurring_rule_occurrences (business_id, draft_id)
    WHERE draft_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_recurring_occurrences_tx
    ON recurring_rule_occurrences (business_id, posted_transaction_id)
    WHERE posted_transaction_id IS NOT NULL;

-- 3. Grant table permissions to runtime application role
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE recurring_rules TO finsight_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE recurring_rule_occurrences TO finsight_app;
    END IF;
END $$;
