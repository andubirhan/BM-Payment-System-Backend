-- Add vat_rate column to pos_transaction_line_items table
ALTER TABLE pos_transaction_line_items ADD COLUMN IF NOT EXISTS vat_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0;
