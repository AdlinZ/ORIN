package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.config.Neo4jConfig;
import com.adlin.orin.modules.knowledge.entity.GraphBuildState;
import com.adlin.orin.modules.knowledge.entity.GraphEntity;
import com.adlin.orin.modules.knowledge.entity.GraphRelation;
import com.adlin.orin.modules.knowledge.entity.KnowledgeGraph;
import com.adlin.orin.modules.knowledge.repository.GraphEntityRepository;
import com.adlin.orin.modules.knowledge.repository.GraphRelationRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeGraphRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 知识图谱抽取服务
 * 使用 LLM 从文档中抽取实体和关系，存储到 Neo4j 图数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphExtractionService {

    private final Neo4jConfig.Neo4jConnectionManager neo4jConnectionManager;
    private final KnowledgeGraphRepository knowledgeGraphRepository;
    private final GraphEntityRepository graphEntityRepository;
    private final GraphRelationRepository graphRelationRepository;
    private final com.adlin.orin.modules.knowledge.component.SiliconFlowEmbeddingAdapter embeddingAdapter;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_INITIAL_INTERVAL_MS = 1000;
    private static final double RETRY_MULTIPLIER = 2.0;
    private static final long RETRY_MAX_INTERVAL_MS = 30000;

    /**
     * 构建图谱 - 从文档中抽取实体和关系
     */
    @Transactional
    public void buildGraph(String graphId, String documentId, String textContent) {
        log.info("Starting graph build for graphId={}, documentId={}", graphId, documentId);

        Optional<KnowledgeGraph> graphOpt = knowledgeGraphRepository.findById(graphId);
        if (graphOpt.isEmpty()) {
            throw new IllegalArgumentException("图谱不存在: " + graphId);
        }

        KnowledgeGraph graph = graphOpt.get();
        graph.setBuildStatus(GraphBuildState.BUILDING);
        knowledgeGraphRepository.save(graph);

        try {
            // 1. 抽取实体
            List<ExtractedEntity> entities = extractEntities(textContent);
            log.info("Extracted {} entities from document {}", entities.size(), documentId);

            // 2. 抽取关系
            List<ExtractedRelation> relations = extractRelations(textContent, entities);
            log.info("Extracted {} relations from document {}", relations.size(), documentId);

            // 3. 存储到 Neo4j
            saveToNeo4j(graphId, documentId, entities, relations);

            // 4. 同步到 MySQL (作为备份/索引)
            saveEntitiesToMySQL(graphId, documentId, entities);
            saveRelationsToMySQL(graphId, documentId, relations);

            // 5. 更新图谱统计
            updateGraphStats(graphId);

            graph.setBuildStatus(GraphBuildState.SUCCESS);
            knowledgeGraphRepository.save(graph);

            log.info("Graph build completed successfully for graphId={}", graphId);

        } catch (Exception e) {
            log.error("Graph build failed for graphId={}", graphId, e);
            graph.setBuildStatus(GraphBuildState.FAILED);
            knowledgeGraphRepository.save(graph);
            throw new RuntimeException("图谱构建失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 LLM 抽取实体
     */
    private List<ExtractedEntity> extractEntities(String text) {
        // 构建提示词
        String prompt = buildEntityExtractionPrompt(text);

        try {
            // 调用 LLM 进行实体抽取
            String response = callLlm(prompt);
            return parseEntityResponse(response);
        } catch (Exception e) {
            log.error("Entity extraction failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 使用 LLM 抽取关系
     */
    private List<ExtractedRelation> extractRelations(String text, List<ExtractedEntity> entities) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        String prompt = buildRelationExtractionPrompt(text, entities);

        try {
            String response = callLlm(prompt);
            return parseRelationResponse(response);
        } catch (Exception e) {
            log.error("Relation extraction failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 存储到 Neo4j
     */
    private void saveToNeo4j(String graphId, String documentId,
                             List<ExtractedEntity> entities,
                             List<ExtractedRelation> relations) {
        String cypher;
        int saved = 0;

        // 使用事务批量保存
        try (var session = createNeo4jSession()) {
            // 保存实体节点
            for (ExtractedEntity entity : entities) {
                cypher = String.format(
                    "MERGE (e:Entity {name: $name, graphId: $graphId}) " +
                    "SET e.type = $type, e.description = $description, " +
                    "    e.documentId = $documentId, e.updatedAt = timestamp()",
                    entity.name, graphId, entity.type, entity.description, documentId
                );
                session.run(cypher, Map.of(
                    "name", entity.name,
                    "graphId", graphId,
                    "type", entity.type,
                    "description", entity.description != null ? entity.description : "",
                    "documentId", documentId
                ));
                saved++;
            }

            // 保存关系边
            Map<String, String> entityNameToId = new HashMap<>();
            for (ExtractedEntity entity : entities) {
                cypher = "MATCH (e:Entity {name: $name, graphId: $graphId}) RETURN id(e) as id";
                var result = session.run(cypher, Map.of("name", entity.name, "graphId", graphId));
                if (result.hasNext()) {
                    entityNameToId.put(entity.name, result.next().get("id").asString());
                }
            }

            for (ExtractedRelation relation : relations) {
                String sourceId = entityNameToId.get(relation.sourceEntity);
                String targetId = entityNameToId.get(relation.targetEntity);

                if (sourceId != null && targetId != null) {
                    cypher = String.format(
                        "MATCH (s:Entity {name: $sourceName, graphId: $graphId}) " +
                        "MATCH (t:Entity {name: $targetName, graphId: $graphId}) " +
                        "MERGE (s)-[r:%s]->(t) " +
                        "SET r.description = $description, r.weight = $weight",
                        relation.relationType
                    );
                    session.run(cypher, Map.of(
                        "sourceName", relation.sourceEntity,
                        "targetName", relation.targetEntity,
                        "graphId", graphId,
                        "description", relation.description != null ? relation.description : "",
                        "weight", relation.confidence
                    ));
                }
            }

            log.info("Saved {} entities and {} relations to Neo4j for graphId={}",
                    saved, relations.size(), graphId);
        } catch (Exception e) {
            log.warn("Neo4j storage failed, entities saved to MySQL only: {}", e.getMessage());
        }
    }

    /**
     * 保存实体到 MySQL
     */
    private void saveEntitiesToMySQL(String graphId, String documentId, List<ExtractedEntity> entities) {
        for (ExtractedEntity entity : entities) {
            GraphEntity graphEntity = GraphEntity.builder()
                    .graphId(graphId)
                    .name(entity.name)
                    .entityType(entity.type)
                    .description(entity.description)
                    .sourceDocumentId(documentId)
                    .properties(toJson(Map.of("confidence", entity.confidence)))
                    .build();
            graphEntityRepository.save(graphEntity);
        }
    }

    /**
     * 保存关系到 MySQL
     */
    private void saveRelationsToMySQL(String graphId, String documentId, List<ExtractedRelation> relations) {
        // 需要将实体名称映射到 ID
        List<GraphEntity> savedEntities = graphEntityRepository.findByGraphId(graphId);
        Map<String, String> nameToId = new HashMap<>();
        for (GraphEntity e : savedEntities) {
            nameToId.put(e.getName(), e.getId());
        }

        for (ExtractedRelation relation : relations) {
            String sourceId = nameToId.get(relation.sourceEntity);
            String targetId = nameToId.get(relation.targetEntity);

            if (sourceId != null && targetId != null) {
                GraphRelation graphRelation = GraphRelation.builder()
                        .graphId(graphId)
                        .sourceEntityId(sourceId)
                        .targetEntityId(targetId)
                        .relationType(relation.relationType)
                        .description(relation.description)
                        .sourceDocumentId(documentId)
                        .weight(relation.confidence)
                        .properties(toJson(Map.of("extractedFrom", "llm")))
                        .build();
                graphRelationRepository.save(graphRelation);
            }
        }
    }

    /**
     * 获取图谱可视化数据
     */
    public GraphVisualizationData getVisualizationData(String graphId, String documentIdFilter) {
        List<GraphEntity> entities;
        List<GraphRelation> relations;

        if (StringUtils.hasText(documentIdFilter)) {
            entities = graphEntityRepository.findByGraphId(graphId).stream()
                    .filter(e -> documentIdFilter.equals(e.getSourceDocumentId()))
                    .toList();
            Set<String> entityIds = new HashSet<>();
            for (GraphEntity e : entities) {
                entityIds.add(e.getId());
            }
            relations = graphRelationRepository.findByGraphId(graphId).stream()
                    .filter(r -> entityIds.contains(r.getSourceEntityId()) ||
                                 entityIds.contains(r.getTargetEntityId()))
                    .toList();
        } else {
            entities = graphEntityRepository.findByGraphId(graphId);
            relations = graphRelationRepository.findByGraphId(graphId);
        }

        // 转换为可视化格式
        List<GraphVisualizationData.Node> nodes = new ArrayList<>();
        Map<String, String> entityIdToNodeId = new HashMap<>();
        Set<String> entityTypes = new HashSet<>();

        for (int i = 0; i < entities.size(); i++) {
            GraphEntity entity = entities.get(i);
            String nodeId = "node_" + i;
            entityIdToNodeId.put(entity.getId(), nodeId);
            entityTypes.add(entity.getEntityType());

            nodes.add(new GraphVisualizationData.Node(
                    nodeId,
                    entity.getName(),
                    entity.getEntityType(),
                    entity.getDescription(),
                    entity.getSourceDocumentId()
            ));
        }

        List<GraphVisualizationData.Edge> edges = new ArrayList<>();
        for (GraphRelation relation : relations) {
            String sourceNodeId = entityIdToNodeId.get(relation.getSourceEntityId());
            String targetNodeId = entityIdToNodeId.get(relation.getTargetEntityId());
            if (sourceNodeId != null && targetNodeId != null) {
                edges.add(new GraphVisualizationData.Edge(
                        sourceNodeId,
                        targetNodeId,
                        relation.getRelationType(),
                        relation.getDescription(),
                        relation.getWeight()
                ));
            }
        }

        return new GraphVisualizationData(nodes, edges, new ArrayList<>(entityTypes));
    }

    /**
     * 搜索实体
     */
    public List<GraphEntity> searchEntities(String graphId, String keyword) {
        return graphEntityRepository.findByGraphIdAndNameContainingIgnoreCase(graphId, keyword);
    }

    /**
     * 获取实体详情
     */
    public Optional<GraphEntity> getEntityDetails(String entityId) {
        return graphEntityRepository.findById(entityId);
    }

    /**
     * 获取实体的关联关系
     */
    public List<GraphRelation> getEntityRelations(String entityId) {
        List<GraphRelation> relations = graphRelationRepository.findAll().stream()
                .filter(r -> entityId.equals(r.getSourceEntityId()) ||
                             entityId.equals(r.getTargetEntityId()))
                .toList();
        return relations;
    }

    private org.neo4j.driver.Session createNeo4jSession() {
        var config = neo4jConnectionManager.toConfig();
        return null; // Placeholder - actual implementation needs neo4j driver session
    }

    private String buildEntityExtractionPrompt(String text) {
        return String.format("""
            从以下文本中抽取实体，以JSON数组格式返回。每个实体包含：name(名称), type(类型，如人物/地点/机构/概念/产品等), description(简短描述), confidence(置信度0-1)。

            要求：
            1. 实体类型必须是标准化的类型
            2. 每个实体必须有明确的名称
            3. 忽略无意义的停用词实体
            4. 只返回JSON数组，不要其他内容

            文本内容：
            %s

            返回格式：[{"name":"实体名","type":"类型","description":"描述","confidence":0.9},...]
            """, text.substring(0, Math.min(text.length(), 4000)));
    }

    private String buildRelationExtractionPrompt(String text, List<ExtractedEntity> entities) {
        String entityList = entities.stream()
                .map(e -> String.format("%s(%s)", e.name, e.type))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        return String.format("""
            根据以下文本和已抽取的实体，抽取实体之间的关系。以JSON数组格式返回。每个关系包含：sourceEntity(源实体名称), targetEntity(目标实体名称), relationType(关系类型，如工作于/毕业于/位于/开发/拥有等), description(关系描述), confidence(置信度0-1)。

            已抽取的实体：%s

            要求：
            1. 关系类型必须是标准化的
            2. 只返回存在明确关系的一对实体
            3. 忽略弱关系或不确定的关系
            4. 只返回JSON数组，不要其他内容

            文本内容：
            %s

            返回格式：[{"sourceEntity":"实体A","targetEntity":"实体B","relationType":"关系类型","description":"描述","confidence":0.9},...]
            """, entityList, text.substring(0, Math.min(text.length(), 4000)));
    }

    private String callLlm(String prompt) {
        try {
            // 使用 SiliconFlowEmbeddingAdapter 的 chat 方法调用 LLM
            // 默认使用 Qwen/Qwen2.5-7B-Instruct 模型进行实体和关系抽取
            return embeddingAdapter.chat(prompt, null);
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
        }
    }

    private List<ExtractedEntity> parseEntityResponse(String response) {
        try {
            if (!StringUtils.hasText(response)) {
                return Collections.emptyList();
            }
            // 尝试解析 JSON 数组
            String jsonStr = extractJsonArray(response);
            return objectMapper.readValue(jsonStr, new TypeReference<List<ExtractedEntity>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse entity response: {}", response, e);
            return Collections.emptyList();
        }
    }

    private List<ExtractedRelation> parseRelationResponse(String response) {
        try {
            if (!StringUtils.hasText(response)) {
                return Collections.emptyList();
            }
            String jsonStr = extractJsonArray(response);
            return objectMapper.readValue(jsonStr, new TypeReference<List<ExtractedRelation>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse relation response: {}", response, e);
            return Collections.emptyList();
        }
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "[]";
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private void updateGraphStats(String graphId) {
        Optional<KnowledgeGraph> graphOpt = knowledgeGraphRepository.findById(graphId);
        if (graphOpt.isPresent()) {
            KnowledgeGraph graph = graphOpt.get();
            graph.setEntityCount((int) graphEntityRepository.countByGraphId(graphId));
            graph.setRelationCount((int) graphRelationRepository.countByGraphId(graphId));
            graph.setLastBuildAt(LocalDateTime.now());
            knowledgeGraphRepository.save(graph);
        }
    }

    // 内部类：抽取的实体
    @lombok.Data
    private static class ExtractedEntity {
        private String name;
        private String type;
        private String description;
        private double confidence;
    }

    // 内部类：抽取的关系
    @lombok.Data
    private static class ExtractedRelation {
        private String sourceEntity;
        private String targetEntity;
        private String relationType;
        private String description;
        private double confidence;
    }

    // 可视化数据类
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class GraphVisualizationData {
        private List<Node> nodes;
        private List<Edge> edges;
        private List<String> categories;

        @lombok.Data
        @lombok.AllArgsConstructor
        public static class Node {
            private String id;
            private String name;
            private String type;
            private String description;
            private String documentId;
        }

        @lombok.Data
        @lombok.AllArgsConstructor
        public static class Edge {
            private String source;
            private String target;
            private String relationType;
            private String description;
            private double weight;
        }
    }
}
