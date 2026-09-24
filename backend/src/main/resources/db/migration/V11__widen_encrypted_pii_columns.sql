-- Phase 1.5 S5: Widen sensitive PII columns for versioned AES-256-GCM ciphertext storage
ALTER TABLE app_users
    ALTER COLUMN full_name TYPE TEXT;

ALTER TABLE business_profiles
    ALTER COLUMN whatsapp_number TYPE VARCHAR(255);

ALTER TABLE whatsapp_deliveries
    ALTER COLUMN destination_number TYPE VARCHAR(255);

ALTER TABLE uploaded_documents
    ALTER COLUMN original_filename TYPE TEXT;
