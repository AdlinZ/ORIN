package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.config.Neo4jConfig;
import com.adlin.orin.modules.knowledge.component.SiliconFlowEmbeddingAdapter;
import com.adlin.orin.modules.knowledge.entity.GraphBuildState;
import com.adlin.orin.modules.knowledge.entity.GraphEntity;
import com.adlin.orin.modules.knowledge.entity.GraphRelation;
import com.adlin.orin.modules.knowledge.entity.KnowledgeGraph;
import com.adlin.orin.modules.knowledge.repository.GraphEntityRepository;
import com.adlin.orin.modules.knowledge.repository.GraphRelationRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeGraphRepository;
import com.adlin.orin.modules.system.repository.SystemConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphExtractionServiceTest {

    private static final String GRAPH_ID = "graph-fixture";
    private static final String DOCUMENT_ID = "doc-fixture";

    @Mock
    private KnowledgeGraphRepository knowledgeGraphRepository;

    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Mock
    private GraphEntityRepository graphEntityRepository;

    @Mock
    private GraphRelationRepository graphRelationRepository;

    @Mock
    private DocumentManageService documentManageService;

    @Mock
    private SiliconFlowEmbeddingAdapter embeddingAdapter;

    @Mock
    private SystemConfigRepository systemConfigRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<GraphEntity> savedEntities = new ArrayList<>();
    private final List<GraphRelation> savedRelations = new ArrayList<>();
    private final List<KnowledgeGraph> savedGraphSnapshots = new ArrayList<>();

    private JsonNode fixture;
    private GraphExtractionService service;
    private KnowledgeGraph graph;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("fixtures/graph-extraction-sample.json")) {
            fixture = objectMapper.readTree(input);
        }

        Neo4jConfig.Neo4jConnectionManager neo4j = new Neo4jConfig.Neo4jConnectionManager(
                "bolt://127.0.0.1:1",
                "127.0.0.1",
                1,
                "neo4j",
                "test",
                "neo4j",
                1,
                1
        );

        service = new GraphExtractionService(
                neo4j,
                knowledgeGraphRepository,
                knowledgeDocumentRepository,
                graphEntityRepository,
                graphRelationRepository,
                documentManageService,
                embeddingAdapter,
                objectMapper,
                systemConfigRepository
        );

        savedEntities.clear();
        savedRelations.clear();
        savedGraphSnapshots.clear();
        graph = KnowledgeGraph.builder()
                .id(GRAPH_ID)
                .name("Fixture Graph")
                .build();

        when(knowledgeGraphRepository.findById(GRAPH_ID)).thenReturn(Optional.of(graph));
        when(knowledgeGraphRepository.save(any(KnowledgeGraph.class))).thenAnswer(invocation -> {
            KnowledgeGraph saved = invocation.getArgument(0);
            savedGraphSnapshots.add(snapshotGraph(saved));
            return saved;
        });
        when(systemConfigRepository.findByConfigKey(anyString())).thenReturn(Optional.empty());
        when(graphEntityRepository.save(any(GraphEntity.class))).thenAnswer(invocation -> {
            GraphEntity entity = invocation.getArgument(0);
            entity.setId("entity-" + UUID.randomUUID());
            savedEntities.add(entity);
            return entity;
        });
        when(graphEntityRepository.findByGraphId(GRAPH_ID)).thenAnswer(invocation -> new ArrayList<>(savedEntities));
        when(graphEntityRepository.countByGraphId(GRAPH_ID)).thenAnswer(invocation -> (long) savedEntities.size());
        when(graphRelationRepository.countByGraphId(GRAPH_ID)).thenAnswer(invocation -> (long) savedRelations.size());
    }

    @Test
    @DisplayName("Graph extraction stores valid entity and relation fixtures when Neo4j is unavailable")
    void buildGraph_storesEntitiesAndRelationsWithNeo4jFallback() {
        stubLlmCase("normal");
        stubRelationSave();

        service.buildGraph(GRAPH_ID, DOCUMENT_ID, "ORIN uses LangGraph for workflow execution.");

        assertEquals(GraphBuildState.SUCCESS, graph.getBuildStatus());
        assertEquals(2, savedEntities.size());
        assertEquals(1, savedRelations.size());
        assertEquals("ORIN", savedEntities.get(0).getName());
        assertEquals("USES", savedRelations.get(0).getRelationType());
        assertEquals(GRAPH_ID, savedRelations.get(0).getGraphId());
        verify(graphEntityRepository).countByGraphId(GRAPH_ID);
        verify(graphRelationRepository).countByGraphId(GRAPH_ID);
    }

    @Test
    @DisplayName("Graph extraction persists final graph stats on the last save")
    void buildGraph_persistsFinalStatsOnLastSave() {
        KnowledgeGraph staleGraph = KnowledgeGraph.builder()
                .id(GRAPH_ID)
                .name("Stale Graph")
                .build();
        KnowledgeGraph freshGraph = KnowledgeGraph.builder()
                .id(GRAPH_ID)
                .name("Fresh Graph")
                .build();
        graph = staleGraph;
        when(knowledgeGraphRepository.findById(GRAPH_ID))
                .thenReturn(Optional.of(staleGraph))
                .thenReturn(Optional.of(freshGraph));
        stubLlmCase("normal");
        stubRelationSave();

        service.buildGraph(GRAPH_ID, DOCUMENT_ID, "ORIN uses LangGraph for workflow execution.");

        KnowledgeGraph lastSaved = savedGraphSnapshots.get(savedGraphSnapshots.size() - 1);
        assertEquals(GraphBuildState.SUCCESS, lastSaved.getBuildStatus());
        assertEquals(2, lastSaved.getEntityCount());
        assertEquals(1, lastSaved.getRelationCount());
    }

    @Test
    @DisplayName("Graph extraction succeeds when LLM returns entities but no relations")
    void buildGraph_succeedsWithEntitiesOnlyFixture() {
        stubLlmCase("entitiesOnly");

        service.buildGraph(GRAPH_ID, DOCUMENT_ID, "Milvus stores vector embeddings.");

        assertEquals(GraphBuildState.SUCCESS, graph.getBuildStatus());
        assertEquals(1, savedEntities.size());
        assertEquals("Milvus", savedEntities.get(0).getName());
        assertTrue(savedRelations.isEmpty());
        assertEquals(1, graph.getEntityCount());
        assertEquals(0, graph.getRelationCount());
    }

    @Test
    @DisplayName("Graph extraction skips partial invalid relation rows without blocking valid rows")
    void buildGraph_skipsInvalidRelationRowsAndStoresValidRows() {
        stubLlmCase("partialInvalid");
        stubRelationSave();

        service.buildGraph(GRAPH_ID, DOCUMENT_ID, "Neo4j mirrors graph data to MySQL fallback storage.");

        assertEquals(GraphBuildState.SUCCESS, graph.getBuildStatus());
        assertEquals(2, savedEntities.size());
        assertEquals(1, savedRelations.size());
        assertEquals("FALLS_BACK_TO", savedRelations.get(0).getRelationType());
        assertTrue(savedRelations.stream().noneMatch(r -> "BROKEN_ROW".equals(r.getRelationType())));
        assertTrue(savedRelations.stream().noneMatch(r -> "UNKNOWN_SOURCE".equals(r.getRelationType())));
        assertEquals(2, graph.getEntityCount());
        assertEquals(1, graph.getRelationCount());
    }

    private void stubLlmCase(String caseName) {
        JsonNode node = fixture.get(caseName);
        when(embeddingAdapter.chat(anyString(), isNull()))
                .thenReturn(node.get("entities").toString())
                .thenReturn(node.get("relations").toString());
    }

    private void stubRelationSave() {
        when(graphRelationRepository.save(any(GraphRelation.class))).thenAnswer(invocation -> {
            GraphRelation relation = invocation.getArgument(0);
            relation.setId("relation-" + UUID.randomUUID());
            savedRelations.add(relation);
            return relation;
        });
    }

    private KnowledgeGraph snapshotGraph(KnowledgeGraph graph) {
        return KnowledgeGraph.builder()
                .id(graph.getId())
                .name(graph.getName())
                .buildStatus(graph.getBuildStatus())
                .entityCount(graph.getEntityCount())
                .relationCount(graph.getRelationCount())
                .lastBuildAt(graph.getLastBuildAt())
                .lastSuccessBuildAt(graph.getLastSuccessBuildAt())
                .errorMessage(graph.getErrorMessage())
                .build();
    }
}
