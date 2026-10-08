-- Phase 1.5 S7: Append-only Security Audit Events Foundation

CREATE TABLE IF NOT EXISTS security_audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_type VARCHAR(60) NOT NULL,
    actor_user_id UUID NULL,
    actor_platform_role VARCHAR(30) NULL,
    business_id UUID NULL,
    target_type VARCHAR(50) NULL,
    target_id VARCHAR(100) NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    source_ip VARCHAR(45) NULL,
    user_agent VARCHAR(512) NULL,
    request_id VARCHAR(64) NULL,
    is_system BOOLEAN NOT NULL DEFAULT false,
    metadata JSONB NULL,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES app_users(id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_business FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_occurred_at ON security_audit_events (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON security_audit_events (event_type);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON security_audit_events (actor_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_business ON security_audit_events (business_id);
CREATE INDEX IF NOT EXISTS idx_audit_target ON security_audit_events (target_type, target_id);

-- Explicitly override S6 default privileges:
-- Deny UPDATE, DELETE, TRUNCATE to normal application runtime role finsight_app
REVOKE UPDATE, DELETE, TRUNCATE ON TABLE security_audit_events FROM finsight_app;
GRANT INSERT, SELECT ON TABLE security_audit_events TO finsight_app;

-- Defense-in-depth immutability triggers for runtime operations:
-- Ensures security_audit_events is append-only for normal runtime role.
-- (Note: finsight_dba retains disaster recovery privileges; table-owner finsight_migrator can alter structure during migrations).
CREATE OR REPLACE FUNCTION prevent_audit_log_mutation() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF (OLD.id IS NOT DISTINCT FROM NEW.id)
           AND (OLD.occurred_at IS NOT DISTINCT FROM NEW.occurred_at)
           AND (OLD.event_type IS NOT DISTINCT FROM NEW.event_type)
           AND (OLD.actor_platform_role IS NOT DISTINCT FROM NEW.actor_platform_role)
           AND (OLD.target_type IS NOT DISTINCT FROM NEW.target_type)
           AND (OLD.target_id IS NOT DISTINCT FROM NEW.target_id)
           AND (OLD.outcome IS NOT DISTINCT FROM NEW.outcome)
           AND (OLD.source_ip IS NOT DISTINCT FROM NEW.source_ip)
           AND (OLD.user_agent IS NOT DISTINCT FROM NEW.user_agent)
           AND (OLD.request_id IS NOT DISTINCT FROM NEW.request_id)
           AND (OLD.is_system IS NOT DISTINCT FROM NEW.is_system)
           AND (OLD.metadata IS NOT DISTINCT FROM NEW.metadata)
           AND (NEW.actor_user_id IS NULL OR NEW.actor_user_id = OLD.actor_user_id)
           AND (NEW.business_id IS NULL OR NEW.business_id = OLD.business_id)
           AND (NEW.actor_user_id IS DISTINCT FROM OLD.actor_user_id OR NEW.business_id IS DISTINCT FROM OLD.business_id) THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'TAMPER ATTEMPT: Table security_audit_events is append-only. Mutation is forbidden.'
        USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_audit_no_update_delete
    BEFORE UPDATE OR DELETE ON security_audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_mutation();

CREATE OR REPLACE TRIGGER trg_audit_no_truncate
    BEFORE TRUNCATE ON security_audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_audit_log_mutation();
