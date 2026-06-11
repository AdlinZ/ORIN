-- V92: Resource-level ACL step 1 (KnowledgeBase)
-- Add nullable owner_user_id column + index for owner filtering.
-- Existing rows stay NULL and are treated as "system-level" (visible to admin/operator, hidden from regular user).
SET @add_kb_owner_user_id = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE knowledge_bases ADD COLUMN owner_user_id BIGINT NULL AFTER source_agent_id',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'knowledge_bases'
      AND column_name = 'owner_user_id'
);
PREPARE stmt FROM @add_kb_owner_user_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_kb_owner_user_id_index = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_knowledge_bases_owner_user_id ON knowledge_bases(owner_user_id)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'knowledge_bases'
      AND index_name = 'idx_knowledge_bases_owner_user_id'
);
PREPARE stmt FROM @add_kb_owner_user_id_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
