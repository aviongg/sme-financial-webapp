-- V45: F04 commercial lifecycle completion (Developer B, product-completion pass).
--
-- 1. Optional inventory-item linkage on invoice/bill lines (F04 <-> F08). A link is descriptive until an
--    explicit, idempotent stock action (bill receipt / invoice fulfilment) is taken in F08.
-- 2. Credit notes (against recognized customer invoices) and debit notes (against recognized supplier
--    bills). Each adjustment is an append-only commercial fact that reduces the recognized obligation through
--    ONE non-cash allocation of the new type CREDIT_NOTE in Developer A's obligation_payment_allocations.
--    No bank movement, ledger transaction or obligation amount is ever changed; the original recognized
--    obligation stays immutable and outstanding = original - effective allocations continues to hold.
-- 3. Cancelling an adjustment is the compensating reversal: the adjustment and its allocation become
--    CANCELLED (never deleted) so recognized history remains reconstructible as of any date.

ALTER TABLE commercial_document_lines
    ADD COLUMN inventory_item_id UUID NULL;

ALTER TABLE commercial_document_lines
    ADD CONSTRAINT fk_commercial_document_lines_inventory_item
        FOREIGN KEY (business_id, inventory_item_id)
        REFERENCES inventory_items (business_id, id) ON DELETE RESTRICT;

CREATE INDEX idx_commercial_document_lines_item
    ON commercial_document_lines (business_id, inventory_item_id)
    WHERE inventory_item_id IS NOT NULL;

CREATE TABLE commercial_adjustment_sequences (
    business_id UUID NOT NULL REFERENCES businesses(id),
    kind VARCHAR(12) NOT NULL CHECK (kind IN ('CREDIT_NOTE', 'DEBIT_NOTE')),
    next_value BIGINT NOT NULL DEFAULT 1 CHECK (next_value >= 1 AND next_value <= 1000000),
    CONSTRAINT pk_commercial_adjustment_sequences PRIMARY KEY (business_id, kind)
);

CREATE TABLE commercial_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    document_id UUID NOT NULL,
    adjustment_kind VARCHAR(12) NOT NULL CHECK (adjustment_kind IN ('CREDIT_NOTE', 'DEBIT_NOTE')),
    adjustment_number VARCHAR(16) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    adjustment_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (btrim(reason) <> ''),
    obligation_id UUID NOT NULL,
    allocation_id UUID NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'EFFECTIVE' CHECK (status IN ('EFFECTIVE', 'CANCELLED')),
    idempotency_key VARCHAR(120) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_by_user_id UUID NULL REFERENCES app_users(id),
    cancelled_at TIMESTAMPTZ NULL,
    cancel_reason VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_commercial_adjustments_scoped UNIQUE (business_id, id),
    CONSTRAINT uq_commercial_adjustments_number UNIQUE (business_id, adjustment_kind, adjustment_number),
    CONSTRAINT uq_commercial_adjustments_idempotency UNIQUE (business_id, idempotency_key),
    CONSTRAINT uq_commercial_adjustments_allocation UNIQUE (allocation_id),
    CONSTRAINT chk_commercial_adjustments_number CHECK (
        (adjustment_kind = 'CREDIT_NOTE' AND adjustment_number ~ '^CN-[0-9]{6}$')
        OR (adjustment_kind = 'DEBIT_NOTE' AND adjustment_number ~ '^DN-[0-9]{6}$')
    ),
    CONSTRAINT chk_commercial_adjustments_cancel CHECK (
        (status = 'EFFECTIVE' AND cancelled_at IS NULL AND cancelled_by_user_id IS NULL AND cancel_reason IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by_user_id IS NOT NULL
            AND cancel_reason IS NOT NULL AND btrim(cancel_reason) <> '')
    ),
    CONSTRAINT fk_commercial_adjustments_document
        FOREIGN KEY (document_id, business_id) REFERENCES commercial_documents (id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_commercial_adjustments_obligation
        FOREIGN KEY (obligation_id, business_id) REFERENCES financial_obligations (id, business_id) ON DELETE RESTRICT
);

