package com.adlin.orin.modules.knowledge.controller;

import com.adlin.orin.modules.knowledge.entity.GraphBuildState;
import com.adlin.orin.modules.knowledge.entity.GraphEntity;
import com.adlin.orin.modules.knowledge.entity.KnowledgeGraph;
import com.adlin.orin.modules.knowledge.service.GraphExtractionService;
import com.adlin.orin.modules.knowledge.service.KnowledgeGraphService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeGraphControllerTest {

    @Mock
    private KnowledgeGraphService knowledgeGraphService;

    @Mock
    private GraphExtractionService graphExtractionService;

    @InjectMocks
    private KnowledgeGraphController controller;

    @Test
    @DisplayName("Build endpoint marks graph building and dispatches async extraction")
    void buildGraph_dispatchesAsyncExtraction() {
        String graphId = "graph-001";
        KnowledgeGraph graph = KnowledgeGraph.builder()
                .id(graphId)
                .name("测试图谱")
                .buildStatus(GraphBuildState.BUILDING)
                .build();

        when(knowledgeGraphService.triggerBuild(graphId)).thenReturn(graph);

        ResponseEntity<Map<String, Object>> response = controller.buildGraph(graphId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("success"));
        assertSame(graph, body.get("graph"));
        assertEquals(GraphBuildState.BUILDING, ((KnowledgeGraph) body.get("graph")).getBuildStatus());
        verify(knowledgeGraphService).triggerBuild(graphId);
        verify(graphExtractionService).buildGraphAsync(graphId);
    }

    @Test
    @DisplayName("Search endpoint uses Neo4j-first graph extraction service")
    void searchGraphEntities_usesGraphExtractionService() {
        String graphId = "graph-001";
        GraphEntity entity = GraphEntity.builder()
                .id("entity-001")
                .graphId(graphId)
                .name("ORIN")
                .build();
        when(graphExtractionService.searchEntities(graphId, "ORIN")).thenReturn(List.of(entity));

        ResponseEntity<List<GraphEntity>> response = controller.searchGraphEntities(graphId, "ORIN");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("ORIN", response.getBody().get(0).getName());
        verify(graphExtractionService).searchEntities(graphId, "ORIN");
    }

    @Test
    @DisplayName("Visualization endpoint forwards limit to graph extraction service")
    void getVisualizationData_forwardsLimit() {
        String graphId = "graph-001";
        GraphExtractionService.GraphVisualizationData.Node node =
                new GraphExtractionService.GraphVisualizationData.Node(
                        "entity-1",
                        "ORIN",
                        "Platform",
                        "desc",
                        "doc-1",
                        "entity-1",
                        "doc-1",
                        "doc-1",
                        "",
                        0L,
                        "",
                        List.of("Platform"));
        GraphExtractionService.GraphVisualizationData data =
                new GraphExtractionService.GraphVisualizationData(List.of(node), List.of(), List.of("Platform"));
        when(graphExtractionService.getVisualizationData(graphId, null, 123)).thenReturn(data);

        ResponseEntity<Map<String, Object>> response = controller.getVisualizationData(graphId, null, 123);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(node), response.getBody().get("nodes"));
        verify(graphExtractionService).getVisualizationData(graphId, null, 123);
    }
}
