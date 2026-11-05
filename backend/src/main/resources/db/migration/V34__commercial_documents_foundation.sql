-- F04 Phase A: commercial document foundation (customer INVOICE and supplier BILL).
-- These rows RECORD commercial documents only. financial_effect is fixed to NONE: no ledger
-- event, receivable, payable, obligation, settlement, cash, revenue/expense recognition, COGS,
-- inventory, MonthlyRecord, document_financial_contributions or health-score change is made.
-- Lifecycle: DRAFT -> FINALIZED -> VOID. ISSUED/APPROVED remain reserved for future C04 integration.

CREATE TABLE commercial_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    kind VARCHAR(8) NOT NULL CHECK (kind IN ('INVOICE', 'BILL')),
    status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'FINALIZED', 'VOID')),
    document_number VARCHAR(16) NULL,
    -- Free-text customer/supplier name; AES-256-GCM application envelope only, never plaintext.
    counterparty_name_ciphertext TEXT NULL,
    document_date DATE NULL,
    due_date DATE NULL,
    notes VARCHAR(1000) NULL,
    subtotal NUMERIC(18, 2) NULL,
    total NUMERIC(18, 2) NULL,
    base_currency CHAR(3) NOT NULL DEFAULT 'PKR' CHECK (base_currency = 'PKR'),
    financial_effect VARCHAR(8) NOT NULL DEFAULT 'NONE' CHECK (financial_effect = 'NONE'),
    calculation_version VARCHAR(32) NOT NULL DEFAULT 'f04-commercial-v1'
        CHECK (calculation_version = 'f04-commercial-v1'),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by_user_id UUID NOT NULL REFERENCES app_users(id),
    updated_by_user_id UUID NOT NULL REFERENCES app_users(id),
    finalized_by_user_id UUID NULL REFERENCES app_users(id),
    finalized_at TIMESTAMPTZ NULL,
    voided_by_user_id UUID NULL REFERENCES app_users(id),
    voided_at TIMESTAMPTZ NULL,
    void_reason VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_commercial_documents_scoped_identity UNIQUE (id, business_id),
    CONSTRAINT chk_commercial_documents_number_format CHECK (
        document_number IS NULL
        OR (kind = 'INVOICE' AND document_number ~ '^INV-[0-9]{6}$')
        OR (kind = 'BILL' AND document_number ~ '^BILL-[0-9]{6}$')
    ),
    CONSTRAINT chk_commercial_documents_counterparty_envelope CHECK (
        counterparty_name_ciphertext IS NULL
        OR (length(counterparty_name_ciphertext) <= 2048
            AND counterparty_name_ciphertext ~ '^enc:v1:[A-Za-z0-9_-]{1,32}:[A-Za-z0-9+/=]+$')
    ),
    CONSTRAINT chk_commercial_documents_notes_nonblank CHECK (notes IS NULL OR btrim(notes) <> ''),
    CONSTRAINT chk_commercial_documents_dates CHECK (
        due_date IS NULL OR document_date IS NULL OR due_date >= document_date
    ),
    -- Null (no lines yet) and an explicit zero remain distinct; Phase A total is the line subtotal.
    CONSTRAINT chk_commercial_documents_totals CHECK (
        (subtotal IS NULL AND total IS NULL)
        OR (subtotal IS NOT NULL AND total IS NOT NULL AND subtotal >= 0 AND total = subtotal)
    ),
    CONSTRAINT chk_commercial_documents_lifecycle CHECK (
        (status = 'DRAFT'
            AND document_number IS NULL
            AND finalized_by_user_id IS NULL AND finalized_at IS NULL
            AND voided_by_user_id IS NULL AND voided_at IS NULL AND void_reason IS NULL)
        OR (status IN ('FINALIZED', 'VOID')
            AND document_number IS NOT NULL
            AND counterparty_name_ciphertext IS NOT NULL
            AND document_date IS NOT NULL
            AND total IS NOT NULL AND total > 0
            AND finalized_by_user_id IS NOT NULL AND finalized_at IS NOT NULL
            AND ((status = 'FINALIZED'
                    AND voided_by_user_id IS NULL AND voided_at IS NULL AND void_reason IS NULL)
                OR (status = 'VOID'
                    AND voided_by_user_id IS NOT NULL AND voided_at IS NOT NULL
                    AND void_reason IS NOT NULL AND btrim(void_reason) <> '')))
    )
);

-- Tenant-scoped numbering: a number is unique per business and kind, and only once assigned.
CREATE UNIQUE INDEX uq_commercial_documents_number
    ON commercial_documents (business_id, kind, document_number)
    WHERE document_number IS NOT NULL;
CREATE INDEX idx_commercial_documents_business_kind_status
    ON commercial_documents (business_id, kind, status, updated_at DESC, id);

