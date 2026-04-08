package com.adlin.orin.modules.workflow.engine;

import com.adlin.orin.modules.workflow.engine.handler.IterationNodeHandler;
import com.adlin.orin.modules.workflow.engine.handler.LoopNodeHandler;
import com.adlin.orin.modules.workflow.engine.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Iteration and Loop Node Handler Tests (Unit Test with Mockito)
 */
public class IterationNodeHandlerTest {

    @Mock
    private IterationNodeHandler iterationNodeHandler;

    @Mock
    private LoopNodeHandler loopNodeHandler;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testIterationNodeWithFixedCount() {
        // Setup context for fixed count iteration
        Map<String, Object> nodeData = new HashMap<>();
        Map<String, Object> context = new HashMap<>();
        context.put("iterations", 3);

        // Execute
        NodeExecutionResult result = iterationNodeHandler.execute(nodeData, context);

        // Verify
        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputs());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.getOutputs().get("results");
        assertEquals(3, results.size());
        assertEquals(3, result.getOutputs().get("totalIterations"));
    }

    @Test
    public void testIterationNodeWithItems() {
        Map<String, Object> nodeData = new HashMap<>();
        Map<String, Object> context = new HashMap<>();

        // Set iteration items
        List<String> items = Arrays.asList("item1", "item2", "item3", "item4");
        context.put("items", items);
        context.put("iterations", 10);

        NodeExecutionResult result = iterationNodeHandler.execute(nodeData, context);

        assertTrue(result.isSuccess());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.getOutputs().get("results");
        assertEquals(4, results.size());

        // Verify first item
        assertEquals("item1", results.get(0).get("item"));
    }

    @Test
    public void testIterationNodeWithMaxLimit() {
        Map<String, Object> nodeData = new HashMap<>();
        Map<String, Object> context = new HashMap<>();

        // Try to set iteration count exceeding limit
        context.put("iterations", 200);

        NodeExecutionResult result = iterationNodeHandler.execute(nodeData, context);

        assertTrue(result.isSuccess());

        // Should be limited to 100
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.getOutputs().get("results");
        assertEquals(100, results.size());
    }

    @Test
    public void testLoopNodeBasic() {
        Map<String, Object> nodeData = new HashMap<>();
        Map<String, Object> context = new HashMap<>();

        context.put("maxIterations", 5);

        NodeExecutionResult result = loopNodeHandler.execute(nodeData, context);

        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputs());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.getOutputs().get("results");
        assertEquals(5, results.size());
        assertFalse((Boolean) result.getOutputs().get("exitedEarly"));
    }

    @Test
    public void testLoopNodeWithMaxLimit() {
        Map<String, Object> nodeData = new HashMap<>();
        Map<String, Object> context = new HashMap<>();

        // Try to set iteration count exceeding limit
        context.put("maxIterations", 2000);

        NodeExecutionResult result = loopNodeHandler.execute(nodeData, context);

        assertTrue(result.isSuccess());

        // Should be limited to 1000
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.getOutputs().get("results");
        assertEquals(1000, results.size());
    }
}