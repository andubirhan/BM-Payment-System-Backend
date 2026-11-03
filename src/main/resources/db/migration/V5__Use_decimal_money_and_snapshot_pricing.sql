ALTER TABLE pos_transactions
    ALTER COLUMN gross_amount TYPE NUMERIC(19, 2) USING gross_amount::NUMERIC(19, 2),
    ALTER COLUMN vat_amount TYPE NUMERIC(19, 2) USING vat_amount::NUMERIC(19, 2),
    ALTER COLUMN service_fee_amount TYPE NUMERIC(19, 2) USING service_fee_amount::NUMERIC(19, 2),
    ALTER COLUMN net_amount TYPE NUMERIC(19, 2) USING net_amount::NUMERIC(19, 2),
    ALTER COLUMN total_amount_paid TYPE NUMERIC(19, 2) USING total_amount_paid::NUMERIC(19, 2),
    ALTER COLUMN preauth_hold_amount TYPE NUMERIC(19, 2) USING preauth_hold_amount::NUMERIC(19, 2),
    ALTER COLUMN auto_capture_amount TYPE NUMERIC(19, 2) USING auto_capture_amount::NUMERIC(19, 2),
    ALTER COLUMN refundable_amount TYPE NUMERIC(19, 2) USING refundable_amount::NUMERIC(19, 2),
    ALTER COLUMN refunded_amount TYPE NUMERIC(19, 2) USING refunded_amount::NUMERIC(19, 2),
    ADD COLUMN IF NOT EXISTS currency_code VARCHAR(3) NOT NULL DEFAULT 'ETB',
    ADD COLUMN IF NOT EXISTS prices_include_vat BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS customer_fee_applied BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS customer_fee_rate NUMERIC(10, 6) NOT NULL DEFAULT 0;

ALTER TABLE pos_transaction_line_items
    ALTER COLUMN unit_price TYPE NUMERIC(19, 2) USING unit_price::NUMERIC(19, 2),
    ALTER COLUMN line_gross_amount TYPE NUMERIC(19, 2) USING line_gross_amount::NUMERIC(19, 2),
    ALTER COLUMN line_vat_amount TYPE NUMERIC(19, 2) USING line_vat_amount::NUMERIC(19, 2),
    ALTER COLUMN line_service_fee_amount TYPE NUMERIC(19, 2) USING line_service_fee_amount::NUMERIC(19, 2),
    ALTER COLUMN line_net_amount TYPE NUMERIC(19, 2) USING line_net_amount::NUMERIC(19, 2),
    ALTER COLUMN discount_amount TYPE NUMERIC(19, 2) USING discount_amount::NUMERIC(19, 2);

ALTER TABLE pos_payment_tenders
    ALTER COLUMN amount_tendered TYPE NUMERIC(19, 2) USING amount_tendered::NUMERIC(19, 2),
    ALTER COLUMN amount_applied TYPE NUMERIC(19, 2) USING amount_applied::NUMERIC(19, 2),
    ALTER COLUMN cash_change TYPE NUMERIC(19, 2) USING cash_change::NUMERIC(19, 2);

ALTER TABLE pos_transaction_captures
    ALTER COLUMN amount TYPE NUMERIC(19, 2) USING amount::NUMERIC(19, 2),
    ALTER COLUMN cumulative_amount TYPE NUMERIC(19, 2) USING cumulative_amount::NUMERIC(19, 2);

ALTER TABLE tax_transactions
    ADD COLUMN IF NOT EXISTS gross_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS vat_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS service_fee_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS net_amount NUMERIC(19, 2) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS pos_configuration (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO pos_configuration (config_key, config_value)
VALUES ('currency_code', 'ETB')
ON CONFLICT (config_key) DO NOTHING;
