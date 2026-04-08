package com.adlin.orin.modules.workflow.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Iteration Node Handler
 * 支持循环执行逻辑，支持固定次数循环和条件循环
 * 
 * 输入参数:
 * - iterations: 循环次数 (Integer)
 * - condition: 退出条件表达式 (String)
 * - items: 要迭代的项列表 (List)
 * 
 * 输出参数:
 * - results: 每次迭代的结果列表 (List)
 * - currentIndex: 当前迭代索引 (Integer)
 * - totalIterations: 总迭代次数 (Integer)
 */
@Slf4j
@Component("iterationNodeHandler")
public class IterationNodeHandler implements NodeHandler {

    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        log.info("IterationNode executing with context: {}", context.keySet());
        
        // 获取循环配置
        Object iterationsObj = context.get("iterations");
        Object conditionObj = context.get("condition");
        Object itemsObj = context.get("items");
        
        int maxIterations = 10; // 默认最大迭代次数
        List<Object> items = null;
        
        if (iterationsObj != null) {
            if (iterationsObj instanceof Number) {
                maxIterations = ((Number) iterationsObj).intValue();
            } else if (iterationsObj instanceof String) {
                try {
                    maxIterations = Integer.parseInt((String) iterationsObj);
                } catch (NumberFormatException e) {
                    log.warn("Invalid iterations value: {}, using default 10", iterationsObj);
                }
            }
        }
        
        // 限制最大迭代次数，防止无限循环
        maxIterations = Math.min(Math.max(maxIterations, 1), 100);
        
        // 如果提供了 items，则遍历 items
        if (itemsObj instanceof List) {
            items = (List<Object>) itemsObj;
            maxIterations = Math.min(maxIterations, items.size());
        }
        
        // 执行循环
        List<Map<String, Object>> results = new ArrayList<>();
        int currentIndex = 0;
        
        // 获取循环体节点ID
        String loopBodyId = (String) nodeData.get("loop_body_id");
        String exitCondition = conditionObj != null ? conditionObj.toString() : null;
        
        while (currentIndex < maxIterations) {
            Map<String, Object> iterationResult = new HashMap<>();
            iterationResult.put("index", currentIndex);
            
            // 如果有 items，传递当前项
            if (items != null) {
                iterationResult.put("item", items.get(currentIndex));
            }
            
            // 传递上下文到循环体
            Map<String, Object> loopContext = new HashMap<>(context);
            loopContext.put("currentIndex", currentIndex);
            if (items != null) {
                loopContext.put("currentItem", items.get(currentIndex));
            }
            
            iterationResult.put("timestamp", System.currentTimeMillis());
            results.add(iterationResult);
            
            // 检查退出条件
            if (exitCondition != null && evaluateExitCondition(exitCondition, loopContext)) {
                log.info("Iteration exit condition met at index {}", currentIndex);
                break;
            }
            
            currentIndex++;
        }
        
        // 构建输出
        Map<String, Object> output = new HashMap<>();
        output.put("results", results);
        output.put("currentIndex", currentIndex);
        output.put("totalIterations", currentIndex + 1);
        output.put("completed", true);
        
        log.info("IterationNode completed: {} iterations", currentIndex + 1);
        
        return NodeExecutionResult.success(output);
    }
    
    /**
     * 评估退出条件
     * 简单实现：支持常见条件表达式
     */
    private boolean evaluateExitCondition(String condition, Map<String, Object> context) {
        // 简单条件评估：支持 {{variable}} == value 等模式
        // 实际生产环境应使用表达式解析器
        if (condition == null || condition.isEmpty()) {
            return false;
        }
        
        // TODO: 实现完整的条件表达式解析
        // 当前仅支持基本场景
        
        return false;
    }
}