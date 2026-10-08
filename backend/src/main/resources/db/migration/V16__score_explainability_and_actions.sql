-- Historical evidence was not captured: leave legacy score metadata NULL rather than invent it.
ALTER TABLE score_results ADD COLUMN methodology_version varchar(40);
ALTER TABLE score_results ADD COLUMN explanation jsonb;
ALTER TABLE recommendations ADD COLUMN status varchar(12) NOT NULL DEFAULT 'NEW';
ALTER TABLE recommendations ADD COLUMN status_updated_at timestamp;
ALTER TABLE recommendations ADD CONSTRAINT chk_recommendation_status
    CHECK (status IN ('NEW', 'VIEWED', 'DONE', 'DISMISSED'));
