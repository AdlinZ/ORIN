-- V95: Resource-level ACL — AgentChatSession owner.
-- Add nullable owner_user_id column + index for owner-scoped reads.
-- Existing rows stay NULL (admin/operator-visible only); no backfill is possible
-- because sessions never recorded a creator (ConversationLog.userId was always null).
SET @add_chat_owner_user_id = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE agent_chat_session ADD COLUMN owner_user_id BIGINT NULL AFTER agent_id',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_chat_session'
      AND column_name = 'owner_user_id'
);
PREPARE stmt FROM @add_chat_owner_user_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_chat_owner_user_id_index = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_agent_chat_session_owner_user_id ON agent_chat_session(owner_user_id)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_chat_session'
      AND index_name = 'idx_agent_chat_session_owner_user_id'
);
PREPARE stmt FROM @add_chat_owner_user_id_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;