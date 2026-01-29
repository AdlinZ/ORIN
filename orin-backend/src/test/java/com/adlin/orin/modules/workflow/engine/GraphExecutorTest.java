package com.adlin.orin.modules.workflow.engine;

import com.adlin.orin.modules.trace.service.TraceService;
import com.adlin.orin.modules.workflow.engine.handler.NodeHandler;
import com.adlin.orin.modules.workflow.service.WorkflowEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class GraphExecutorTest {

    private GraphExecutor graphExecutor;

    @Mock
    private WorkflowEventPublisher eventPublisher;

    @Mock
    private TraceService traceService;

    private Map<String, NodeHandler> nodeHandlers;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        nodeHandlers = new HashMap<>();

        // Mock Handlers
        nodeHandlers.put("startNodeHandler",
                (data, ctx) -> NodeHandler.NodeExecutionResult.success(Collections.emptyMap()));
        nodeHandlers.put("endNodeHandler",
                (data, ctx) -> NodeHandler.NodeExecutionResult.success(Collections.emptyMap()));

        // Generic Action Handler
        nodeHandlers.put("actionNodeHandler", (data, ctx) -> {
            System.out.println("Executing Action: " + data);
            Map<String, Object> out = new HashMap<>();
            out.put("executed", true);
            return NodeHandler.NodeExecutionResult.success(out);
        });

        // IfElse Handler (Mock behavior)
        nodeHandlers.put("ifElseNodeHandler", (data, ctx) -> {
            boolean condition = (Boolean) data.get("condition"); // Cast directly
            String handle = condition ? "if" : "else";
            System.out.println("IfElse Condition: " + condition + " -> " + handle);
            return NodeHandler.NodeExecutionResult.success(Collections.emptyMap(), handle);
        });

        graphExecutor = new GraphExecutor(nodeHandlers, eventPublisher, traceService);
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
        ifData.put("condition", true);
        nodes.add(createNode("decision", "if-else", ifData));

        nodes.add(createNode("actionA", "action", Collections.emptyMap()));
        nodes.add(createNode("actionB", "action", Collections.emptyMap()));

        // Edges
        edges.add(createEdge("start", "decision", null));
        edges.add(createEdge("decision", "actionA", "if"));
        edges.add(createEdge("decision", "actionB", "else"));

        graph.put("nodes", nodes);
        graph.put("edges", edges);

        Map<String, Object> context = new ConcurrentHashMap<>();

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
        ifData.put("condition", false);
        nodes.add(createNode("decision", "if-else", ifData));

        nodes.add(createNode("actionA", "action", Collections.emptyMap()));
        nodes.add(createNode("actionB", "action", Collections.emptyMap()));

        edges.add(createEdge("start", "decision", null));
        edges.add(createEdge("decision", "actionA", "if"));
        edges.add(createEdge("decision", "actionB", "else"));

        graph.put("nodes", nodes);
        graph.put("edges", edges);

        Map<String, Object> context = new ConcurrentHashMap<>();

        Map<String, Object> result = graphExecutor.executeGraph(graph, context);

        assertTrue((Boolean) result.get("success"));

        Map<String, Object> resContext = (Map<String, Object>) result.get("context");
        assertFalse(resContext.containsKey("actionA"));
        assertTrue(resContext.containsKey("actionB"));
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
