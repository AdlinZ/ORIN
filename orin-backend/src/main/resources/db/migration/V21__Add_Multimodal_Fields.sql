-- Add multimodal parsing fields to kb_documents
-- Multi-modal Knowledge Base Enhancement
-- Note: parse_status, parsed_path already added in V17, only adding missing fields

-- Use stored procedure to safely add column if not exists
DELIMITER //

CREATE PROCEDURE add_column_if_not_exists()
BEGIN
    DECLARE column_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO column_exists
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'kb_documents'
    AND COLUMN_NAME = 'file_category';

    IF column_exists = 0 THEN
        ALTER TABLE kb_documents
        ADD COLUMN file_category VARCHAR(20) DEFAULT 'DOCUMENT' COMMENT '文件分类: DOCUMENT/IMAGE/AUDIO/VIDEO';
    END IF;
END //

DELIMITER ;

CALL add_column_if_not_exists();
DROP PROCEDURE add_column_if_not_exists;

-- Add index on parse_status for better query performance
CREATE INDEX idx_kb_documents_parse_status ON kb_documents(parse_status);
