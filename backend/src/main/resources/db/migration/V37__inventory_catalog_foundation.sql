-- F08 Phase A: Inventory item catalog foundation.
-- Catalog identity ONLY. This table holds no quantity, no on-hand stock, no inventory value,
-- no average cost, no opening stock and no movement history. Creating an item creates no stock.
-- No MonthlyRecord, health-score, COGS, ledger, project, commercial-document or estimate state is
-- read or changed. Weighted-average-cost valuation in Phase A is a stateless, non-authoritative
-- preview computed in the application and never persisted.

CREATE TABLE inventory_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    sku VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    item_kind VARCHAR(16) NOT NULL
        CHECK (item_kind IN ('RAW_MATERIAL', 'RESALE_GOOD', 'CONSUMABLE', 'FINISHED_GOOD')),
    category VARCHAR(80) NULL,
    reorder_level NUMERIC(15, 6) NULL CHECK (reorder_level IS NULL OR reorder_level >= 0),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by UUID NOT NULL REFERENCES app_users(id),
    updated_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_inventory_items_scoped_identity UNIQUE (business_id, id),
    -- SKUs are stored upper-cased, so this constraint makes them unique per business case-insensitively.
    CONSTRAINT uq_inventory_items_business_sku UNIQUE (business_id, sku),
    CONSTRAINT chk_inventory_items_sku_normalized CHECK (
        sku = upper(btrim(sku))
        AND sku ~ '^[A-Z0-9][A-Z0-9._/-]{0,39}$'
    ),
    CONSTRAINT chk_inventory_items_name_nonblank CHECK (btrim(name) <> '' AND name = btrim(name)),
    CONSTRAINT chk_inventory_items_unit_nonblank CHECK (btrim(unit) <> '' AND unit = btrim(unit)),
    CONSTRAINT chk_inventory_items_category_nonblank CHECK (
        category IS NULL OR (btrim(category) <> '' AND category = btrim(category))
    )
);

CREATE INDEX idx_inventory_items_business_status_sku
    ON inventory_items (business_id, status, sku);
CREATE INDEX idx_inventory_items_business_kind
    ON inventory_items (business_id, item_kind, sku);

-- Catalog lifecycle guard:
-- 1. TRUNCATE and DELETE are forbidden; items are archived, never destroyed.
-- 2. Identity, tenant and creation provenance are immutable.
-- 3. Every update must strictly increase the version (optimistic concurrency).
-- 4. ARCHIVED is terminal and the archived row is immutable.
CREATE FUNCTION enforce_inventory_item_lifecycle() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'Inventory catalog tables cannot be truncated'
            USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Inventory items cannot be deleted; archive them instead'
            USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'UPDATE' AND (
        NEW.id IS DISTINCT FROM OLD.id
        OR NEW.business_id IS DISTINCT FROM OLD.business_id
        OR NEW.created_by IS DISTINCT FROM OLD.created_by
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR NEW.version <= OLD.version
        OR OLD.status = 'ARCHIVED'
    ) THEN
        RAISE EXCEPTION 'Inventory item identity, creation provenance, archived state and monotonic version are immutable'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_inventory_items_lifecycle
    BEFORE UPDATE OR DELETE ON inventory_items
    FOR EACH ROW EXECUTE FUNCTION enforce_inventory_item_lifecycle();
CREATE TRIGGER trg_inventory_items_no_truncate
    BEFORE TRUNCATE ON inventory_items
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_inventory_item_lifecycle();

-- Least-privilege runtime and backup grants. The runtime role has no DELETE: archive is the only exit.
REVOKE ALL ON inventory_items FROM PUBLIC;
DO $$ BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE ALL ON inventory_items FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE ON inventory_items TO finsight_app;
    END IF;
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        REVOKE ALL ON inventory_items FROM finsight_backup;
        GRANT SELECT ON inventory_items TO finsight_backup;
    END IF;
END $$;
