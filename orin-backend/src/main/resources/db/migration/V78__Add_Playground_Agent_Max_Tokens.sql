ALTER TABLE playground_workflows
    ADD COLUMN agent_max_tokens INT NULL AFTER dag_subtasks_json;

UPDATE playground_workflows
SET agent_max_tokens = 2400
WHERE agent_max_tokens IS NULL;
