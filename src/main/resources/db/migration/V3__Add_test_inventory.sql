CREATE TABLE IF NOT EXISTS pos_items (
    id BIGSERIAL PRIMARY KEY,
    item_code VARCHAR(100) NOT NULL UNIQUE,
    item_name VARCHAR(255) NOT NULL,
    quantity_on_hand BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE pos_transaction_line_items
    ADD COLUMN IF NOT EXISTS item_id BIGINT REFERENCES pos_items(id),
    ADD COLUMN IF NOT EXISTS inventory_adjusted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_pos_items_item_name ON pos_items(item_name);
