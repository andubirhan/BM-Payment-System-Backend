CREATE INDEX IF NOT EXISTS idx_pos_transactions_status_created_at ON pos_transactions(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pos_transactions_type_status_created_at ON pos_transactions(transaction_type, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pos_transactions_cashier_created_at ON pos_transactions(cashier_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pos_transactions_register_created_at ON pos_transactions(cash_register_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pos_transactions_parent_transaction_id ON pos_transactions(parent_transaction_id);