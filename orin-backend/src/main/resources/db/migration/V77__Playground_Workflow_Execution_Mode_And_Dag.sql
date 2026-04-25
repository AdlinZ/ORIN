ALTER TABLE playground_workflows
    ADD COLUMN execution_mode VARCHAR(32) NULL AFTER router_prompt;

ALTER TABLE playground_workflows
    ADD COLUMN dag_subtasks_json TEXT NULL AFTER execution_mode;

UPDATE playground_workflows
SET execution_mode = 'DYNAMIC'
WHERE execution_mode IS NULL OR execution_mode = '';
