-- V46: F08 authoritative perpetual inventory, weighted-average cost and COGS (Developer B).
--
-- Policy f08-wac-v1 (frozen, docs/phase2/FINANCIAL_POLICIES_PRODUCT_COMPLETION.md):
--   * state per item is (Q, V); V is stored at scale 4 (the ledger's NUMERIC(19,4) scale);
--   * partial outbound value = round(V * q / Q, 4, HALF_UP) (unified application convention);
--   * an outbound movement that empties the item takes exactly V, so no residual ever survives depletion;
--   * movements apply in recording order and may not be back-dated before the item's last movement date;
--   * stock never goes negative (no negative-stock policy exists).
-- Every movement stores exact signed deltas and the resulting (Q, V), so
-- opening + sum(deltas) = position holds exactly and is verified by tests.

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    item_id UUID NOT NULL,
    sequence_no BIGINT NOT NULL CHECK (sequence_no >= 1),
    movement_type VARCHAR(20) NOT NULL CHECK (movement_type IN (
        'OPENING', 'RECEIPT', 'ISSUE', 'SUPPLIER_RETURN', 'CUSTOMER_RETURN',
        'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'REVERSAL')),
    purpose VARCHAR(12) NULL CHECK (purpose IS NULL OR purpose IN ('SALE', 'PROJECT', 'SAMPLE', 'INTERNAL', 'WASTAGE')),
    quantity_delta NUMERIC(15, 6) NOT NULL CHECK (quantity_delta <> 0),
    value_delta NUMERIC(19, 4) NOT NULL,
    quantity_after NUMERIC(15, 6) NOT NULL CHECK (quantity_after >= 0),
    value_after NUMERIC(19, 4) NOT NULL CHECK (value_after >= 0),
    movement_date DATE NOT NULL,
    source_type VARCHAR(20) NOT NULL CHECK (source_type IN (
        'MANUAL', 'SUPPLIER_BILL', 'CUSTOMER_INVOICE', 'TRANSACTION', 'PROJECT', 'SAMPLE', 'MOVEMENT')),
    source_id UUID NULL,
    source_line_id UUID NULL,
    project_id UUID NULL,
    sample_id UUID NULL,
    related_movement_id UUID NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    policy_version VARCHAR(16) NOT NULL CHECK (policy_version = 'f08-wac-v1'),
    notes VARCHAR(500) NULL,
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_inventory_movements_scoped UNIQUE (business_id, id),
    CONSTRAINT uq_inventory_movements_sequence UNIQUE (business_id, item_id, sequence_no),
    CONSTRAINT uq_inventory_movements_idempotency UNIQUE (business_id, idempotency_key),
    CONSTRAINT chk_inventory_movements_depleted_value CHECK (quantity_after > 0 OR value_after = 0),
    CONSTRAINT chk_inventory_movements_direction CHECK (
        (movement_type IN ('OPENING', 'RECEIPT', 'CUSTOMER_RETURN', 'ADJUSTMENT_IN')
            AND quantity_delta > 0 AND value_delta >= 0)
        OR (movement_type IN ('ISSUE', 'SUPPLIER_RETURN', 'ADJUSTMENT_OUT')
            AND quantity_delta < 0 AND value_delta <= 0)
        OR (movement_type = 'REVERSAL'
            AND ((quantity_delta > 0 AND value_delta >= 0) OR (quantity_delta < 0 AND value_delta <= 0)))
    ),
    CONSTRAINT chk_inventory_movements_purpose CHECK (
        (movement_type = 'ISSUE' AND purpose IS NOT NULL)
        -- CUSTOMER_RETURN returns stock against one earlier ISSUE (customer return for SALE, internal return
        -- for PROJECT/SAMPLE/INTERNAL) and inherits that issue's purpose, project and sample.
        OR (movement_type = 'CUSTOMER_RETURN' AND purpose IS NOT NULL AND related_movement_id IS NOT NULL)
        OR (movement_type = 'REVERSAL' AND related_movement_id IS NOT NULL)
        OR (movement_type NOT IN ('ISSUE', 'CUSTOMER_RETURN', 'REVERSAL') AND purpose IS NULL)
    ),
    -- PROJECT consumption always names its project; a SALE may name the project/order it was sold for.
    CONSTRAINT chk_inventory_movements_project CHECK (
        (COALESCE(purpose, '') = 'PROJECT' AND project_id IS NOT NULL)
        OR (COALESCE(purpose, '') = 'SALE')
        OR (COALESCE(purpose, '') NOT IN ('PROJECT', 'SALE') AND project_id IS NULL)
    ),
    CONSTRAINT chk_inventory_movements_sample CHECK ((COALESCE(purpose, '') = 'SAMPLE') = (sample_id IS NOT NULL)),
    CONSTRAINT fk_inventory_movements_item
        FOREIGN KEY (business_id, item_id) REFERENCES inventory_items (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_movements_project
        FOREIGN KEY (business_id, project_id) REFERENCES projects (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_movements_sample
        FOREIGN KEY (business_id, sample_id) REFERENCES sample_activities (business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_movements_related
        FOREIGN KEY (business_id, related_movement_id) REFERENCES inventory_movements (business_id, id) ON DELETE RESTRICT
);

-- A movement can be reversed at most once.
CREATE UNIQUE INDEX uq_inventory_movements_single_reversal
    ON inventory_movements (business_id, related_movement_id)
    WHERE movement_type = 'REVERSAL';
CREATE INDEX idx_inventory_movements_item_date
    ON inventory_movements (business_id, item_id, sequence_no DESC);
CREATE INDEX idx_inventory_movements_date_purpose
    ON inventory_movements (business_id, movement_date, purpose);
CREATE INDEX idx_inventory_movements_project
    ON inventory_movements (business_id, project_id) WHERE project_id IS NOT NULL;
CREATE INDEX idx_inventory_movements_sample
    ON inventory_movements (business_id, sample_id) WHERE sample_id IS NOT NULL;
CREATE INDEX idx_inventory_movements_source
    ON inventory_movements (business_id, source_type, source_id) WHERE source_id IS NOT NULL;

CREATE TABLE inventory_positions (
    business_id UUID NOT NULL REFERENCES businesses(id),
    item_id UUID NOT NULL,
    quantity NUMERIC(15, 6) NOT NULL CHECK (quantity >= 0),
    value NUMERIC(19, 4) NOT NULL CHECK (value >= 0),
    last_sequence_no BIGINT NOT NULL CHECK (last_sequence_no >= 1),
    last_movement_date DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_inventory_positions PRIMARY KEY (business_id, item_id),
    CONSTRAINT chk_inventory_positions_depleted CHECK (quantity > 0 OR value = 0),
    CONSTRAINT fk_inventory_positions_item
        FOREIGN KEY (business_id, item_id) REFERENCES inventory_items (business_id, id) ON DELETE RESTRICT
);

CREATE FUNCTION enforce_inventory_movement_history() RETURNS TRIGGER AS $$
BEGIN
    IF TG_TABLE_NAME = 'inventory_movements' THEN
        RAISE EXCEPTION 'Inventory movements are append-only; record a REVERSAL instead'
            USING ERRCODE = '55000';
    END IF;
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'Inventory positions cannot be deleted' USING ERRCODE = '55000';
    END IF;
    IF NEW.business_id IS DISTINCT FROM OLD.business_id
       OR NEW.item_id IS DISTINCT FROM OLD.item_id
       OR NEW.version <= OLD.version
       OR NEW.last_sequence_no <= OLD.last_sequence_no THEN
        RAISE EXCEPTION 'Inventory position identity is immutable and every change must follow a new movement'
            USING ERRCODE = '55000';
    END IF;
    -- The position must equal the state recorded on its latest movement.
    IF NOT EXISTS (
        SELECT 1 FROM inventory_movements m
        WHERE m.business_id = NEW.business_id AND m.item_id = NEW.item_id
          AND m.sequence_no = NEW.last_sequence_no
          AND m.quantity_after = NEW.quantity AND m.value_after = NEW.value
    ) THEN
        RAISE EXCEPTION 'Inventory position must match its latest movement' USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_inventory_movements_append_only
    BEFORE UPDATE OR DELETE ON inventory_movements
    FOR EACH ROW EXECUTE FUNCTION enforce_inventory_movement_history();
CREATE TRIGGER trg_inventory_movements_no_truncate
    BEFORE TRUNCATE ON inventory_movements
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_inventory_movement_history();
CREATE TRIGGER trg_inventory_positions_guard
    BEFORE UPDATE OR DELETE ON inventory_positions
    FOR EACH ROW EXECUTE FUNCTION enforce_inventory_movement_history();
CREATE TRIGGER trg_inventory_positions_no_truncate
    BEFORE TRUNCATE ON inventory_positions
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_inventory_movement_history();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT ON TABLE inventory_movements TO finsight_app;
        REVOKE UPDATE, DELETE, TRUNCATE ON TABLE inventory_movements FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE inventory_positions TO finsight_app;
        REVOKE DELETE, TRUNCATE ON TABLE inventory_positions FROM finsight_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON TABLE inventory_movements, inventory_positions TO finsight_backup;
    END IF;
END $$;
