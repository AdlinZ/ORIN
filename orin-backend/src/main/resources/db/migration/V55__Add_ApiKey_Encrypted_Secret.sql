-- Add encrypted secret field to support admin-controlled key reveal
ALTER TABLE api_keys
    ADD COLUMN encrypted_secret VARCHAR(1024) NULL COMMENT 'Encrypted API key plaintext for admin reveal';

