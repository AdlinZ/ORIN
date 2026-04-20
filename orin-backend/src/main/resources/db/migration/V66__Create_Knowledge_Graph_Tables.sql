CREATE TABLE IF NOT EXISTS knowledge_graphs (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    knowledge_base_id VARCHAR(100),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    build_status VARCHAR(50) DEFAULT 'PENDING',
    entity_count INT DEFAULT 0,
    relation_count INT DEFAULT 0,
    last_build_at DATETIME,
    last_success_build_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_graph_name (name),
    INDEX idx_graph_status (build_status),
    INDEX idx_graph_kb_id (knowledge_base_id)
);

CREATE TABLE IF NOT EXISTS graph_entities (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    graph_id VARCHAR(36) NOT NULL,
    name VARCHAR(500) NOT NULL,
    entity_type VARCHAR(100),
    description TEXT,
    source_document_id VARCHAR(100),
    source_chunk_id VARCHAR(100),
    properties TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_graph_id (graph_id),
    INDEX idx_entity_type (entity_type)
);

CREATE TABLE IF NOT EXISTS graph_relations (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    graph_id VARCHAR(36) NOT NULL,
    source_entity_id VARCHAR(36) NOT NULL,
    target_entity_id VARCHAR(36) NOT NULL,
    relation_type VARCHAR(100) NOT NULL,
    description TEXT,
    source_document_id VARCHAR(100),
    source_chunk_id VARCHAR(100),
    properties TEXT,
    weight DOUBLE DEFAULT 1.0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_graph_id (graph_id),
    INDEX idx_source_entity (source_entity_id),
    INDEX idx_target_entity (target_entity_id),
    INDEX idx_relation_type (relation_type)
);
