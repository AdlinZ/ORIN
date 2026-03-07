-- V13: Add desc_generation_model field to model_config
ALTER TABLE model_config ADD COLUMN IF NOT EXISTS desc_generation_model VARCHAR(255) DEFAULT NULL;
