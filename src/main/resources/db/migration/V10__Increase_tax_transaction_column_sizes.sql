-- Increase column sizes for tax_transactions fiscal authority fields
-- Fiscal authority response codes and submission modes can be longer than 20 characters

ALTER TABLE tax_transactions 
ALTER COLUMN authority_response_code TYPE varchar(50),
ALTER COLUMN submission_mode TYPE varchar(50);
