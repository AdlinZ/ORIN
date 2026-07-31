-- Persist a safe, user-facing reason when document vectorization fails.
ALTER TABLE kb_documents
    ADD COLUMN vector_error TEXT NULL COMMENT '脱敏后的向量化失败原因' AFTER vector_status;
