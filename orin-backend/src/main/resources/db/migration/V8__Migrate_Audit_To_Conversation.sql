-- Migrate existing chat logs from audit_logs to conversation_logs
-- This script safely copies logs that have a conversation_id

INSERT INTO conversation_logs (id, conversation_id, agent_id, user_id, model, query, response, prompt_tokens, completion_tokens, total_tokens, response_time, success, error_message, created_at)
SELECT 
    id, 
    conversation_id, 
    provider_id, 
    user_id, 
    model, 
    request_params, 
    response_content, 
    prompt_tokens, 
    completion_tokens, 
    total_tokens, 
    response_time, 
    success, 
    error_message, 
    created_at
FROM audit_logs
WHERE conversation_id IS NOT NULL 
  AND id NOT IN (SELECT id FROM conversation_logs);
