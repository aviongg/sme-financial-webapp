-- Phase S2: Link business_profiles to businesses (tenant integrity)

-- Guarantee zero orphan profiles before adding foreign key constraint
INSERT INTO businesses (id, status, created_at, updated_at)
SELECT user_id, 'ACTIVE', created_at, created_at
FROM business_profiles
ON CONFLICT (id) DO NOTHING;

-- Add foreign key constraint from business_profiles(user_id) to businesses(id) with ON DELETE RESTRICT
ALTER TABLE business_profiles
    ADD CONSTRAINT fk_business_profiles_business
    FOREIGN KEY (user_id) REFERENCES businesses(id)
    ON DELETE RESTRICT;
