ALTER TABLE pos_transactions
    ADD COLUMN IF NOT EXISTS withholding_vat_applied BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS withholding_vat_rate NUMERIC(10, 6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS withholding_vat_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS withholding_certificate_number VARCHAR(120),
    ADD COLUMN IF NOT EXISTS withholding_certificate_date DATE;

INSERT INTO pos_configuration (config_key, config_value)
VALUES ('sale_withholding_vat_rate', '0.000000'),
       ('purchase_withholding_vat_rate', '0.000000')
ON CONFLICT (config_key) DO NOTHING;
