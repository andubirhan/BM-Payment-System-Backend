UPDATE pos_transactions
SET version = 0
WHERE version IS NULL;

ALTER TABLE pos_transactions
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;
