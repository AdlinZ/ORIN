-- V16: Add embedding provider and api key fields to model_config table
-- ============================================

ALTER TABLE model_config
ADD COLUMN IF NOT EXISTS embedding_provider VARCHAR(50) AFTER embedding_model,
ADD COLUMN IF NOT EXISTS embedding_api_key_id BIGINT AFTER embedding_provider;
