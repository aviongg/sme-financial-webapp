CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE business_profiles (
    user_id UUID PRIMARY KEY,

    business_type VARCHAR(20) NOT NULL
        CHECK (business_type IN ('trade', 'manufacturing', 'services', 'retail')),

    language_preference VARCHAR(5) NOT NULL DEFAULT 'en'
        CHECK (language_preference IN ('en', 'ur')),

    whatsapp_number VARCHAR(20),

    whatsapp_opt_in BOOLEAN NOT NULL DEFAULT false,

    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE monthly_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES business_profiles(user_id),

    month CHAR(7) NOT NULL,

    cash_inflow NUMERIC(14,2) NOT NULL DEFAULT 0,

    cash_outflow NUMERIC(14,2) NOT NULL DEFAULT 0,

    revenue NUMERIC(14,2) NOT NULL DEFAULT 0,

    cogs NUMERIC(14,2) NOT NULL DEFAULT 0,

    operating_expenses NUMERIC(14,2) NOT NULL DEFAULT 0,

    cash_balance_eom NUMERIC(14,2) NOT NULL DEFAULT 0,

    receivables_outstanding NUMERIC(14,2),

    payables_outstanding NUMERIC(14,2),

    inventory_value NUMERIC(14,2),

    loan_outstanding NUMERIC(14,2),

    interest_expense NUMERIC(14,2),

    financing_type VARCHAR(15) NOT NULL DEFAULT 'none'
        CHECK (financing_type IN ('none', 'conventional', 'islamic')),

    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    UNIQUE(user_id, month)
);

CREATE TABLE uploaded_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES business_profiles(user_id),

    file_url TEXT NOT NULL,

    upload_timestamp TIMESTAMP NOT NULL DEFAULT now(),

    document_type_hint VARCHAR(20) DEFAULT 'unknown',

    processing_status VARCHAR(15) NOT NULL DEFAULT 'pending'
        CHECK (
            processing_status IN (
                'pending',
                'processing',
                'extracted',
                'needs_review',
                'confirmed',
                'failed'
            )
        ),

    extracted_data JSONB,

    confirmed_data JSONB,

    linked_month CHAR(7)
);

CREATE TABLE score_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES business_profiles(user_id),

    month CHAR(7) NOT NULL,

    composite_score NUMERIC(5,2) NOT NULL,

    band VARCHAR(20) NOT NULL,

    component_scores JSONB NOT NULL,

    weakest_component VARCHAR(20) NOT NULL,

    data_completeness NUMERIC(3,2) NOT NULL,

    computed_at TIMESTAMP NOT NULL DEFAULT now(),

    UNIQUE(user_id, month)
);