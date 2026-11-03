-- Fix pos_transaction_line_items table schema to match entity
-- First, drop the existing table and recreate it with correct schema

DROP TABLE IF EXISTS pos_transaction_line_items CASCADE;

CREATE TABLE pos_transaction_line_items (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    item_code VARCHAR(100) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    unit_price BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    line_gross_amount BIGINT NOT NULL,
    line_vat_amount BIGINT NOT NULL,
    line_service_fee_amount BIGINT NOT NULL,
    line_net_amount BIGINT NOT NULL,
    discount_amount BIGINT NOT NULL,
    vat_exempt BOOLEAN NOT NULL,
    vat_category VARCHAR(50),
    vat_rate NUMERIC(19, 4) NOT NULL DEFAULT 0.0,
    item_description VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_transaction FOREIGN KEY (transaction_id) REFERENCES pos_transactions(id)
);

CREATE INDEX idx_line_items_transaction ON pos_transaction_line_items(transaction_id);
