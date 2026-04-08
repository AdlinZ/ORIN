package com.adlin.orin.modules.workflow.engine;

import com.adlin.orin.modules.workflow.engine.handler.IterationNodeHandler;
import com.adlin.orin.modules.workflow.engine.handler.LoopNodeHandler;
import com.adlin.orin.modules.workflow.engine.handler.NodeExecutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Iteration and Loop Node Handler Tests
 */
@SpringBootTest
public class IterationNodeHandlerTest {

    @Autowired
    private IterationNodeHandler iterationNodeHandler;

    @Autowired
    private LoopNodeHandler loopNodeHandler;

    @Test
    public void testIterationNodeWithFixedCount() {
        Map<String, Object> nodeData = new HashMap<>();
        Map<String, Object> context = new HashMap<>();
        
        // 设置迭代次数
        context.put("iterations", 3);
        
        NodeExecutionResult result = iterationNodeHandler.execute(nodeData, context);
        
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
        
        // 设置迭代项
        List<String> items = List.of("item1", "item2", "item3", "item4");
        context.put("items", items);
        context.put("iterations", 10);
        
        NodeExecutionResult result = iterationNodeHandler.execute(nodeData, context);
        
        assertTrue(result.isSuccess());
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.getOutputs().get("results");
        assertEquals(4, results.size());
        
        // 验证第一项
        assertEquals("item1", results.get(0).get("item"));
    }

    @Test
    public void testIterationNodeWithMaxLimit() {
        Map<String, Object> nodeData = new HashMap<>();
        Map<String, Object> context = new HashMap<>();
        
        // 尝试设置超过限制的迭代次数
        context.put("iterations", 200);
        
        NodeExecutionResult result = iterationNodeHandler.execute(nodeData, context);
        
        assertTrue(result.isSuccess());
        
        // 应该被限制在100次
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
        
        // 尝试设置超过限制的迭代次数
        context.put("maxIterations", 2000);
        
        NodeExecutionResult result = loopNodeHandler.execute(nodeData, context);
        
        assertTrue(result.isSuccess());
        
        // 应该被限制在1000次
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.getOutputs().get("results");
        assertEquals(1000, results.size());
    }
}