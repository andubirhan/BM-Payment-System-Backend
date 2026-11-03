-- SQL Migration for Bank Payment Audit Logs Table
-- Per BRD Section 7: Audit Trail Database Schematic
-- Per BRD SEC-3.1: This table should be configured with append-only access privileges

CREATE TABLE IF NOT EXISTS bank_payment_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(100) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    request_payload TEXT NOT NULL,
    response_payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_bank_audit_transaction 
        FOREIGN KEY (transaction_id) 
        REFERENCES pos_transactions(transaction_id)
        ON DELETE CASCADE
);

-- Create index on transaction_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_bank_audit_transaction_id 
    ON bank_payment_audit_logs(transaction_id);

-- Create index on created_at for reconciliation queries
CREATE INDEX IF NOT EXISTS idx_bank_audit_created_at 
    ON bank_payment_audit_logs(created_at);

-- Create index on status for filtering
CREATE INDEX IF NOT EXISTS idx_bank_audit_status 
    ON bank_payment_audit_logs(status);

-- Security Note: Per BRD SEC-3.1, configure append-only privileges:
-- GRANT INSERT ON bank_payment_audit_logs TO pos_app_user;
-- REVOKE UPDATE, DELETE ON bank_payment_audit_logs FROM pos_app_user;
-- Any UPDATE or DELETE attempts should trigger security alerts to SOC team
