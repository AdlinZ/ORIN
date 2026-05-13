package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.config.Neo4jConfig;
import com.adlin.orin.modules.knowledge.component.SiliconFlowEmbeddingAdapter;
import com.adlin.orin.modules.knowledge.entity.GraphEntity;
import com.adlin.orin.modules.knowledge.entity.GraphRelation;
import com.adlin.orin.modules.knowledge.repository.GraphEntityRepository;
import com.adlin.orin.modules.knowledge.repository.GraphRelationRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeGraphRepository;
import com.adlin.orin.modules.system.repository.SystemConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphExtractionSearchServiceTest {

    private static final String GRAPH_ID = "graph-search";

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

    private GraphExtractionService service;

    @BeforeEach
    void setUp() {
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
                new ObjectMapper(),
                systemConfigRepository
        );

        when(systemConfigRepository.findByConfigKey(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Entity search falls back to MySQL when Neo4j is unavailable")
    void searchEntities_fallsBackToMySql() {
        GraphEntity entity = GraphEntity.builder()
                .id("entity-1")
                .graphId(GRAPH_ID)
                .name("ORIN")
                .build();
        when(graphEntityRepository.findByGraphIdAndNameContainingIgnoreCase(GRAPH_ID, "ORIN"))
                .thenReturn(List.of(entity));

        List<GraphEntity> result = service.searchEntities(GRAPH_ID, "ORIN");

        assertEquals(1, result.size());
        assertEquals("ORIN", result.get(0).getName());
        verify(graphEntityRepository).findByGraphIdAndNameContainingIgnoreCase(GRAPH_ID, "ORIN");
    }

    @Test
    @DisplayName("Entity details fallback rejects entities from another graph")
    void getEntityDetails_fallbackScopesByGraphId() {
        GraphEntity otherGraphEntity = GraphEntity.builder()
                .id("entity-other")
                .graphId("other-graph")
                .name("ORIN")
                .build();
        when(graphEntityRepository.findById("entity-other")).thenReturn(Optional.of(otherGraphEntity));

        Optional<GraphEntity> result = service.getEntityDetails(GRAPH_ID, "entity-other");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Entity relations fallback only searches relations in the requested graph")
    void getEntityRelations_fallbackScopesByGraphId() {
        GraphRelation matching = GraphRelation.builder()
                .id("relation-1")
                .graphId(GRAPH_ID)
                .sourceEntityId("entity-1")
                .targetEntityId("entity-2")
                .relationType("DEPENDS_ON")
                .build();
        GraphRelation unrelated = GraphRelation.builder()
                .id("relation-2")
                .graphId(GRAPH_ID)
                .sourceEntityId("entity-3")
                .targetEntityId("entity-4")
                .relationType("CONTAINS")
                .build();
        when(graphRelationRepository.findByGraphId(GRAPH_ID)).thenReturn(List.of(matching, unrelated));

        List<GraphRelation> result = service.getEntityRelations(GRAPH_ID, "entity-1");

        assertEquals(1, result.size());
        assertEquals("relation-1", result.get(0).getId());
        verify(graphRelationRepository).findByGraphId(GRAPH_ID);
    }

    @Test
    @DisplayName("Visualization fallback applies limit before loading relations and preserves entity ids")
    void getVisualizationData_fallbackAppliesLimitAndPreservesEntityIds() {
        GraphEntity entityOne = GraphEntity.builder()
                .id("entity-1")
                .graphId(GRAPH_ID)
                .name("ORIN")
                .entityType("Platform")
                .build();
        GraphEntity entityTwo = GraphEntity.builder()
                .id("entity-2")
                .graphId(GRAPH_ID)
                .name("LangGraph")
                .entityType("Engine")
                .build();
        GraphRelation relation = GraphRelation.builder()
                .id("relation-1")
                .graphId(GRAPH_ID)
                .sourceEntityId("entity-1")
                .targetEntityId("entity-2")
                .relationType("USES")
                .build();
        when(graphEntityRepository.findByGraphId(eq(GRAPH_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entityOne, entityTwo)));
        when(graphRelationRepository.findByGraphIdAndEntityIds(eq(GRAPH_ID), any(Set.class), any(Pageable.class)))
                .thenReturn(List.of(relation));

        GraphExtractionService.GraphVisualizationData result =
                service.getVisualizationData(GRAPH_ID, null, 2);

        assertEquals(2, result.getNodes().size());
        assertEquals("entity-1", result.getNodes().get(0).getId());
        assertEquals("entity-1", result.getNodes().get(0).getEntityId());
        assertEquals(1, result.getEdges().size());
        assertEquals("entity-1", result.getEdges().get(0).getSource());
        assertEquals("entity-2", result.getEdges().get(0).getTarget());
        verify(graphEntityRepository).findByGraphId(eq(GRAPH_ID), argThat(pageable -> pageable.getPageSize() == 2));
        verify(graphRelationRepository).findByGraphIdAndEntityIds(
                eq(GRAPH_ID),
                eq(Set.of("entity-1", "entity-2")),
                argThat(pageable -> pageable.getPageSize() == 6));
    }
}
