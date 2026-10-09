-- Additive MVP identity and OCR provenance. Historical migrations remain unchanged.
ALTER TABLE businesses ADD COLUMN business_name VARCHAR(120) NOT NULL DEFAULT 'My business';
ALTER TABLE businesses ADD CONSTRAINT ck_business_name_nonblank CHECK (length(trim(business_name)) > 0);

ALTER TABLE uploaded_documents ADD COLUMN reviewed_data JSONB;
ALTER TABLE uploaded_documents ADD COLUMN extraction_provenance VARCHAR(20) NOT NULL DEFAULT 'ORIGINAL_OCR';
-- Old releases overwrote extracted_data on correction. Its original source cannot be recovered.
UPDATE uploaded_documents SET extraction_provenance = 'LEGACY_UNKNOWN' WHERE extracted_data IS NOT NULL;
ALTER TABLE uploaded_documents ADD CONSTRAINT ck_extraction_provenance
    CHECK (extraction_provenance IN ('ORIGINAL_OCR', 'LEGACY_UNKNOWN'));

CREATE TABLE document_corrections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL,
    business_id UUID NOT NULL,
    actor_user_id UUID,
    previous_data JSONB,
    new_data JSONB NOT NULL,
    changed_fields JSONB NOT NULL CHECK (jsonb_typeof(changed_fields) = 'array'),
    corrected_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Snapshot identifiers intentionally have no cascading FK: deleting an unconfirmed draft
-- must not erase its correction history. Access still requires its tenant-scoped document.
CREATE INDEX idx_document_corrections_scope ON document_corrections (business_id, document_id, corrected_at);
REVOKE UPDATE, DELETE, TRUNCATE ON document_corrections FROM finsight_app;
GRANT SELECT, INSERT ON document_corrections TO finsight_app;

CREATE FUNCTION prevent_document_correction_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Document correction history is append-only' USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_document_corrections_no_update_delete BEFORE UPDATE OR DELETE ON document_corrections
    FOR EACH ROW EXECUTE FUNCTION prevent_document_correction_mutation();
CREATE TRIGGER trg_document_corrections_no_truncate BEFORE TRUNCATE ON document_corrections
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_document_correction_mutation();

CREATE FUNCTION protect_document_extraction() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.extracted_data IS NOT NULL AND NEW.extracted_data IS DISTINCT FROM OLD.extracted_data THEN
        RAISE EXCEPTION 'Original document extraction is immutable' USING ERRCODE = '55000';
    END IF;
    IF NEW.extraction_provenance IS DISTINCT FROM OLD.extraction_provenance THEN
        RAISE EXCEPTION 'Document extraction provenance is immutable' USING ERRCODE = '55000';
    END IF;
    IF OLD.processing_status = 'confirmed' AND NEW.reviewed_data IS DISTINCT FROM OLD.reviewed_data THEN
        RAISE EXCEPTION 'Confirmed document review is immutable' USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_document_extraction_immutable BEFORE UPDATE ON uploaded_documents
    FOR EACH ROW EXECUTE FUNCTION protect_document_extraction();
