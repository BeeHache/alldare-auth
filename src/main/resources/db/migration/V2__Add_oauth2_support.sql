-- V2: Add OAuth2 support
-- Make password_hash nullable for users authenticated via external providers
ALTER TABLE accounts ALTER COLUMN password_hash DROP NOT NULL;

-- Add provider and provider_id columns
ALTER TABLE accounts ADD COLUMN provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE accounts ADD COLUMN provider_id VARCHAR(255);

-- Ensure combination of provider and provider_id is unique
-- and also unique for LOCAL provider login
CREATE UNIQUE INDEX idx_provider_provider_id ON accounts(provider, provider_id) WHERE provider <> 'LOCAL';
