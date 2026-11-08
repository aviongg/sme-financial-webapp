-- V38: Create Salesperson and Commission Foundation (F10 Salesperson & Commission Management)
-- Author: Google Antigravity (Sole Developer A Full-Stack Lead)

-- 1. Extend business_memberships role check constraint to include SALESPERSON
ALTER TABLE business_memberships DROP CONSTRAINT IF EXISTS business_memberships_role_check;
ALTER TABLE business_memberships ADD CONSTRAINT business_memberships_role_check
    CHECK (role IN ('OWNER', 'ACCOUNTANT', 'MANAGER', 'VIEWER', 'SALESPERSON'));

-- 2. Salesperson Profiles
CREATE TABLE IF NOT EXISTS salesperson_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    notes VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_salesperson_business_email UNIQUE (business_id, email),
    CONSTRAINT uq_salesperson_business_user UNIQUE (business_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_salesperson_business_status ON salesperson_profiles(business_id, status);
CREATE INDEX IF NOT EXISTS idx_salesperson_business_user ON salesperson_profiles(business_id, user_id);

-- 3. Customer Sales Assignments
CREATE TABLE IF NOT EXISTS sales_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    salesperson_id UUID NOT NULL REFERENCES salesperson_profiles(id) ON DELETE CASCADE,
    counterparty_id UUID NOT NULL REFERENCES counterparties(id) ON DELETE RESTRICT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    effective_start_date DATE NOT NULL,
    effective_end_date DATE,
    assigned_by_user_id UUID NOT NULL REFERENCES app_users(id),
    notes VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sales_assignments_sp ON sales_assignments(business_id, salesperson_id, status);
CREATE INDEX IF NOT EXISTS idx_sales_assignments_cp ON sales_assignments(business_id, counterparty_id, status);

-- 4. Commission Rules (Foundation for F10B)
CREATE TABLE IF NOT EXISTS commission_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    salesperson_id UUID REFERENCES salesperson_profiles(id) ON DELETE CASCADE,
    rule_name VARCHAR(255) NOT NULL,
    calculation_type VARCHAR(32) NOT NULL
        CHECK (calculation_type IN ('PERCENTAGE', 'FIXED')),
    earning_basis VARCHAR(32) NOT NULL DEFAULT 'COLLECTION_BASED'
        CHECK (earning_basis IN ('COLLECTION_BASED', 'SALES_BASED')),
    rate_percentage NUMERIC(8, 4),
    fixed_amount NUMERIC(19, 4),
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    effective_start_date DATE NOT NULL,
    effective_end_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_commission_rules_lookup ON commission_rules(business_id, salesperson_id, status);

-- 5. Commission Earnings (Foundation for F10B & F10C)
CREATE TABLE IF NOT EXISTS commission_earnings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    salesperson_id UUID NOT NULL REFERENCES salesperson_profiles(id) ON DELETE CASCADE,
    rule_id UUID NOT NULL REFERENCES commission_rules(id) ON DELETE RESTRICT,
    counterparty_id UUID NOT NULL REFERENCES counterparties(id) ON DELETE RESTRICT,
    source_type VARCHAR(32) NOT NULL
        CHECK (source_type IN ('OBLIGATION_ALLOCATION', 'MANUAL_SALE_DECLARATION', 'COMMERCIAL_ORDER')),
    source_id VARCHAR(255) NOT NULL,
    source_fingerprint VARCHAR(255) NOT NULL,
    sale_amount NUMERIC(19, 4) NOT NULL,
    commission_rate_snapshot NUMERIC(8, 4),
    commission_amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    status VARCHAR(32) NOT NULL DEFAULT 'EARNED'
        CHECK (status IN ('CALCULATED', 'EARNED', 'APPROVED', 'PARTIALLY_SETTLED', 'SETTLED', 'PAID', 'VOID', 'REVERSED')),
    approved_amount NUMERIC(19, 4),
    approved_at TIMESTAMPTZ,
    approved_by_user_id UUID REFERENCES app_users(id),
    paid_at TIMESTAMPTZ,
    notes VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_commission_earning_source UNIQUE (business_id, source_fingerprint)
);
CREATE INDEX IF NOT EXISTS idx_commission_earnings_sp_status ON commission_earnings(business_id, salesperson_id, status);

