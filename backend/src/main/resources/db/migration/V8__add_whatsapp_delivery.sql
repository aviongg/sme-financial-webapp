ALTER TABLE business_profiles
    ADD COLUMN IF NOT EXISTS whatsapp_opted_in_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS whatsapp_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES business_profiles(user_id) ON DELETE CASCADE,
    score_result_id UUID REFERENCES score_results(id) ON DELETE SET NULL,
    target_month CHAR(7) NOT NULL,
    source_fingerprint VARCHAR(64) NOT NULL,
    delivery_cycle VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(150),
    destination_number VARCHAR(25) NOT NULL,
    language VARCHAR(5) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    provider_name VARCHAR(50) NOT NULL,
    provider_message_id VARCHAR(100),
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'pending'
        CHECK (delivery_status IN ('pending', 'sending', 'sent', 'failed', 'indeterminate')),
    attempt_count INT NOT NULL DEFAULT 0,
    scheduled_at TIMESTAMP NOT NULL DEFAULT now(),
    sent_at TIMESTAMP,
    failed_at TIMESTAMP,
    failure_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_whatsapp_deliveries_user_cycle UNIQUE (user_id, delivery_cycle)
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_deliveries_user_id
    ON whatsapp_deliveries(user_id);

CREATE INDEX IF NOT EXISTS idx_whatsapp_deliveries_status
    ON whatsapp_deliveries(delivery_status);

CREATE INDEX IF NOT EXISTS idx_whatsapp_deliveries_cycle
    ON whatsapp_deliveries(delivery_cycle);
