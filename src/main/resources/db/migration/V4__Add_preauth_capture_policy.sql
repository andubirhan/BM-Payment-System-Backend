ALTER TABLE pos_transactions
    ADD COLUMN IF NOT EXISTS auto_capture_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS manual_capture_occurred BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS preauth_expired_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS preauth_cancellation_key VARCHAR(120),
    ADD COLUMN IF NOT EXISTS preauth_release_reference VARCHAR(120),
    ADD COLUMN IF NOT EXISTS preauth_release_status VARCHAR(40);

CREATE TABLE IF NOT EXISTS pos_transaction_captures (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES pos_transactions(id) ON DELETE CASCADE,
    operation_type VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    amount BIGINT NOT NULL,
    cumulative_amount BIGINT NOT NULL,
    provider_reference VARCHAR(120),
    provider_status VARCHAR(40),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pos_transaction_captures_transaction
    ON pos_transaction_captures(transaction_id, created_at);
