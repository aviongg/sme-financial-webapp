ALTER TABLE business_profiles
    ADD COLUMN payment_behavior VARCHAR(20)
        CHECK (payment_behavior IN ('immediate', '2weeks', '1month_plus', 'irregular')),
    ADD COLUMN ntn_registered BOOLEAN,
    ADD COLUMN business_registered BOOLEAN;
