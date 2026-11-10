-- V50: WhatsApp inbound conversational Copilot (S-B06, Developer B).
--
-- Phone numbers are never stored in plaintext here: phone_hash is HMAC-SHA256(server key, E.164) used only for
-- lookup, plus the last 4 digits for display. Message bodies and replies are AES-256-GCM application ciphertext
-- (existing SensitiveDataCipher). A sender is mapped to exactly one business user only after proving possession
-- of the number (LINK code sent FROM that WhatsApp number) and with explicit consent.

CREATE TABLE whatsapp_inbound_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    user_id UUID NOT NULL REFERENCES app_users(id),
    phone_hash VARCHAR(64) NOT NULL CHECK (phone_hash ~ '^[0-9a-f]{64}$'),
    phone_last4 VARCHAR(4) NOT NULL CHECK (phone_last4 ~ '^[0-9]{4}$'),
    status VARCHAR(12) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED', 'EXPIRED')),
    verification_code_hash VARCHAR(64) NULL,
    verification_expires_at TIMESTAMPTZ NULL,
    verification_attempts INT NOT NULL DEFAULT 0 CHECK (verification_attempts >= 0),
    language VARCHAR(2) NOT NULL DEFAULT 'en' CHECK (language IN ('en', 'ur')),
    consent_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ NULL,
    revoked_at TIMESTAMPTZ NULL,
    revoke_reason VARCHAR(120) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT chk_whatsapp_inbound_links_state CHECK (
        (status = 'PENDING' AND verification_code_hash IS NOT NULL AND verification_expires_at IS NOT NULL AND activated_at IS NULL)
        OR (status = 'ACTIVE' AND activated_at IS NOT NULL AND revoked_at IS NULL)
        OR (status IN ('REVOKED', 'EXPIRED') AND revoked_at IS NOT NULL)
    )
);

-- One live (pending or active) mapping per phone and per user/business.
CREATE UNIQUE INDEX uq_whatsapp_inbound_links_live_phone
    ON whatsapp_inbound_links (phone_hash) WHERE status IN ('PENDING', 'ACTIVE');
CREATE UNIQUE INDEX uq_whatsapp_inbound_links_live_user
    ON whatsapp_inbound_links (business_id, user_id) WHERE status IN ('PENDING', 'ACTIVE');

CREATE TABLE whatsapp_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    link_id UUID NOT NULL REFERENCES whatsapp_inbound_links(id),
    business_id UUID NOT NULL REFERENCES businesses(id),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    turns_ciphertext TEXT NULL,
    status VARCHAR(8) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_whatsapp_conversations_link ON whatsapp_conversations (link_id, status, last_message_at DESC);

CREATE TABLE whatsapp_inbound_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_message_id VARCHAR(160) NOT NULL,
    phone_hash VARCHAR(64) NOT NULL,
    business_id UUID NULL REFERENCES businesses(id),
    link_id UUID NULL REFERENCES whatsapp_inbound_links(id),
    conversation_id UUID NULL REFERENCES whatsapp_conversations(id),
    message_type VARCHAR(20) NOT NULL,
    body_ciphertext TEXT NULL,
    provider_timestamp TIMESTAMPTZ NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RECEIVED', 'ANSWERED', 'LINKED', 'UNLINKED_SENDER', 'NO_CONSENT',
        'RATE_LIMITED', 'STALE', 'UNSUPPORTED_TYPE', 'REVOKED', 'ACCESS_DENIED', 'HELP', 'FAILED')),
    reason VARCHAR(120) NULL,
    reply_ciphertext TEXT NULL,
    reply_status VARCHAR(12) NULL CHECK (reply_status IS NULL OR reply_status IN ('SENT', 'FAILED', 'SKIPPED')),
    reply_provider_message_id VARCHAR(160) NULL,
    processed_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_whatsapp_inbound_messages_provider_id UNIQUE (provider_message_id)
);
CREATE INDEX idx_whatsapp_inbound_messages_phone_time ON whatsapp_inbound_messages (phone_hash, received_at DESC);
CREATE INDEX idx_whatsapp_inbound_messages_business ON whatsapp_inbound_messages (business_id, received_at DESC);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE whatsapp_inbound_links, whatsapp_conversations, whatsapp_inbound_messages TO finsight_app;
        REVOKE DELETE, TRUNCATE ON TABLE whatsapp_inbound_links, whatsapp_conversations, whatsapp_inbound_messages FROM finsight_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        GRANT SELECT ON TABLE whatsapp_inbound_links, whatsapp_conversations, whatsapp_inbound_messages TO finsight_backup;
    END IF;
END $$;
