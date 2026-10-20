-- V27: Create Payment Cycles, Multi-Obligation Allocations, Advances, and Payment Schedules (A-F03-PAYMENT-CYCLES-V2)
-- Author: Antigravity (Developer A Backend Coordinator)

-- 1. Create counterparty_advances table (for unapplied customer/supplier advances)
CREATE TABLE IF NOT EXISTS counterparty_advances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    counterparty_id UUID NOT NULL,
    direction VARCHAR(32) NOT NULL,
    financial_transaction_id UUID NOT NULL,
    original_amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    effective_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    reference_number VARCHAR(100),
    description VARCHAR(1000),
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    reversal_transaction_id UUID,
    created_by_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_counterparty_advances_id_business UNIQUE (id, business_id),
    CONSTRAINT fk_counterparty_advances_counterparty
        FOREIGN KEY (counterparty_id, business_id)
        REFERENCES counterparties (id, business_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_counterparty_advances_tx
        FOREIGN KEY (financial_transaction_id, business_id)
        REFERENCES financial_transactions (id, business_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_counterparty_advances_reversal_tx
        FOREIGN KEY (reversal_transaction_id, business_id)
        REFERENCES financial_transactions (id, business_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_advances_direction CHECK (direction IN ('RECEIVABLE', 'PAYABLE')),
    CONSTRAINT ck_advances_status CHECK (status IN ('ACTIVE', 'EXHAUSTED', 'CANCELLED')),
    CONSTRAINT ck_advances_amount CHECK (original_amount > 0),
    CONSTRAINT ck_advances_currency CHECK (currency = 'PKR'),
    CONSTRAINT ck_advances_cancel CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL) OR
        (status IN ('ACTIVE', 'EXHAUSTED') AND cancelled_at IS NULL AND reversal_transaction_id IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_advances_business_cp_status
    ON counterparty_advances (business_id, counterparty_id, status);

CREATE INDEX IF NOT EXISTS idx_advances_business_tx
    ON counterparty_advances (business_id, financial_transaction_id);

CREATE INDEX IF NOT EXISTS idx_advances_reversal_tx
    ON counterparty_advances (reversal_transaction_id)
    WHERE reversal_transaction_id IS NOT NULL;

-- 2. Upgrade obligation_payment_allocations to support both cash settlements and advance applications
ALTER TABLE obligation_payment_allocations
    ADD COLUMN IF NOT EXISTS allocation_type VARCHAR(32) NOT NULL DEFAULT 'CASH';

ALTER TABLE obligation_payment_allocations
    ADD COLUMN IF NOT EXISTS advance_id UUID;

ALTER TABLE obligation_payment_allocations
    ALTER COLUMN financial_transaction_id DROP NOT NULL;

ALTER TABLE obligation_payment_allocations
    ADD CONSTRAINT fk_obligation_allocations_advance
        FOREIGN KEY (advance_id, business_id)
        REFERENCES counterparty_advances (id, business_id)
        ON DELETE RESTRICT;

ALTER TABLE obligation_payment_allocations
    ADD CONSTRAINT ck_obligation_allocations_type
        CHECK (allocation_type IN ('CASH', 'ADVANCE'));

ALTER TABLE obligation_payment_allocations
    ADD CONSTRAINT ck_obligation_allocations_source
        CHECK (
            (allocation_type = 'CASH' AND financial_transaction_id IS NOT NULL AND advance_id IS NULL) OR
            (allocation_type = 'ADVANCE' AND advance_id IS NOT NULL AND financial_transaction_id IS NULL)
        );

CREATE INDEX IF NOT EXISTS idx_allocations_business_advance
    ON obligation_payment_allocations (business_id, advance_id)
    WHERE advance_id IS NOT NULL;

-- 3. Create obligation_payment_schedules table (non-posting payment plans)
CREATE TABLE IF NOT EXISTS obligation_payment_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    obligation_id UUID NOT NULL,
    installment_number INT NOT NULL,
    expected_date DATE NOT NULL,
    expected_amount NUMERIC(19, 4) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
    notes VARCHAR(500),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_obligation_schedules_id_business UNIQUE (id, business_id),
    CONSTRAINT fk_obligation_schedules_obligation
        FOREIGN KEY (obligation_id, business_id)
        REFERENCES financial_obligations (id, business_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_schedules_status CHECK (status IN ('PLANNED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_schedules_amount CHECK (expected_amount > 0),
    CONSTRAINT uq_schedules_obligation_installment UNIQUE (obligation_id, installment_number)
);

CREATE INDEX IF NOT EXISTS idx_schedules_business_date
    ON obligation_payment_schedules (business_id, expected_date);

CREATE INDEX IF NOT EXISTS idx_schedules_obligation
    ON obligation_payment_schedules (obligation_id, installment_number);

-- 4. Runtime role privileges for finsight_app (Least privilege: DML only; physical DELETE denied on advances)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE DELETE ON TABLE counterparty_advances FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE counterparty_advances TO finsight_app;

        REVOKE DELETE ON TABLE obligation_payment_allocations FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE obligation_payment_allocations TO finsight_app;

        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE obligation_payment_schedules TO finsight_app;
    END IF;
END $$;
