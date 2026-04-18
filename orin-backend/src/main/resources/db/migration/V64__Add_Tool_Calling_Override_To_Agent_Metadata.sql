-- Add tool_calling_override column to agent_metadata table
ALTER TABLE agent_metadata ADD COLUMN tool_calling_override BOOLEAN DEFAULT NULL;