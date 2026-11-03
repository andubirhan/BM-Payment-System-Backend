-- Add befa_payment_id column to pos_transactions table
-- This column enables safe retries by allowing lookup of transactions by BEFA payment ID

ALTER TABLE pos_transactions
ADD COLUMN befa_payment_id VARCHAR(100);

-- Add index for faster lookups
CREATE UNIQUE INDEX IF NOT EXISTS uq_pos_transactions_befa_payment_id ON pos_transactions(befa_payment_id);
