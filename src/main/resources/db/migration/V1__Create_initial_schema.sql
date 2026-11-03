-- Initial schema migration for POS Tax VAT System
-- This migration creates all the core tables for the system

-- Create pos_transactions table
CREATE TABLE IF NOT EXISTS pos_transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(100) NOT NULL UNIQUE,
    parent_transaction_id VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    cash_register_id VARCHAR(36) NOT NULL,
    cashier_id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    befa_payment_id VARCHAR(100) NOT NULL UNIQUE,
    bank_transaction_id VARCHAR(100),
    gross_amount BIGINT NOT NULL,
    vat_amount BIGINT NOT NULL,
    service_fee_amount BIGINT NOT NULL,
    net_amount BIGINT NOT NULL,
    total_amount_paid BIGINT NOT NULL,
    customer_name VARCHAR(500),
    customer_tin VARCHAR(500),
    fiscal_signature VARCHAR(500),
    fiscal_reference VARCHAR(100),
    preauth_hold_amount BIGINT,
    preauth_expires_at TIMESTAMP,
    settled_at TIMESTAMP,
    authorized_at TIMESTAMP,
    captured_at TIMESTAMP,
    cancellation_reason VARCHAR(255),
    refundable_amount BIGINT,
    refunded_amount BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create pos_transaction_line_items table
CREATE TABLE IF NOT EXISTS pos_transaction_line_items (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES pos_transactions(id) ON DELETE CASCADE,
    item_code VARCHAR(100),
    item_name VARCHAR(255),
    unit_price BIGINT,
    quantity INTEGER,
    line_gross_amount BIGINT,
    line_vat_amount BIGINT,
    line_service_fee_amount BIGINT,
    line_net_amount BIGINT,
    discount_amount BIGINT,
    vat_exempt BOOLEAN,
    vat_category VARCHAR(50),
    vat_rate DECIMAL(10, 6)
);

-- Create pos_payment_tenders table
CREATE TABLE IF NOT EXISTS pos_payment_tenders (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES pos_transactions(id) ON DELETE CASCADE,
    payment_method VARCHAR(50),
    payment_provider VARCHAR(100),
    amount_tendered BIGINT,
    amount_applied BIGINT,
    cash_change BIGINT,
    card_last_four VARCHAR(4),
    card_brand VARCHAR(50),
    auth_code VARCHAR(100),
    rrn VARCHAR(100),
    status VARCHAR(50)
);

-- Create pos_audit_log table
CREATE TABLE IF NOT EXISTS pos_audit_log (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES pos_transactions(id) ON DELETE CASCADE,
    actor_id VARCHAR(100),
    actor_role VARCHAR(50),
    action VARCHAR(100),
    from_status VARCHAR(50),
    to_status VARCHAR(50),
    payload_details JSONB,
    ip_address VARCHAR(45),
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create vat_rates table
CREATE TABLE IF NOT EXISTS vat_rates (
    id BIGSERIAL PRIMARY KEY,
    category_code VARCHAR(50) NOT NULL UNIQUE,
    category_name VARCHAR(255),
    rate DECIMAL(10, 6) NOT NULL,
    rate_type VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT true,
    effective_from DATE,
    effective_to DATE
);

-- Create tax_transactions table
CREATE TABLE IF NOT EXISTS tax_transactions (
    id BIGSERIAL PRIMARY KEY,
    tax_transaction_id VARCHAR(100) NOT NULL UNIQUE,
    pos_transaction_id BIGINT REFERENCES pos_transactions(id),
    fiscal_signature VARCHAR(500),
    fiscal_reference VARCHAR(100),
    tin VARCHAR(100),
    submission_status VARCHAR(50),
    is_credit_note BOOLEAN DEFAULT false,
    original_transaction_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create bank_payment_audit_logs table
CREATE TABLE IF NOT EXISTS bank_payment_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES pos_transactions(id) ON DELETE CASCADE,
    action_type VARCHAR(100),
    status VARCHAR(50),
    request_payload TEXT,
    response_payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_pos_transactions_status ON pos_transactions(status);
CREATE INDEX IF NOT EXISTS idx_pos_transactions_created_at ON pos_transactions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pos_transactions_cashier_id ON pos_transactions(cashier_id);
CREATE INDEX IF NOT EXISTS idx_pos_transactions_cash_register_id ON pos_transactions(cash_register_id);
CREATE INDEX IF NOT EXISTS idx_pos_line_items_transaction_id ON pos_transaction_line_items(transaction_id);
CREATE INDEX IF NOT EXISTS idx_pos_payment_tenders_transaction_id ON pos_payment_tenders(transaction_id);
CREATE INDEX IF NOT EXISTS idx_pos_audit_log_transaction_id ON pos_audit_log(transaction_id);
CREATE INDEX IF NOT EXISTS idx_tax_transactions_pos_transaction_id ON tax_transactions(pos_transaction_id);
CREATE INDEX IF NOT EXISTS idx_bank_payment_audit_logs_transaction_id ON bank_payment_audit_logs(transaction_id);