CREATE TABLE commercial_document_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    document_id UUID NOT NULL,
    line_number INT NOT NULL CHECK (line_number >= 1 AND line_number <= 100),
    description VARCHAR(200) NOT NULL CHECK (btrim(description) <> ''),
    quantity NUMERIC(15, 6) NOT NULL CHECK (quantity > 0),
    unit VARCHAR(40) NULL CHECK (unit IS NULL OR btrim(unit) <> ''),
    unit_price NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
    -- No rounding policy: the stored 2-decimal amount must equal the exact product.
    line_amount NUMERIC(18, 2) NOT NULL CHECK (line_amount >= 0 AND line_amount = quantity * unit_price),
    CONSTRAINT uq_commercial_document_lines_number UNIQUE (business_id, document_id, line_number),
    CONSTRAINT fk_commercial_document_lines_document
        FOREIGN KEY (document_id, business_id)
        REFERENCES commercial_documents (id, business_id) ON DELETE CASCADE
);

CREATE INDEX idx_commercial_document_lines_document
    ON commercial_document_lines (business_id, document_id, line_number);

CREATE TABLE commercial_number_sequences (
    business_id UUID NOT NULL REFERENCES businesses(id),
    kind VARCHAR(8) NOT NULL CHECK (kind IN ('INVOICE', 'BILL')),
    next_value BIGINT NOT NULL DEFAULT 1 CHECK (next_value >= 1 AND next_value <= 1000000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_commercial_number_sequences PRIMARY KEY (business_id, kind)
);

-- 1. TRUNCATE is forbidden on every commercial table. Documents are always created as DRAFT.
-- 2. Only DRAFT documents can be deleted; FINALIZED and VOID documents are permanent.
-- 3. Identity, kind, creation provenance and currency never change; version is strictly monotonic.
-- 4. DRAFT -> FINALIZED requires at least one stored line whose exact sum equals the subtotal.
-- 5. FINALIZED content is immutable; the only permitted change is the FINALIZED -> VOID transition.
-- 6. VOID documents are completely immutable. DRAFT cannot be voided (delete it instead).
-- 7. Lines can be inserted, updated or deleted only while both their original and new parent are DRAFT.
CREATE FUNCTION enforce_commercial_document_immutability() RETURNS TRIGGER AS $$
DECLARE
    parent_status VARCHAR(12);
    line_count INT;
    line_sum NUMERIC;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'Commercial document tables cannot be truncated'
            USING ERRCODE = '55000';
    END IF;

    IF TG_TABLE_NAME = 'commercial_documents' THEN
        IF TG_OP = 'INSERT' THEN
            IF NEW.status <> 'DRAFT' THEN
                RAISE EXCEPTION 'Commercial documents must be created as DRAFT and finalized through the lifecycle'
                    USING ERRCODE = '55000';
            END IF;
            RETURN NEW;
        END IF;

        IF TG_OP = 'DELETE' THEN
            IF OLD.status <> 'DRAFT' THEN
                RAISE EXCEPTION 'Finalized and voided commercial documents are permanent and cannot be deleted'
                    USING ERRCODE = '55000';
            END IF;
            RETURN OLD;
        END IF;

        IF NEW.id IS DISTINCT FROM OLD.id
           OR NEW.business_id IS DISTINCT FROM OLD.business_id
           OR NEW.kind IS DISTINCT FROM OLD.kind
           OR NEW.base_currency IS DISTINCT FROM OLD.base_currency
           OR NEW.created_by_user_id IS DISTINCT FROM OLD.created_by_user_id
           OR NEW.created_at IS DISTINCT FROM OLD.created_at
           OR NEW.version <= OLD.version THEN
            RAISE EXCEPTION 'Commercial document identity, kind, currency, creation provenance and monotonic version are immutable'
                USING ERRCODE = '55000';
        END IF;

        IF OLD.status = 'VOID' THEN
            RAISE EXCEPTION 'Voided commercial documents are immutable'
                USING ERRCODE = '55000';
        END IF;

        IF OLD.status = 'FINALIZED' THEN
            IF NEW.status <> 'VOID'
               OR NEW.document_number IS DISTINCT FROM OLD.document_number
               OR NEW.counterparty_name_ciphertext IS DISTINCT FROM OLD.counterparty_name_ciphertext
               OR NEW.document_date IS DISTINCT FROM OLD.document_date
               OR NEW.due_date IS DISTINCT FROM OLD.due_date
               OR NEW.notes IS DISTINCT FROM OLD.notes
               OR NEW.subtotal IS DISTINCT FROM OLD.subtotal
               OR NEW.total IS DISTINCT FROM OLD.total
               OR NEW.financial_effect IS DISTINCT FROM OLD.financial_effect
               OR NEW.calculation_version IS DISTINCT FROM OLD.calculation_version
               OR NEW.finalized_by_user_id IS DISTINCT FROM OLD.finalized_by_user_id
               OR NEW.finalized_at IS DISTINCT FROM OLD.finalized_at THEN
                RAISE EXCEPTION 'Finalized commercial documents are immutable except for the FINALIZED -> VOID transition'
                    USING ERRCODE = '55000';
            END IF;
            RETURN NEW;
        END IF;

        -- OLD.status = 'DRAFT'
        IF NEW.status = 'VOID' THEN
            RAISE EXCEPTION 'Draft commercial documents cannot be voided; delete the draft instead'
                USING ERRCODE = '55000';
        END IF;
        IF NEW.status = 'FINALIZED' THEN
            SELECT count(*), sum(line_amount) INTO line_count, line_sum
            FROM commercial_document_lines
            WHERE business_id = NEW.business_id AND document_id = NEW.id;
            IF line_count = 0 OR line_sum IS DISTINCT FROM NEW.subtotal THEN
                RAISE EXCEPTION 'A commercial document can be finalized only when its stored lines sum exactly to its subtotal'
                    USING ERRCODE = '55000';
            END IF;
        END IF;
        RETURN NEW;
    END IF;

    IF TG_TABLE_NAME = 'commercial_document_lines' THEN
        IF TG_OP IN ('UPDATE', 'DELETE') THEN
            SELECT status INTO parent_status
            FROM commercial_documents
            WHERE id = OLD.document_id AND business_id = OLD.business_id;
            -- A missing parent is only possible during the cascade from an allowed DRAFT deletion.
            IF parent_status IN ('FINALIZED', 'VOID') THEN
                RAISE EXCEPTION 'Lines of finalized or voided commercial documents are immutable'
                    USING ERRCODE = '55000';
            END IF;
        END IF;
        IF TG_OP IN ('INSERT', 'UPDATE') THEN
            SELECT status INTO parent_status
            FROM commercial_documents
            WHERE id = NEW.document_id AND business_id = NEW.business_id;
            IF parent_status IS DISTINCT FROM 'DRAFT' THEN
                RAISE EXCEPTION 'Lines can be changed only on draft commercial documents'
                    USING ERRCODE = '55000';
            END IF;
            RETURN NEW;
        END IF;
        RETURN OLD;
    END IF;

    IF TG_TABLE_NAME = 'commercial_number_sequences' THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'Commercial number sequences are permanent; numbers are never reset or reused'
                USING ERRCODE = '55000';
        END IF;
        IF NEW.business_id IS DISTINCT FROM OLD.business_id
           OR NEW.kind IS DISTINCT FROM OLD.kind
           OR NEW.created_at IS DISTINCT FROM OLD.created_at
           OR NEW.next_value <= OLD.next_value THEN
            RAISE EXCEPTION 'Commercial number sequences are monotonic and never reset'
                USING ERRCODE = '55000';
        END IF;
        RETURN NEW;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_commercial_documents_immutability
    BEFORE INSERT OR UPDATE OR DELETE ON commercial_documents
    FOR EACH ROW EXECUTE FUNCTION enforce_commercial_document_immutability();
CREATE TRIGGER trg_commercial_documents_no_truncate
    BEFORE TRUNCATE ON commercial_documents
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_commercial_document_immutability();

CREATE TRIGGER trg_commercial_document_lines_immutability
    BEFORE INSERT OR UPDATE OR DELETE ON commercial_document_lines
    FOR EACH ROW EXECUTE FUNCTION enforce_commercial_document_immutability();
CREATE TRIGGER trg_commercial_document_lines_no_truncate
    BEFORE TRUNCATE ON commercial_document_lines
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_commercial_document_immutability();

CREATE TRIGGER trg_commercial_number_sequences_monotonic
    BEFORE UPDATE OR DELETE ON commercial_number_sequences
    FOR EACH ROW EXECUTE FUNCTION enforce_commercial_document_immutability();
CREATE TRIGGER trg_commercial_number_sequences_no_truncate
    BEFORE TRUNCATE ON commercial_number_sequences
    FOR EACH STATEMENT EXECUTE FUNCTION enforce_commercial_document_immutability();

-- Least-privilege runtime and backup grants. Draft-only deletion is enforced by the trigger;
-- sequences are never deleted, so the runtime role has no DELETE on them.
REVOKE ALL ON commercial_documents, commercial_document_lines, commercial_number_sequences FROM PUBLIC;
DO $$ BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_app') THEN
        REVOKE ALL ON commercial_documents, commercial_document_lines, commercial_number_sequences FROM finsight_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON commercial_documents, commercial_document_lines TO finsight_app;
        GRANT SELECT, INSERT, UPDATE ON commercial_number_sequences TO finsight_app;
    END IF;
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        REVOKE ALL ON commercial_documents, commercial_document_lines, commercial_number_sequences FROM finsight_backup;
        GRANT SELECT ON commercial_documents, commercial_document_lines, commercial_number_sequences TO finsight_backup;
    END IF;
END $$;
