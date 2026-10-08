CREATE TABLE insights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES business_profiles(user_id),

    month CHAR(7) NOT NULL,

    insight_text TEXT NOT NULL,

    category VARCHAR(30) NOT NULL,

    priority VARCHAR(10) NOT NULL
        CHECK (priority IN ('high', 'medium', 'low')),

    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_insights_user_id_created_at
    ON insights(user_id, created_at DESC);
