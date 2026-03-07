-- V12: Add description generation model field to knowledge_bases
ALTER TABLE knowledge_bases ADD COLUMN IF NOT EXISTS description_model VARCHAR(255) DEFAULT NULL;
