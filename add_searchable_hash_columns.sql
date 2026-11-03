-- Add searchable hash column for customer_tin (encrypted field)
-- customer_name is no longer encrypted, so no hash needed

ALTER TABLE pos_transactions 
ADD COLUMN customer_tin_hash VARCHAR(255);

-- Create index for faster searching
CREATE INDEX idx_customer_tin_hash ON pos_transactions(customer_tin_hash);
