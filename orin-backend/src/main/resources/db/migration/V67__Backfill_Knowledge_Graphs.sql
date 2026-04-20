-- 为已有知识库自动补充知识图谱记录（如果还没有对应图谱）
INSERT INTO knowledge_graphs (id, knowledge_base_id, name, description, build_status, entity_count, relation_count, created_at, updated_at)
SELECT
    UUID(),
    kb.id,
    kb.name,
    kb.description,
    'PENDING',
    0,
    0,
    NOW(),
    NOW()
FROM knowledge_bases kb
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_graphs kg WHERE kg.knowledge_base_id = kb.id
);
