-- F02: additive capability preferences; no financial or historical-score changes.
CREATE TABLE business_configuration (
    business_id UUID PRIMARY KEY REFERENCES businesses(id) ON DELETE CASCADE,
    operating_profile VARCHAR(24) NOT NULL CHECK (operating_profile IN ('MANUFACTURING','RETAIL_TRADE','SERVICES','PROJECT_ORDER')),
    operating_answers JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(operating_answers) = 'object'),
    requested_modules JSONB NOT NULL CHECK (jsonb_typeof(requested_modules) = 'array'),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    configured BOOLEAN NOT NULL DEFAULT false,
    updated_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO business_configuration (business_id, operating_profile, requested_modules)
SELECT b.id, CASE p.business_type WHEN 'manufacturing' THEN 'MANUFACTURING'
    WHEN 'trade' THEN 'RETAIL_TRADE' WHEN 'retail' THEN 'RETAIL_TRADE' ELSE 'SERVICES' END,
    '["core_finance","documents","whatsapp","ai"]'::jsonb
FROM businesses b LEFT JOIN business_profiles p ON p.user_id = b.id;

-- Existing role bootstrap normally supplies these defaults. Explicit grants also
-- cover upgrades where only the established runtime/backup roles already exist.
DO $$ BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON business_configuration TO finsight_app;
    END IF;
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON business_configuration TO finsight_backup;
    END IF;
END $$;
