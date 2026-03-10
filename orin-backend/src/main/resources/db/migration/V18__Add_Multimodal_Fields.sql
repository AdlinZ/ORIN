-- Add multimodal parsing fields to kb_documents
-- Multi-modal Knowledge Base Enhancement

ALTER TABLE kb_documents 
ADD COLUMN parse_status VARCHAR(20) DEFAULT 'PENDING',
ADD COLUMN parsed_text_path VARCHAR(500),
ADD COLUMN file_category VARCHAR(20) DEFAULT 'DOCUMENT';
