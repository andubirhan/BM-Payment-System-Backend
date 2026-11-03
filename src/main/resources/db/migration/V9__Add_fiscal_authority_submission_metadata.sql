ALTER TABLE tax_transactions
    ALTER COLUMN fiscal_signature DROP NOT NULL,
    ALTER COLUMN fiscal_reference DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS currency_code VARCHAR(3) NOT NULL DEFAULT 'ETB',
    ADD COLUMN IF NOT EXISTS submission_mode VARCHAR(20) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN IF NOT EXISTS authority_transaction_id VARCHAR(100);

ALTER TABLE pos_transactions
    ADD COLUMN IF NOT EXISTS fiscal_submission_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS fiscal_submission_mode VARCHAR(20),
    ADD COLUMN IF NOT EXISTS fiscal_authority_response_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS fiscal_authority_response_message VARCHAR(255),
    ADD COLUMN IF NOT EXISTS fiscal_submitted_to_authority_at TIMESTAMP;