-- 6. Commission Advances (Foundation for F10C)
CREATE TABLE IF NOT EXISTS commission_advances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    salesperson_id UUID NOT NULL REFERENCES salesperson_profiles(id) ON DELETE CASCADE,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    applied_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    payment_date DATE NOT NULL,
    financial_account_id UUID REFERENCES financial_accounts(id),
    financial_transaction_id UUID REFERENCES financial_transactions(id),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'FULLY_APPLIED', 'REFUNDED', 'VOID')),
    notes VARCHAR(1000),
    idempotency_key VARCHAR(100),
    created_by_user_id UUID REFERENCES app_users(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_advance_amounts CHECK (applied_amount >= 0 AND applied_amount <= amount),
    CONSTRAINT uq_commission_advance_idempotency UNIQUE (business_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_commission_advances_sp ON commission_advances(business_id, salesperson_id, status);
CREATE INDEX IF NOT EXISTS idx_commission_advances_tx ON commission_advances(business_id, financial_transaction_id);

-- 7. Commission Payouts and Advance Applications (F10C Financial Settlement)
CREATE TABLE IF NOT EXISTS commission_payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    salesperson_id UUID NOT NULL REFERENCES salesperson_profiles(id) ON DELETE CASCADE,
    earning_id UUID NOT NULL REFERENCES commission_earnings(id) ON DELETE CASCADE,
    payout_type VARCHAR(32) NOT NULL
        CHECK (payout_type IN ('PAYOUT', 'ADVANCE_APPLICATION')),
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'PKR',
    payment_date DATE NOT NULL,
    financial_account_id UUID REFERENCES financial_accounts(id),
    financial_transaction_id UUID REFERENCES financial_transactions(id),
    commission_advance_id UUID REFERENCES commission_advances(id),
    status VARCHAR(32) NOT NULL DEFAULT 'SETTLED'
        CHECK (status IN ('SETTLED', 'REVERSED')),
    reversal_transaction_id UUID REFERENCES financial_transactions(id),
    reversal_reason VARCHAR(255),
    notes VARCHAR(1000),
    idempotency_key VARCHAR(100),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_payout_type_links CHECK (
        (payout_type = 'PAYOUT' AND financial_transaction_id IS NOT NULL AND financial_account_id IS NOT NULL AND commission_advance_id IS NULL) OR
        (payout_type = 'ADVANCE_APPLICATION' AND commission_advance_id IS NOT NULL AND financial_transaction_id IS NULL AND financial_account_id IS NULL)
    ),
    CONSTRAINT uq_commission_payout_idempotency UNIQUE (business_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_commission_payouts_earning ON commission_payouts(business_id, earning_id, status);
CREATE INDEX IF NOT EXISTS idx_commission_payouts_salesperson ON commission_payouts(business_id, salesperson_id, status);
CREATE INDEX IF NOT EXISTS idx_commission_payouts_advance ON commission_payouts(business_id, commission_advance_id);
CREATE INDEX IF NOT EXISTS idx_commission_payouts_tx ON commission_payouts(business_id, financial_transaction_id);

-- 8. Runtime role privileges for finsight_app (Least privilege: SELECT, INSERT, UPDATE; physical DELETE revoked)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE DELETE ON TABLE salesperson_profiles FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE salesperson_profiles TO finsight_app;

        REVOKE DELETE ON TABLE sales_assignments FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE sales_assignments TO finsight_app;

        REVOKE DELETE ON TABLE commission_rules FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE commission_rules TO finsight_app;

        REVOKE DELETE ON TABLE commission_earnings FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE commission_earnings TO finsight_app;

        REVOKE DELETE ON TABLE commission_advances FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE commission_advances TO finsight_app;

        REVOKE DELETE ON TABLE commission_payouts FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE commission_payouts TO finsight_app;
    END IF;
END $$;
