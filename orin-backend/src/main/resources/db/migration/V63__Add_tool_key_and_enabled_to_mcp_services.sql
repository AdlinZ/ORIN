ALTER TABLE mcp_services
    ADD COLUMN tool_key VARCHAR(100) NULL AFTER name,
    ADD COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER description;

CREATE INDEX idx_mcp_tool_key ON mcp_services(tool_key);
