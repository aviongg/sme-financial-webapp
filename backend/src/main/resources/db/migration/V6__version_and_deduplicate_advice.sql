-- Advice is a cache of persisted scores. Legacy rows are refreshed on first read.
ALTER TABLE insights
    ADD COLUMN source_version VARCHAR(64),
    ADD COLUMN language VARCHAR(5) CHECK (language IN ('en', 'ur')),
    ADD COLUMN source_computed_at TIMESTAMP;
ALTER TABLE recommendations
    ADD COLUMN source_version VARCHAR(64),
    ADD COLUMN language VARCHAR(5) CHECK (language IN ('en', 'ur')),
    ADD COLUMN source_computed_at TIMESTAMP;

-- Retain the newest cached item if earlier concurrent generation duplicated it.
DELETE FROM insights WHERE id IN (
    SELECT id FROM (
        SELECT id, row_number() OVER (
            PARTITION BY user_id, month, category ORDER BY created_at DESC, id DESC
        ) AS row_number FROM insights
    ) duplicates WHERE row_number > 1
);
DELETE FROM recommendations WHERE id IN (
    SELECT id FROM (
        SELECT id, row_number() OVER (
            PARTITION BY user_id, month, category ORDER BY created_at DESC, id DESC
        ) AS row_number FROM recommendations
    ) duplicates WHERE row_number > 1
);
ALTER TABLE insights ADD CONSTRAINT uq_insights_user_month_category UNIQUE (user_id, month, category);
ALTER TABLE recommendations ADD CONSTRAINT uq_recommendations_user_month_category UNIQUE (user_id, month, category);
