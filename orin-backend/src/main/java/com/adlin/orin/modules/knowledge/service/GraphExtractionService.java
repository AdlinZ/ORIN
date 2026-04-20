package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.config.Neo4jConfig;
import com.adlin.orin.modules.knowledge.entity.GraphBuildState;
import com.adlin.orin.modules.knowledge.entity.GraphEntity;
import com.adlin.orin.modules.knowledge.entity.GraphRelation;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.KnowledgeGraph;
import com.adlin.orin.modules.knowledge.repository.GraphEntityRepository;
import com.adlin.orin.modules.knowledge.repository.GraphRelationRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeGraphRepository;
import com.adlin.orin.modules.system.repository.SystemConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.SessionConfig;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 知识图谱抽取服务
 * 使用 LLM 从文档中抽取实体和关系，优先存储到 Neo4j（外部图数据库），并同步到 MySQL 作为索引兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphExtractionService {

    private final Neo4jConfig.Neo4jConnectionManager neo4jConnectionManager;
    private final KnowledgeGraphRepository knowledgeGraphRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final GraphEntityRepository graphEntityRepository;
    private final GraphRelationRepository graphRelationRepository;
    private final DocumentManageService documentManageService;
    private final com.adlin.orin.modules.knowledge.component.SiliconFlowEmbeddingAdapter embeddingAdapter;
    private final ObjectMapper objectMapper;
    private final SystemConfigRepository systemConfigRepository;

    private volatile Driver neo4jDriver;

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

            // 3. 存储到 Neo4j（主）
            saveToNeo4j(graphId, documentId, entities, relations);

            // 4. 同步到 MySQL (兜底/索引)
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
     * 异步构建图谱（自动闭环）：
     * 1) 清理旧图数据
     * 2) 遍历文档抽取实体/关系
     * 3) 写入 Neo4j + MySQL
     * 4) 更新图谱状态
     */
    @Async("taskExecutor")
    public void buildGraphAsync(String graphId) {
        Optional<KnowledgeGraph> graphOpt = knowledgeGraphRepository.findById(graphId);
        if (graphOpt.isEmpty()) {
            log.warn("Graph build async aborted, graph not found: {}", graphId);
            return;
        }

        KnowledgeGraph graph = graphOpt.get();
        graph.setBuildStatus(GraphBuildState.BUILDING);
        knowledgeGraphRepository.save(graph);

        int processed = 0;
        int skipped = 0;
        int failed = 0;
        int totalEntities = 0;

        try {
            clearGraphData(graphId);

            // Only load documents belonging to this graph's knowledge base (if linked),
            // otherwise fall back to all documents.
            List<KnowledgeDocument> docs;
            if (StringUtils.hasText(graph.getKnowledgeBaseId())) {
                docs = knowledgeDocumentRepository.findByKnowledgeBaseIdOrderByUploadTimeDesc(graph.getKnowledgeBaseId());
                log.info("Graph build async loading docs for kbId={}, count={}", graph.getKnowledgeBaseId(), docs.size());
            } else {
                docs = knowledgeDocumentRepository.findAll();
                log.info("Graph build async loading all docs (no kbId), count={}", docs.size());
            }

            if (docs.isEmpty()) {
                graph.setBuildStatus(GraphBuildState.FAILED);
                graph.setErrorMessage("知识库中没有文档，请先上传文档后再构建图谱");
                knowledgeGraphRepository.save(graph);
                log.warn("Graph build async finished with no documents. graphId={}", graphId);
                return;
            }

            for (KnowledgeDocument doc : docs) {
                if (Boolean.TRUE.equals(doc.getDeletedFlag())) {
                    skipped++;
                    continue;
                }

                String text = loadDocumentText(doc);
                if (!StringUtils.hasText(text) || text.trim().length() < 20) {
                    skipped++;
                    continue;
                }

                try {
                    List<ExtractedEntity> entities = extractEntities(text);
                    if (entities.isEmpty()) {
                        log.warn("Graph build async: no entities extracted from docId={}, skipping", doc.getId());
                        skipped++;
                        continue;
                    }
                    List<ExtractedRelation> relations = extractRelations(text, entities);
                    saveToNeo4j(graphId, doc.getId(), entities, relations);
                    saveEntitiesToMySQL(graphId, doc.getId(), entities);
                    saveRelationsToMySQL(graphId, doc.getId(), relations);
                    totalEntities += entities.size();
                    processed++;
                } catch (Exception e) {
                    failed++;
                    log.warn("Graph build async doc failed: graphId={}, docId={}, err={}",
                            graphId, doc.getId(), e.getMessage());
                }
            }

            updateGraphStats(graphId);
            boolean success = processed > 0 && totalEntities > 0;
            graph.setBuildStatus(success ? GraphBuildState.SUCCESS : GraphBuildState.FAILED);
            if (success) {
                graph.setErrorMessage(null);
                graph.setLastSuccessBuildAt(LocalDateTime.now());
            } else {
                String reason = failed > 0
                        ? String.format("所有文档处理失败（共%d个文档，%d个失败，%d个跳过），请检查 LLM API 配置或文档内容", docs.size(), failed, skipped)
                        : String.format("未抽取到任何实体（共%d个文档，%d个跳过），请检查文档是否有效或 LLM 返回格式", docs.size(), skipped);
                graph.setErrorMessage(reason);
            }
            knowledgeGraphRepository.save(graph);
            log.info("Graph build async completed. graphId={}, processed={}, skipped={}, failed={}, totalEntities={}",
                    graphId, processed, skipped, failed, totalEntities);
        } catch (Exception e) {
            log.error("Graph build async failed. graphId={}", graphId, e);
            graph.setBuildStatus(GraphBuildState.FAILED);
            graph.setErrorMessage("构建时发生异常：" + e.getMessage());
            knowledgeGraphRepository.save(graph);
        }
    }

    /**
     * 使用 LLM 抽取实体
     */
    private List<ExtractedEntity> extractEntities(String text) {
        String prompt = buildEntityExtractionPrompt(text);

        try {
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
        int saved = 0;

        try (var session = createNeo4jSession()) {
            for (ExtractedEntity entity : entities) {
                String cypher =
                    "MERGE (e:Entity {name: $name, graphId: $graphId}) " +
                    "SET e.nodeId = coalesce(e.nodeId, randomUUID()), " +
                    "    e.type = $type, e.entity_type = $type, " +
                    "    e.description = $description, e.documentId = $documentId, " +
                    "    e.entity_id = $entityId, e.source_id = $sourceId, " +
                    "    e.file_path = $filePath, e.truncate = $truncate, " +
                    "    e.source_chunk_id = $sourceChunkId, " +
                    "    e.tags = $tags, " +
                    "    e.created_at = coalesce(e.created_at, timestamp()), " +
                    "    e.updated_at = timestamp(), e.updatedAt = timestamp()";
                Map<String, Object> entityParams = new HashMap<>();
                entityParams.put("name", entity.name);
                entityParams.put("graphId", graphId);
                entityParams.put("type", safeText(entity.type, "unknown"));
                entityParams.put("description", safeText(entity.description, ""));
                entityParams.put("documentId", safeText(documentId, ""));
                entityParams.put("entityId", safeText(entity.name, ""));
                entityParams.put("sourceId", safeText(documentId, ""));
                entityParams.put("filePath", safeText(documentId, ""));
                entityParams.put("truncate", "");
                entityParams.put("sourceChunkId", "");
                entityParams.put("tags", List.of(safeText(entity.type, "unknown")));
                session.run(cypher, entityParams);
                saved++;
            }

            Map<String, String> entityNameToId = new HashMap<>();
            for (ExtractedEntity entity : entities) {
                String cypher = "MATCH (e:Entity {name: $name, graphId: $graphId}) RETURN id(e) as id";
                var result = session.run(cypher, Map.of("name", entity.name, "graphId", graphId));
                if (result.hasNext()) {
                    entityNameToId.put(entity.name, result.next().get("id").asString());
                }
            }

            for (ExtractedRelation relation : relations) {
                String sourceId = entityNameToId.get(relation.sourceEntity);
                String targetId = entityNameToId.get(relation.targetEntity);

                if (sourceId != null && targetId != null) {
                    String relationType = normalizeRelationType(relation.relationType);
                    String cypher = String.format(
                        "MATCH (s:Entity {name: $sourceName, graphId: $graphId}) " +
                        "MATCH (t:Entity {name: $targetName, graphId: $graphId}) " +
                        "MERGE (s)-[r:%s]->(t) " +
                        "SET r.description = $description, r.weight = $weight, " +
                        "    r.relation_type = $relationType, r.source_id = $sourceId, " +
                        "    r.documentId = $documentId, r.updated_at = timestamp(), " +
                        "    r.created_at = coalesce(r.created_at, timestamp())",
                        relationType
                    );
                    session.run(cypher, Map.of(
                        "sourceName", relation.sourceEntity,
                        "targetName", relation.targetEntity,
                        "graphId", graphId,
                        "description", safeText(relation.description, ""),
                        "weight", relation.confidence,
                        "relationType", safeText(relation.relationType, "RELATED_TO"),
                        "sourceId", safeText(documentId, ""),
                        "documentId", safeText(documentId, "")
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
     * 获取图谱可视化数据（优先 Neo4j，失败后回退 MySQL）
     */
    public GraphVisualizationData getVisualizationData(String graphId, String documentIdFilter) {
        GraphVisualizationData neo4jData = tryLoadVisualizationFromNeo4j(graphId, documentIdFilter);
        if (!neo4jData.getNodes().isEmpty()) {
            return neo4jData;
        }

        log.info("Falling back to MySQL graph visualization for graphId={}", graphId);
        return loadVisualizationFromMySql(graphId, documentIdFilter);
    }

    private GraphVisualizationData tryLoadVisualizationFromNeo4j(String graphId, String documentIdFilter) {
        try (var session = createNeo4jSession()) {
            String nodeCypher =
                    "MATCH (e:Entity {graphId: $graphId}) " +
                    "WHERE $documentId = '' OR e.documentId = $documentId " +
                    "RETURN coalesce(e.nodeId, toString(id(e))) AS nodeId, " +
                    "       coalesce(e.name, '') AS name, " +
                    "       coalesce(e.type, e.entity_type, 'unknown') AS type, " +
                    "       coalesce(e.description, '') AS description, " +
                    "       coalesce(e.documentId, '') AS documentId, " +
                    "       coalesce(e.entity_id, e.name, '') AS entityId, " +
                    "       coalesce(e.source_id, e.documentId, '') AS sourceId, " +
                    "       coalesce(e.file_path, '') AS filePath, " +
                    "       coalesce(e.truncate, '') AS truncate, " +
                    "       coalesce(e.created_at, 0) AS createdAt, " +
                    "       coalesce(e.source_chunk_id, '') AS sourceChunkId, " +
                    "       coalesce(e.tags, []) AS tags";
            var nodeResult = session.run(nodeCypher, Map.of(
                    "graphId", graphId,
                    "documentId", safeText(documentIdFilter, "")
            ));

            List<GraphVisualizationData.Node> nodes = new ArrayList<>();
            Set<String> categories = new LinkedHashSet<>();
            Set<String> nodeIds = new HashSet<>();
            while (nodeResult.hasNext()) {
                Record record = nodeResult.next();
                String nodeId = record.get("nodeId").asString();
                String nodeType = safeText(record.get("type").asString(), "unknown");
                categories.add(nodeType);
                nodeIds.add(nodeId);
                nodes.add(new GraphVisualizationData.Node(
                        nodeId,
                        record.get("name").asString(),
                        nodeType,
                        record.get("description").asString(),
                        record.get("documentId").asString(),
                        record.get("entityId").asString(),
                        record.get("sourceId").asString(),
                        record.get("filePath").asString(),
                        record.get("truncate").asString(),
                        record.get("createdAt").asLong(0L),
                        record.get("sourceChunkId").asString(),
                        toStringList(record.get("tags").asList())
                ));
            }

            if (nodes.isEmpty()) {
                return new GraphVisualizationData(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
            }

            String edgeCypher =
                    "MATCH (s:Entity {graphId: $graphId})-[r]->(t:Entity {graphId: $graphId}) " +
                    "WHERE ($documentId = '' OR s.documentId = $documentId OR t.documentId = $documentId OR r.documentId = $documentId) " +
                    "  AND coalesce(s.nodeId, toString(id(s))) IN $nodeIds " +
                    "  AND coalesce(t.nodeId, toString(id(t))) IN $nodeIds " +
                    "RETURN coalesce(s.nodeId, toString(id(s))) AS source, " +
                    "       coalesce(t.nodeId, toString(id(t))) AS target, " +
                    "       coalesce(r.relation_type, type(r), 'RELATED_TO') AS relationType, " +
                    "       coalesce(r.description, '') AS description, " +
                    "       coalesce(r.weight, 1.0) AS weight";
            var edgeResult = session.run(edgeCypher, Map.of(
                    "graphId", graphId,
                    "documentId", safeText(documentIdFilter, ""),
                    "nodeIds", nodeIds
            ));

            List<GraphVisualizationData.Edge> edges = new ArrayList<>();
            while (edgeResult.hasNext()) {
                Record record = edgeResult.next();
                edges.add(new GraphVisualizationData.Edge(
                        record.get("source").asString(),
                        record.get("target").asString(),
                        record.get("relationType").asString(),
                        record.get("description").asString(),
                        record.get("weight").asDouble(1.0d)
                ));
            }

            return new GraphVisualizationData(nodes, edges, new ArrayList<>(categories));
        } catch (Exception e) {
            log.warn("Load visualization from Neo4j failed, fallback to MySQL. graphId={}, error={}",
                    graphId, e.getMessage());
            return new GraphVisualizationData(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
    }

    private GraphVisualizationData loadVisualizationFromMySql(String graphId, String documentIdFilter) {
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
                    .filter(r -> entityIds.contains(r.getSourceEntityId()) || entityIds.contains(r.getTargetEntityId()))
                    .toList();
        } else {
            entities = graphEntityRepository.findByGraphId(graphId);
            relations = graphRelationRepository.findByGraphId(graphId);
        }

        List<GraphVisualizationData.Node> nodes = new ArrayList<>();
        Map<String, String> entityIdToNodeId = new HashMap<>();
        Set<String> entityTypes = new HashSet<>();

        for (int i = 0; i < entities.size(); i++) {
            GraphEntity entity = entities.get(i);
            String nodeId = "node_" + i;
            entityIdToNodeId.put(entity.getId(), nodeId);
            String entityType = safeText(entity.getEntityType(), "unknown");
            entityTypes.add(entityType);
            Map<String, Object> props = parseProperties(entity.getProperties());

            nodes.add(new GraphVisualizationData.Node(
                    nodeId,
                    entity.getName(),
                    entityType,
                    entity.getDescription(),
                    safeText(entity.getSourceDocumentId(), ""),
                    safeText(entity.getName(), ""),
                    safeText(entity.getSourceDocumentId(), ""),
                    safeText(asString(props.get("file_path")), safeText(entity.getSourceDocumentId(), "")),
                    safeText(asString(props.get("truncate")), ""),
                    toEpochSeconds(entity.getCreatedAt()),
                    safeText(entity.getSourceChunkId(), ""),
                    Collections.singletonList(entityType)
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
                        relation.getWeight() == null ? 1.0d : relation.getWeight()
                ));
            }
        }

        return new GraphVisualizationData(nodes, edges, new ArrayList<>(entityTypes));
    }

    /**
     * 搜索实体（优先 Neo4j）
     */
    public List<GraphEntity> searchEntities(String graphId, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        List<GraphEntity> neo4jResult = trySearchEntitiesFromNeo4j(graphId, keyword);
        if (!neo4jResult.isEmpty()) {
            return neo4jResult;
        }
        return graphEntityRepository.findByGraphIdAndNameContainingIgnoreCase(graphId, keyword);
    }

    /**
     * 获取实体详情（优先 Neo4j）
     */
    public Optional<GraphEntity> getEntityDetails(String graphId, String entityId) {
        Optional<GraphEntity> fromNeo4j = tryGetEntityDetailsFromNeo4j(graphId, entityId);
        if (fromNeo4j.isPresent()) {
            return fromNeo4j;
        }
        return graphEntityRepository.findById(entityId);
    }

    /**
     * 获取实体关联关系（优先 Neo4j）
     */
    public List<GraphRelation> getEntityRelations(String graphId, String entityId) {
        List<GraphRelation> fromNeo4j = tryGetEntityRelationsFromNeo4j(graphId, entityId);
        if (!fromNeo4j.isEmpty()) {
            return fromNeo4j;
        }
        return graphRelationRepository.findAll().stream()
                .filter(r -> entityId.equals(r.getSourceEntityId()) || entityId.equals(r.getTargetEntityId()))
                .toList();
    }

    private org.neo4j.driver.Session createNeo4jSession() {
        Driver driver = getOrCreateNeo4jDriver();
        return driver.session(SessionConfig.forDatabase(neo4jConnectionManager.getDatabase()));
    }

    private void clearGraphData(String graphId) {
        graphRelationRepository.deleteByGraphId(graphId);
        graphEntityRepository.deleteByGraphId(graphId);
        try (var session = createNeo4jSession()) {
            session.run("MATCH (e:Entity {graphId: $graphId}) DETACH DELETE e", Map.of("graphId", graphId));
        } catch (Exception e) {
            log.warn("Clear Neo4j graph data failed. graphId={}, err={}", graphId, e.getMessage());
        }
    }

    private String loadDocumentText(KnowledgeDocument doc) {
        try {
            Map<String, Object> content = documentManageService.getDocumentContent(doc.getId());
            Object textObj = content.get("text");
            String text = textObj == null ? "" : String.valueOf(textObj);
            if (StringUtils.hasText(text)) {
                return text;
            }
        } catch (Exception e) {
            log.warn("Load document text failed from document service: docId={}, err={}", doc.getId(), e.getMessage());
        }
        return safeText(doc.getContentPreview(), "");
    }

    private String getDbConfigValue(String key, String defaultValue) {
        return systemConfigRepository.findByConfigKey(key)
                .map(e -> e.getConfigValue())
                .filter(StringUtils::hasText)
                .orElse(defaultValue);
    }

    private Driver getOrCreateNeo4jDriver() {
        if (neo4jDriver != null) {
            return neo4jDriver;
        }
        synchronized (this) {
            if (neo4jDriver == null) {
                String uri = getDbConfigValue("neo4j.uri", neo4jConnectionManager.getUri());
                String host = getDbConfigValue("neo4j.host", neo4jConnectionManager.getHost());
                int port;
                try { port = Integer.parseInt(getDbConfigValue("neo4j.port", String.valueOf(neo4jConnectionManager.getPort()))); }
                catch (NumberFormatException e) { port = neo4jConnectionManager.getPort(); }
                String username = getDbConfigValue("neo4j.username", neo4jConnectionManager.getUsername());
                String password = getDbConfigValue("neo4j.password", safeText(neo4jConnectionManager.getPassword(), ""));
                int maxPool;
                try { maxPool = Integer.parseInt(getDbConfigValue("neo4j.maxConnectionPoolSize", String.valueOf(neo4jConnectionManager.getMaxConnectionPoolSize()))); }
                catch (NumberFormatException e) { maxPool = neo4jConnectionManager.getMaxConnectionPoolSize(); }
                long acquisitionMs;
                try { acquisitionMs = Long.parseLong(getDbConfigValue("neo4j.connectionAcquisitionTimeoutMs", String.valueOf(neo4jConnectionManager.getConnectionAcquisitionTimeoutMs()))); }
                catch (NumberFormatException e) { acquisitionMs = neo4jConnectionManager.getConnectionAcquisitionTimeoutMs(); }

                String boltUri = StringUtils.hasText(uri) ? uri : String.format("bolt://%s:%d", host, port);
                Config config = Config.builder()
                        .withMaxConnectionPoolSize(maxPool)
                        .withConnectionAcquisitionTimeout(acquisitionMs, TimeUnit.MILLISECONDS)
                        .build();
                neo4jDriver = GraphDatabase.driver(boltUri, AuthTokens.basic(username, safeText(password, "")), config);
                log.info("Neo4j driver initialized for {}", boltUri);
            }
        }
        return neo4jDriver;
    }

    public synchronized void resetNeo4jDriver() {
        if (neo4jDriver != null) {
            try { neo4jDriver.close(); } catch (Exception e) { log.warn("Error closing Neo4j driver: {}", e.getMessage()); }
            neo4jDriver = null;
            log.info("Neo4j driver reset, will reconnect on next use");
        }
    }

    @PreDestroy
    public void closeNeo4jDriver() {
        if (neo4jDriver != null) {
            neo4jDriver.close();
            neo4jDriver = null;
            log.info("Neo4j driver closed");
        }
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

    private Optional<GraphEntity> tryGetEntityDetailsFromNeo4j(String graphId, String entityId) {
        try (var session = createNeo4jSession()) {
            String cypher =
                    "MATCH (e:Entity {graphId: $graphId}) " +
                    "WHERE coalesce(e.nodeId, toString(id(e))) = $entityId OR e.entity_id = $entityId OR e.name = $entityId " +
                    "RETURN coalesce(e.nodeId, toString(id(e))) AS id, " +
                    "       coalesce(e.name, '') AS name, " +
                    "       coalesce(e.type, e.entity_type, 'unknown') AS entityType, " +
                    "       coalesce(e.description, '') AS description, " +
                    "       coalesce(e.documentId, '') AS sourceDocumentId, " +
                    "       coalesce(e.source_chunk_id, '') AS sourceChunkId, " +
                    "       e AS rawNode " +
                    "LIMIT 1";
            var result = session.run(cypher, Map.of("graphId", graphId, "entityId", entityId));
            if (!result.hasNext()) {
                return Optional.empty();
            }
            Record record = result.next();
            Map<String, Object> props = new LinkedHashMap<>(record.get("rawNode").asNode().asMap());
            GraphEntity entity = GraphEntity.builder()
                    .id(record.get("id").asString())
                    .graphId(graphId)
                    .name(record.get("name").asString())
                    .entityType(record.get("entityType").asString())
                    .description(record.get("description").asString())
                    .sourceDocumentId(record.get("sourceDocumentId").asString())
                    .sourceChunkId(record.get("sourceChunkId").asString())
                    .properties(toJson(props))
                    .build();
            return Optional.of(entity);
        } catch (Exception e) {
            log.warn("Get entity details from Neo4j failed, graphId={}, entityId={}, err={}",
                    graphId, entityId, e.getMessage());
            return Optional.empty();
        }
    }

    private List<GraphRelation> tryGetEntityRelationsFromNeo4j(String graphId, String entityId) {
        try (var session = createNeo4jSession()) {
            String cypher =
                    "MATCH (e:Entity {graphId: $graphId}) " +
                    "WHERE coalesce(e.nodeId, toString(id(e))) = $entityId OR e.entity_id = $entityId OR e.name = $entityId " +
                    "MATCH (e)-[r]-(other:Entity {graphId: $graphId}) " +
                    "RETURN coalesce(r.id, toString(id(r))) AS id, " +
                    "       coalesce(startNode(r).nodeId, toString(id(startNode(r)))) AS sourceEntityId, " +
                    "       coalesce(endNode(r).nodeId, toString(id(endNode(r)))) AS targetEntityId, " +
                    "       coalesce(r.relation_type, type(r), 'RELATED_TO') AS relationType, " +
                    "       coalesce(r.description, '') AS description, " +
                    "       coalesce(r.weight, 1.0) AS weight, " +
                    "       r AS rawRel";
            var result = session.run(cypher, Map.of("graphId", graphId, "entityId", entityId));
            List<GraphRelation> relations = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                relations.add(GraphRelation.builder()
                        .id(record.get("id").asString())
                        .graphId(graphId)
                        .sourceEntityId(record.get("sourceEntityId").asString())
                        .targetEntityId(record.get("targetEntityId").asString())
                        .relationType(record.get("relationType").asString())
                        .description(record.get("description").asString())
                        .weight(record.get("weight").asDouble(1.0d))
                        .properties(toJson(record.get("rawRel").asRelationship().asMap()))
                        .build());
            }
            return relations;
        } catch (Exception e) {
            log.warn("Get entity relations from Neo4j failed, graphId={}, entityId={}, err={}",
                    graphId, entityId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<GraphEntity> trySearchEntitiesFromNeo4j(String graphId, String keyword) {
        try (var session = createNeo4jSession()) {
            String cypher =
                    "MATCH (e:Entity {graphId: $graphId}) " +
                    "WHERE toLower(coalesce(e.name, '')) CONTAINS toLower($keyword) " +
                    "RETURN coalesce(e.nodeId, toString(id(e))) AS id, " +
                    "       coalesce(e.name, '') AS name, " +
                    "       coalesce(e.type, e.entity_type, 'unknown') AS entityType, " +
                    "       coalesce(e.description, '') AS description, " +
                    "       coalesce(e.documentId, '') AS sourceDocumentId, " +
                    "       coalesce(e.source_chunk_id, '') AS sourceChunkId, " +
                    "       e AS rawNode " +
                    "LIMIT 200";
            var result = session.run(cypher, Map.of("graphId", graphId, "keyword", keyword));
            List<GraphEntity> entities = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                entities.add(GraphEntity.builder()
                        .id(record.get("id").asString())
                        .graphId(graphId)
                        .name(record.get("name").asString())
                        .entityType(record.get("entityType").asString())
                        .description(record.get("description").asString())
                        .sourceDocumentId(record.get("sourceDocumentId").asString())
                        .sourceChunkId(record.get("sourceChunkId").asString())
                        .properties(toJson(record.get("rawNode").asNode().asMap()))
                        .build());
            }
            return entities;
        } catch (Exception e) {
            log.warn("Search entities from Neo4j failed, graphId={}, err={}", graphId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> parseProperties(String properties) {
        if (!StringUtils.hasText(properties)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(properties, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private List<String> toStringList(List<Object> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private String normalizeRelationType(String relationType) {
        String raw = safeText(relationType, "RELATED_TO").trim();
        String normalized = raw.replaceAll("[^\\p{L}\\p{N}_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (!StringUtils.hasText(normalized)) {
            return "RELATED_TO";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long toEpochSeconds(LocalDateTime value) {
        if (value == null) {
            return 0L;
        }
        return value.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
    }

    // 内部类：抽取的实体
    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExtractedEntity {
        private String name;
        private String type;
        private String description;
        private double confidence;
    }

    // 内部类：抽取的关系
    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExtractedRelation {
        private String sourceEntity;
        private String targetEntity;
        // LLM may return "relation", "relationType", "relationtype", "reltype" — accept all
        @com.fasterxml.jackson.annotation.JsonAlias({"relation", "relationtype", "reltype", "relationship"})
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
            private String entityId;
            private String sourceId;
            private String filePath;
            private String truncate;
            private long createdAt;
            private String sourceChunkId;
            private List<String> tags;
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
