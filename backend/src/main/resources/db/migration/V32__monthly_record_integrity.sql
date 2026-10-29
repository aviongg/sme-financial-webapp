-- Pilot snapshot integrity only. Provenance starts prospectively: no invented historical backfill.
ALTER TABLE monthly_records ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE monthly_records ADD CONSTRAINT uq_monthly_records_scoped_identity UNIQUE (user_id, id, month);
ALTER TABLE uploaded_documents ADD CONSTRAINT uq_uploaded_documents_scoped_identity UNIQUE (user_id, id);

CREATE TABLE document_financial_contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    document_id UUID NOT NULL UNIQUE,
    monthly_record_id UUID NOT NULL,
    target_month CHAR(7) NOT NULL,
    target_classification VARCHAR(20) NOT NULL CHECK (target_classification IN ('revenue', 'operating_expenses', 'cogs', 'none')),
    cash_flow_impact VARCHAR(15) NOT NULL CHECK (cash_flow_impact IN ('cash_inflow', 'cash_outflow', 'none')),
    confirmed_amount NUMERIC(14,2) NOT NULL CHECK (confirmed_amount > 0),
    actor_user_id UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_fingerprint CHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    duplicate_override BOOLEAN NOT NULL DEFAULT false,
    FOREIGN KEY (business_id, document_id) REFERENCES uploaded_documents(user_id, id),
    FOREIGN KEY (business_id, monthly_record_id, target_month) REFERENCES monthly_records(user_id, id, month)
);
CREATE INDEX idx_document_contributions_business_month ON document_financial_contributions(business_id, target_month);
CREATE INDEX idx_document_contributions_fingerprint ON document_financial_contributions(business_id, source_fingerprint);

CREATE FUNCTION prevent_document_contribution_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Document financial contributions are append-only' USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_document_contributions_no_update_delete BEFORE UPDATE OR DELETE ON document_financial_contributions
    FOR EACH ROW EXECUTE FUNCTION prevent_document_contribution_mutation();
CREATE TRIGGER trg_document_contributions_no_truncate BEFORE TRUNCATE ON document_financial_contributions
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_document_contribution_mutation();

REVOKE ALL ON document_financial_contributions FROM PUBLIC;
REVOKE ALL ON document_financial_contributions FROM finsight_app, finsight_backup;
GRANT SELECT, INSERT ON document_financial_contributions TO finsight_app;
GRANT SELECT ON document_financial_contributions TO finsight_backup;