CREATE INDEX idx_commercial_adjustments_document
    ON commercial_adjustments (business_id, document_id, status, adjustment_date);
CREATE INDEX idx_commercial_adjustments_date
    ON commercial_adjustments (business_id, adjustment_date, status);

-- Developer-A obligation allocation extension (smallest additive contract, documented in
-- docs/phase2/handoffs/PRODUCT_COMPLETION_DEV_A_CONTRACTS.md): a third, non-cash allocation type.
ALTER TABLE obligation_payment_allocations
    ADD COLUMN commercial_adjustment_id UUID NULL;

ALTER TABLE obligation_payment_allocations
    DROP CONSTRAINT ck_obligation_allocations_type;
ALTER TABLE obligation_payment_allocations
    ADD CONSTRAINT ck_obligation_allocations_type
        CHECK (allocation_type IN ('CASH', 'ADVANCE', 'CREDIT_NOTE'));

ALTER TABLE obligation_payment_allocations
    DROP CONSTRAINT ck_obligation_allocations_source;
ALTER TABLE obligation_payment_allocations
    ADD CONSTRAINT ck_obligation_allocations_source
        CHECK (
            (allocation_type = 'CASH' AND financial_transaction_id IS NOT NULL AND advance_id IS NULL
                AND commercial_adjustment_id IS NULL) OR
            (allocation_type = 'ADVANCE' AND advance_id IS NOT NULL AND financial_transaction_id IS NULL
                AND commercial_adjustment_id IS NULL) OR
            (allocation_type = 'CREDIT_NOTE' AND commercial_adjustment_id IS NOT NULL
                AND financial_transaction_id IS NULL AND advance_id IS NULL)
        );

CREATE UNIQUE INDEX uq_allocations_commercial_adjustment
    ON obligation_payment_allocations (commercial_adjustment_id)
    WHERE commercial_adjustment_id IS NOT NULL;

CREATE FUNCTION enforce_commercial_adjustment_history() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'Commercial adjustments cannot be truncated' USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Commercial adjustments are permanent; cancel them instead' USING ERRCODE = '55000';
    END IF;
    IF OLD.status = 'CANCELLED' THEN
        RAISE EXCEPTION 'Cancelled commercial adjustments are immutable' USING ERRCODE = '55000';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.business_id IS DISTINCT FROM OLD.business_id
       OR NEW.document_id IS DISTINCT FROM OLD.document_id
       OR NEW.adjustment_kind IS DISTINCT FROM OLD.adjustment_kind
       OR NEW.adjustment_number IS DISTINCT FROM OLD.adjustment_number
       OR NEW.amount IS DISTINCT FROM OLD.amount
       OR NEW.adjustment_date IS DISTINCT FROM OLD.adjustment_date
       OR NEW.reason IS DISTINCT FROM OLD.reason
       OR NEW.obligation_id IS DISTINCT FROM OLD.obligation_id
       OR NEW.allocation_id IS DISTINCT FROM OLD.allocation_id
       OR NEW.idempotency_key IS DISTINCT FROM OLD.idempotency_key
       OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash
       OR NEW.created_by_user_id IS DISTINCT FROM OLD.created_by_user_id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.status <> 'CANCELLED'
       OR NEW.version <= OLD.version THEN
        RAISE EXCEPTION 'Commercial adjustments are append-only; only EFFECTIVE -> CANCELLED is allowed'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_commercial_adjustments_history
    BEFORE UPDATE OR DELETE ON commercial_adjustments
    FOR EACH ROW EXECUTE FUNCTION enforce_commercial_adjustment_history();
CREATE TRIGGER trg_commercial_adjustments_truncate
    BEFORE TRUNCATE ON commercial_adjustments
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_commercial_adjustment_history();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE commercial_adjustments TO finsight_app;
        REVOKE DELETE, TRUNCATE ON TABLE commercial_adjustments FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE commercial_adjustment_sequences TO finsight_app;
        REVOKE DELETE, TRUNCATE ON TABLE commercial_adjustment_sequences FROM finsight_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON TABLE commercial_adjustments, commercial_adjustment_sequences TO finsight_backup;
    END IF;
END $$;
