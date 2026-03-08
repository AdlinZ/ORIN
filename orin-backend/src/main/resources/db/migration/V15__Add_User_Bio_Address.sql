-- V15: Add bio, address, phone fields to sys_user table
-- ============================================

ALTER TABLE sys_user
ADD COLUMN IF NOT EXISTS bio VARCHAR(500) AFTER avatar,
ADD COLUMN IF NOT EXISTS address VARCHAR(255) AFTER bio,
ADD COLUMN IF NOT EXISTS phone VARCHAR(50) AFTER address;
