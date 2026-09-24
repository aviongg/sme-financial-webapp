-- Phase 1.5 S7: Platform Role, Auth Version, and TOTP MFA Tables

-- 1. Extend app_users with platform_role and auth_version
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS platform_role VARCHAR(30) NULL
    CONSTRAINT chk_app_users_platform_role
        CHECK (platform_role IS NULL OR platform_role = 'PLATFORM_ADMIN');

ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS auth_version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_app_users_platform_role ON app_users (platform_role)
    WHERE platform_role IS NOT NULL;

-- 2. Database enforcement: protect platform_role against runtime privilege escalation
-- finsight_app cannot modify platform_role (only operator/deployment roles like finsight_migrator/finsight_dba can)
CREATE OR REPLACE FUNCTION protect_platform_role() RETURNS TRIGGER AS $$
BEGIN
    IF (OLD.platform_role IS DISTINCT FROM NEW.platform_role) AND (current_user = 'finsight_app') THEN
        RAISE EXCEPTION 'Unauthorized: finsight_app cannot modify platform_role'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_protect_platform_role
    BEFORE UPDATE ON app_users
    FOR EACH ROW EXECUTE FUNCTION protect_platform_role();

-- 3. Dedicated user_mfa table
CREATE TABLE IF NOT EXISTS user_mfa (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    totp_secret TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ENABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_at TIMESTAMPTZ NULL,
    enabled_at TIMESTAMPTZ NULL,
    last_used_time_step BIGINT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_mfa_status ON user_mfa (status);

-- 4. Dedicated user_mfa_recovery_codes table
CREATE TABLE IF NOT EXISTS user_mfa_recovery_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    used_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_recovery_codes_user ON user_mfa_recovery_codes (user_id);
