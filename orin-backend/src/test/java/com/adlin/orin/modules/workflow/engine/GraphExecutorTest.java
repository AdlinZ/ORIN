package com.adlin.orin.modules.workflow.engine;

import com.adlin.orin.common.exception.WorkflowExecutionException;
import com.adlin.orin.modules.trace.service.TraceService;
import com.adlin.orin.modules.workflow.engine.handler.NodeHandler;
import com.adlin.orin.modules.workflow.engine.handler.NodeExecutionResult;
import com.adlin.orin.modules.workflow.engine.handler.IfElseNodeHandler;
import com.adlin.orin.modules.workflow.service.WorkflowEventPublisher;
import com.adlin.orin.modules.observability.service.LangfuseObservabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

class GraphExecutorTest {

    private GraphExecutor graphExecutor;

    @Mock
    private WorkflowEventPublisher eventPublisher;

    @Mock
    private TraceService traceService;

    @Mock
    private LangfuseObservabilityService langfuseObservabilityService;

    private Map<String, NodeHandler> nodeHandlers;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        nodeHandlers = new HashMap<>();

        // Mock Handlers
        nodeHandlers.put("startNodeHandler",
                (data, ctx) -> NodeExecutionResult.success(Collections.emptyMap()));
        nodeHandlers.put("endNodeHandler",
                (data, ctx) -> NodeExecutionResult.success(Collections.emptyMap()));
        nodeHandlers.put("answerNodeHandler",
                (data, ctx) -> NodeExecutionResult.success(Map.of("answer", "done")));

        // Generic Action Handler
        nodeHandlers.put("actionNodeHandler", (data, ctx) -> {
            System.out.println("Executing Action: " + data);
            Map<String, Object> out = new HashMap<>();
            out.put("executed", true);
            return NodeExecutionResult.success(out);
        });

        nodeHandlers.put("ifElseNodeHandler", new IfElseNodeHandler());

