-- V26: Create Financial Obligations and Payment Allocations Schema (A-F03-OBLIGATIONS-V1)
-- Author: Antigravity (Developer A Backend Coordinator)

-- 1. Extend event_type CHECK constraint on financial_transactions to include settlement classifications
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS chk_tx_event_type;
ALTER TABLE financial_transactions ADD CONSTRAINT chk_tx_event_type
    CHECK (event_type IN (
        'DIRECT_SALE_RECEIPT',
        'OPERATING_EXPENSE_PAYMENT',
        'INTERNAL_TRANSFER',
        'RECEIVABLE_COLLECTION',
        'PAYABLE_SETTLEMENT'
    ));

-- 2. Create financial_obligations table
CREATE TABLE IF NOT EXISTS financial_obligations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    counterparty_id UUID NOT NULL,
    direction VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL_DECLARATION',
    source_id VARCHAR(255),
    reference_number VARCHAR(100),
    issue_date DATE NOT NULL,
    due_date DATE,
    original_amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    void_date DATE,
    void_reason VARCHAR(500),
    description VARCHAR(1000),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_financial_obligations_id_business UNIQUE (id, business_id),
    CONSTRAINT fk_financial_obligations_counterparty
        FOREIGN KEY (counterparty_id, business_id)
        REFERENCES counterparties (id, business_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_financial_obligations_direction CHECK (direction IN ('RECEIVABLE', 'PAYABLE')),
    CONSTRAINT ck_financial_obligations_source_type CHECK (source_type IN (
        'MANUAL_DECLARATION',
        'CUSTOMER_INVOICE',
        'SUPPLIER_BILL',
        'OPENING_BALANCE',
        'APPROVED_ADJUSTMENT'
    )),
    CONSTRAINT ck_financial_obligations_status CHECK (status IN ('OPEN', 'VOIDED')),
    CONSTRAINT ck_financial_obligations_void CHECK (
        (status = 'VOIDED' AND void_date IS NOT NULL AND void_reason IS NOT NULL) OR
        (status = 'OPEN' AND void_date IS NULL AND void_reason IS NULL)
    ),
    CONSTRAINT ck_financial_obligations_currency CHECK (currency = 'PKR'),
    CONSTRAINT ck_financial_obligations_amount CHECK (original_amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_obligations_business_dir_status
    ON financial_obligations (business_id, direction, status, issue_date DESC);

CREATE INDEX IF NOT EXISTS idx_obligations_business_cp
    ON financial_obligations (business_id, counterparty_id);

CREATE INDEX IF NOT EXISTS idx_obligations_business_due_date
    ON financial_obligations (business_id, due_date)
    WHERE due_date IS NOT NULL;

-- Business-scoped unique source identity to prevent duplicate invoices/bills in future
CREATE UNIQUE INDEX IF NOT EXISTS uq_obligations_business_source
    ON financial_obligations (business_id, source_type, source_id)
    WHERE source_id IS NOT NULL;

-- 3. Create obligation_payment_allocations table
CREATE TABLE IF NOT EXISTS obligation_payment_allocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    obligation_id UUID NOT NULL,
    financial_transaction_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    effective_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'EFFECTIVE',
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    reversal_transaction_id UUID,
    created_by_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_obligation_allocations_id_business UNIQUE (id, business_id),
    CONSTRAINT fk_obligation_allocations_obligation
        FOREIGN KEY (obligation_id, business_id)
        REFERENCES financial_obligations (id, business_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_obligation_allocations_tx
        FOREIGN KEY (financial_transaction_id, business_id)
        REFERENCES financial_transactions (id, business_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_obligation_allocations_reversal_tx
        FOREIGN KEY (reversal_transaction_id, business_id)
        REFERENCES financial_transactions (id, business_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_obligation_allocations_status CHECK (status IN ('EFFECTIVE', 'CANCELLED')),
    CONSTRAINT ck_obligation_allocations_cancel CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL) OR
        (status = 'EFFECTIVE' AND cancelled_at IS NULL AND reversal_transaction_id IS NULL)
    ),
    CONSTRAINT ck_obligation_allocations_amount CHECK (amount > 0),
    CONSTRAINT uq_obligation_allocations_tx_obligation UNIQUE (financial_transaction_id, obligation_id)
);

CREATE INDEX IF NOT EXISTS idx_allocations_obligation
    ON obligation_payment_allocations (obligation_id, status, effective_date ASC);

CREATE INDEX IF NOT EXISTS idx_allocations_business_tx
    ON obligation_payment_allocations (business_id, financial_transaction_id);

CREATE INDEX IF NOT EXISTS idx_allocations_reversal_tx
    ON obligation_payment_allocations (reversal_transaction_id)
    WHERE reversal_transaction_id IS NOT NULL;

-- 4. Runtime role privileges for finsight_app (Least privilege: SELECT, INSERT, UPDATE; physical DELETE denied)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE DELETE ON TABLE financial_obligations FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE financial_obligations TO finsight_app;

        REVOKE DELETE ON TABLE obligation_payment_allocations FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE obligation_payment_allocations TO finsight_app;

        GRANT SELECT, INSERT ON TABLE financial_transactions TO finsight_app;
        GRANT SELECT, INSERT ON TABLE account_movements TO finsight_app;
    END IF;
END $$;
