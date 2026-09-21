CREATE TABLE recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES business_profiles(user_id),
    month CHAR(7) NOT NULL,
    recommendation_text TEXT NOT NULL,
    category VARCHAR(40) NOT NULL,
    priority VARCHAR(10) NOT NULL CHECK (priority IN ('high', 'medium', 'low')),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_recommendations_user_id_created_at
    ON recommendations(user_id, created_at DESC);
