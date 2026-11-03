-- Increase column sizes for encrypted data
-- Encrypted data is larger than plain text due to Base64 encoding and IV/GCM tag

ALTER TABLE pos_payment_tenders 
ALTER COLUMN auth_code TYPE varchar(500),
ALTER COLUMN rrn TYPE varchar(500);
