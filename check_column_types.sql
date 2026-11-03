-- Check the actual column types in pos_transactions
-- Run this to see what types the columns currently have

SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'pos_transactions' 
AND column_name IN ('customer_name', 'customer_tin', 'fiscal_signature', 'fiscal_reference', 'cancellation_reason')
ORDER BY column_name;
