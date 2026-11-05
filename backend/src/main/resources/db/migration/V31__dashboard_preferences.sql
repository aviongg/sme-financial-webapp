-- F13 stores presentation overrides only. Financial records and business configuration are unchanged.
CREATE TABLE dashboard_preferences (
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    overrides_json JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(overrides_json) = 'array'),
    registry_version VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (business_id, user_id)
);
CREATE INDEX dashboard_preferences_user_idx ON dashboard_preferences(user_id);

DO $$ BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON dashboard_preferences TO finsight_app;
    END IF;
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON dashboard_preferences TO finsight_backup;
    END IF;
END $$;