        graphExecutor = new GraphExecutor(nodeHandlers, eventPublisher, traceService, langfuseObservabilityService);
        graphExecutor.setInstanceId(1L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testBranchingLogic_TruePath() {
        // Build Graph: Start -> If(True) -> ActionA
        // -> ActionB (Else) - Should be SKIPPED

        Map<String, Object> graph = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        nodes.add(createNode("start", "start", Collections.emptyMap()));

        Map<String, Object> ifData = new HashMap<>();
        ifData.put("conditions", List.of(Map.of(
                "variable", "inputs.status",
                "operator", "equals",
                "value", "ready")));
        ifData.put("logical_operator", "and");
        nodes.add(createNode("decision", "if-else", ifData));

        nodes.add(createNode("actionA", "action", Collections.emptyMap()));
        nodes.add(createNode("actionB", "action", Collections.emptyMap()));

        // Edges
        edges.add(createEdge("start", "decision", null));
        edges.add(createEdge("decision", "actionA", "if"));
        edges.add(createEdge("decision", "actionB", "else"));

        graph.put("nodes", nodes);
        graph.put("edges", edges);

        Map<String, Object> context = new ConcurrentHashMap<>(Map.of("inputs", Map.of("status", "ready")));

        Map<String, Object> result = graphExecutor.executeGraph(graph, context);

        assertTrue((Boolean) result.get("success"));

        Map<String, Object> resContext = (Map<String, Object>) result.get("context");
        assertTrue(resContext.containsKey("actionA"));
        assertFalse(resContext.containsKey("actionB")); // Should be skipped
    }

    @SuppressWarnings("unchecked")
    @Test
    void testBranchingLogic_FalsePath() {
        // Build Graph: Start -> If(False) -> ActionA (If) - Should be SKIPPED
        // -> ActionB (Else)

        Map<String, Object> graph = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        nodes.add(createNode("start", "start", Collections.emptyMap()));

        Map<String, Object> ifData = new HashMap<>();
        ifData.put("conditions", List.of(Map.of(
                "variable", "inputs.status",
                "operator", "equals",
                "value", "ready")));
        ifData.put("logical_operator", "and");
        nodes.add(createNode("decision", "if-else", ifData));

        nodes.add(createNode("actionA", "action", Collections.emptyMap()));
        nodes.add(createNode("actionB", "action", Collections.emptyMap()));

        edges.add(createEdge("start", "decision", null));
        edges.add(createEdge("decision", "actionA", "if"));
        edges.add(createEdge("decision", "actionB", "else"));

        graph.put("nodes", nodes);
        graph.put("edges", edges);

        Map<String, Object> context = new ConcurrentHashMap<>(Map.of("inputs", Map.of("status", "blocked")));

        Map<String, Object> result = graphExecutor.executeGraph(graph, context);

        assertTrue((Boolean) result.get("success"));

        Map<String, Object> resContext = (Map<String, Object>) result.get("context");
        assertFalse(resContext.containsKey("actionA"));
        assertTrue(resContext.containsKey("actionB"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testBranchingLogic_UsesOrinConditionsContract() {
        Map<String, Object> graph = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        nodes.add(createNode("start", "start", Collections.emptyMap()));
        nodes.add(createNode("decision", "if-else", Map.of(
                "conditions", List.of(Map.of(
                        "variable", "inputs.status",
                        "operator", "equals",
                        "value", "ready")),
                "logical_operator", "and")));
        nodes.add(createNode("actionA", "action", Collections.emptyMap()));
        nodes.add(createNode("actionB", "action", Collections.emptyMap()));

        edges.add(createEdge("start", "decision", null));
        edges.add(createEdge("decision", "actionA", "if"));
        edges.add(createEdge("decision", "actionB", "else"));

        graph.put("nodes", nodes);
        graph.put("edges", edges);

        Map<String, Object> result = graphExecutor.executeGraph(
                graph,
                new ConcurrentHashMap<>(Map.of("inputs", Map.of("status", "ready"))));

        Map<String, Object> resContext = (Map<String, Object>) result.get("context");
        assertTrue(resContext.containsKey("actionA"));
        assertFalse(resContext.containsKey("actionB"));
    }

    @Test
    void testNodeFailure() {
        // Mock a failing node
        nodeHandlers.put("failnodeNodeHandler", (data, ctx) -> {
            throw new RuntimeException("Node execution failed");
        });

        Map<String, Object> graph = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        nodes.add(createNode("start", "start", Collections.emptyMap()));
        nodes.add(createNode("failNode", "failNode", Collections.emptyMap()));
        edges.add(createEdge("start", "failNode", null));

        graph.put("nodes", nodes);
        graph.put("edges", edges);

        assertThrows(WorkflowExecutionException.class, () -> {
            graphExecutor.executeGraph(graph, new ConcurrentHashMap<>());
        });
        verify(eventPublisher).publishNodeFailed(eq(1L), eq("failNode"), anyString(), anyString());
        verify(eventPublisher).publishWorkflowFailed(eq(1L), anyString());
    }

    @Test
    void testUnknownNodeTypeFailsValidation() {
        Map<String, Object> graph = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(createNode("start", "start", Collections.emptyMap()));
        nodes.add(createNode("unknown", "unknown-type", Collections.emptyMap()));
        graph.put("nodes", nodes);
        graph.put("edges", List.of(createEdge("start", "unknown", null)));

        assertThrows(IllegalArgumentException.class, () ->
                graphExecutor.executeGraph(graph, new ConcurrentHashMap<>()));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testTerminalOutputsAreReturnedUnderOutputs() {
        nodeHandlers.put("llmNodeHandler", (data, ctx) -> NodeExecutionResult.success(Map.of("text", "hello")));
        nodeHandlers.put("endNodeHandler", (data, ctx) -> NodeExecutionResult.success(Map.of("answer", "hello")));

        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", List.of(
                createNode("start", "start", Collections.emptyMap()),
                createNode("llm", "llm", Collections.emptyMap()),
                createNode("end", "end", Map.of(
                        "outputs", List.of(Map.of("name", "answer", "value", "{{ llm.text }}"))))));
        graph.put("edges", List.of(
                createEdge("start", "llm", null),
                createEdge("llm", "end", null)));

        Map<String, Object> result = graphExecutor.executeGraph(graph, new ConcurrentHashMap<>());

        assertTrue((Boolean) result.get("success"));
        assertFalse(result.containsKey("answer"));
        assertEquals("hello", ((Map<String, Object>) result.get("outputs")).get("answer"));
        assertTrue(((Map<String, Object>) result.get("context")).containsKey("llm"));
    }

    @Test
    void testCycleFailsValidation() {
        Map<String, Object> graph = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(createNode("start", "start", Collections.emptyMap()));
        nodes.add(createNode("action", "action", Collections.emptyMap()));
        graph.put("nodes", nodes);
        graph.put("edges", List.of(
                createEdge("start", "action", null),
                createEdge("action", "start", null)
        ));

        assertThrows(IllegalArgumentException.class, () ->
                graphExecutor.executeGraph(graph, new ConcurrentHashMap<>()));
    }

    private Map<String, Object> createNode(String id, String type, Map<String, Object> data) {
        Map<String, Object> n = new HashMap<>();
        n.put("id", id);
        n.put("type", type);
        n.put("data", data);
        return n;
    }

    private Map<String, Object> createEdge(String source, String target, String sourceHandle) {
        Map<String, Object> e = new HashMap<>();
        e.put("source", source);
        e.put("target", target);
        if (sourceHandle != null)
            e.put("sourceHandle", sourceHandle);
        return e;
    }
}
