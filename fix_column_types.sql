-- Fix column types from bytea to text/varchar for string columns in pos_transactions
-- Run this in PostgreSQL to fix the schema issue
-- USING clause is required when converting from bytea to text

ALTER TABLE pos_transactions 
    ALTER COLUMN customer_name TYPE VARCHAR(255) USING customer_name::text,
    ALTER COLUMN customer_tin TYPE VARCHAR(255) USING customer_tin::text,
    ALTER COLUMN fiscal_signature TYPE VARCHAR(500) USING fiscal_signature::text,
    ALTER COLUMN fiscal_reference TYPE VARCHAR(100) USING fiscal_reference::text,
    ALTER COLUMN cancellation_reason TYPE VARCHAR(255) USING cancellation_reason::text;

-- Also fix any other string columns that might have bytea type
ALTER TABLE pos_transactions 
    ALTER COLUMN transaction_id TYPE VARCHAR(100) USING transaction_id::text,
    ALTER COLUMN parent_transaction_id TYPE VARCHAR(100) USING parent_transaction_id::text,
    ALTER COLUMN cash_register_id TYPE VARCHAR(36) USING cash_register_id::text,
    ALTER COLUMN cashier_id TYPE VARCHAR(36) USING cashier_id::text,
    ALTER COLUMN idempotency_key TYPE VARCHAR(100) USING idempotency_key::text;

-- Fix line items table as well
ALTER TABLE pos_transaction_line_items
    ALTER COLUMN item_code TYPE VARCHAR(100) USING item_code::text,
    ALTER COLUMN item_name TYPE VARCHAR(255) USING item_name::text,
    ALTER COLUMN vat_category TYPE VARCHAR(50) USING vat_category::text,
    ALTER COLUMN item_description TYPE VARCHAR(255) USING item_description::text;
