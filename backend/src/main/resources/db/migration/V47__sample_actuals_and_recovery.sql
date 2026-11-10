-- V47: F09 actual sample/production-activity finance (Developer B).
--
-- Actual cost of a sample = F08 SAMPLE inventory consumption (single inventory authority, inventory_movements)
--                          + direct costs attributed from posted ledger payments (sample_actual_costs).
-- Recovery = portions of RECOGNIZED customer invoices linked to the sample (sample_recovery_links); the
-- outstanding recovery is read from the invoice's single AR obligation. No separate inventory or AR exists.
-- A ledger payment can be attributed at most once in total across samples and C05 projects.

CREATE TABLE sample_actual_costs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    sample_id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    cost_category VARCHAR(12) NOT NULL CHECK (cost_category IN ('MATERIAL', 'LABOUR', 'FREIGHT', 'OTHER')),
    cost_date DATE NOT NULL,
    notes VARCHAR(500) NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'EFFECTIVE' CHECK (status IN ('EFFECTIVE', 'REVERSED')),
    idempotency_key VARCHAR(120) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reversed_by UUID NULL REFERENCES app_users(id),
    reversed_at TIMESTAMPTZ NULL,
    reversal_reason VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_sample_actual_costs_idempotency UNIQUE (business_id, idempotency_key),
    CONSTRAINT chk_sample_actual_costs_reversal CHECK (
        (status = 'EFFECTIVE' AND reversed_at IS NULL AND reversed_by IS NULL AND reversal_reason IS NULL)
        OR (status = 'REVERSED' AND reversed_at IS NOT NULL AND reversed_by IS NOT NULL
            AND reversal_reason IS NOT NULL AND btrim(reversal_reason) <> '')
    ),
    CONSTRAINT fk_sample_actual_costs_sample
        FOREIGN KEY (business_id, sample_id) REFERENCES sample_activities (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_sample_actual_costs_tx
        FOREIGN KEY (transaction_id, business_id) REFERENCES financial_transactions (id, business_id) ON DELETE RESTRICT
);

CREATE INDEX idx_sample_actual_costs_sample ON sample_actual_costs (business_id, sample_id, status);
CREATE INDEX idx_sample_actual_costs_tx ON sample_actual_costs (business_id, transaction_id, status);

CREATE TABLE sample_recovery_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    sample_id UUID NOT NULL,
    document_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'UNLINKED')),
    notes VARCHAR(500) NULL,
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    unlinked_by UUID NULL REFERENCES app_users(id),
    unlinked_at TIMESTAMPTZ NULL,
    unlink_reason VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT chk_sample_recovery_links_unlink CHECK (
        (status = 'ACTIVE' AND unlinked_at IS NULL AND unlinked_by IS NULL AND unlink_reason IS NULL)
        OR (status = 'UNLINKED' AND unlinked_at IS NOT NULL AND unlinked_by IS NOT NULL
            AND unlink_reason IS NOT NULL AND btrim(unlink_reason) <> '')
    ),
    CONSTRAINT fk_sample_recovery_links_sample
        FOREIGN KEY (business_id, sample_id) REFERENCES sample_activities (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_sample_recovery_links_document
        FOREIGN KEY (document_id, business_id) REFERENCES commercial_documents (id, business_id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_sample_recovery_links_active
    ON sample_recovery_links (business_id, sample_id, document_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_sample_recovery_links_document ON sample_recovery_links (business_id, document_id, status);

CREATE FUNCTION enforce_sample_actual_history() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'Sample actual records cannot be truncated' USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Sample actual records are permanent; reverse or unlink instead' USING ERRCODE = '55000';
    END IF;
    IF TG_TABLE_NAME = 'sample_actual_costs' THEN
        IF OLD.status <> 'EFFECTIVE' OR NEW.status <> 'REVERSED'
           OR NEW.id IS DISTINCT FROM OLD.id OR NEW.business_id IS DISTINCT FROM OLD.business_id
           OR NEW.sample_id IS DISTINCT FROM OLD.sample_id OR NEW.transaction_id IS DISTINCT FROM OLD.transaction_id
           OR NEW.amount IS DISTINCT FROM OLD.amount OR NEW.cost_category IS DISTINCT FROM OLD.cost_category
           OR NEW.cost_date IS DISTINCT FROM OLD.cost_date OR NEW.idempotency_key IS DISTINCT FROM OLD.idempotency_key
           OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash OR NEW.created_by IS DISTINCT FROM OLD.created_by
           OR NEW.created_at IS DISTINCT FROM OLD.created_at OR NEW.version <= OLD.version THEN
            RAISE EXCEPTION 'Sample actual costs are append-only; only EFFECTIVE -> REVERSED is allowed'
                USING ERRCODE = '55000';
        END IF;
    ELSE
        IF OLD.status <> 'ACTIVE' OR NEW.status <> 'UNLINKED'
           OR NEW.id IS DISTINCT FROM OLD.id OR NEW.business_id IS DISTINCT FROM OLD.business_id
           OR NEW.sample_id IS DISTINCT FROM OLD.sample_id OR NEW.document_id IS DISTINCT FROM OLD.document_id
           OR NEW.amount IS DISTINCT FROM OLD.amount OR NEW.created_by IS DISTINCT FROM OLD.created_by
           OR NEW.created_at IS DISTINCT FROM OLD.created_at OR NEW.version <= OLD.version THEN
            RAISE EXCEPTION 'Sample recovery links are append-only; only ACTIVE -> UNLINKED is allowed'
                USING ERRCODE = '55000';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sample_actual_costs_history BEFORE UPDATE OR DELETE ON sample_actual_costs
    FOR EACH ROW EXECUTE FUNCTION enforce_sample_actual_history();
CREATE TRIGGER trg_sample_actual_costs_truncate BEFORE TRUNCATE ON sample_actual_costs
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_sample_actual_history();
CREATE TRIGGER trg_sample_recovery_links_history BEFORE UPDATE OR DELETE ON sample_recovery_links
    FOR EACH ROW EXECUTE FUNCTION enforce_sample_actual_history();
CREATE TRIGGER trg_sample_recovery_links_truncate BEFORE TRUNCATE ON sample_recovery_links
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_sample_actual_history();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE sample_actual_costs, sample_recovery_links TO finsight_app;
        REVOKE DELETE, TRUNCATE ON TABLE sample_actual_costs, sample_recovery_links FROM finsight_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON TABLE sample_actual_costs, sample_recovery_links TO finsight_backup;
    END IF;
END $$;
