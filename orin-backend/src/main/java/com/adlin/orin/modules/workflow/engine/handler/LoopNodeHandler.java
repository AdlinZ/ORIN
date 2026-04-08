package com.adlin.orin.modules.workflow.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loop Node Handler
 * 支持 while 循环逻辑，与 IterationNodeHandler 配合使用
 * 
 * 输入参数:
 * - maxIterations: 最大迭代次数 (Integer)
 * - condition: 循环继续条件 (String)
 * - breakCondition: 退出条件 (String)
 * 
 * 输出参数:
 * - results: 每次迭代的结果列表 (List)
 * - currentIndex: 当前迭代索引 (Integer)
 * - exitedEarly: 是否提前退出 (Boolean)
 */
@Slf4j
@Component("loopNodeHandler")
public class LoopNodeHandler implements NodeHandler {

    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        log.info("LoopNode executing with context: {}", context.keySet());
        
        // 获取循环配置
        Object maxIterationsObj = context.get("maxIterations");
        Object conditionObj = context.get("condition");
        Object breakConditionObj = context.get("breakCondition");
        
        int maxIterations = 50; // 默认最大迭代次数
        
        if (maxIterationsObj != null) {
            if (maxIterationsObj instanceof Number) {
                maxIterations = ((Number) maxIterationsObj).intValue();
            } else if (maxIterationsObj instanceof String) {
                try {
                    maxIterations = Integer.parseInt((String) maxIterationsObj);
                } catch (NumberFormatException e) {
                    log.warn("Invalid maxIterations value: {}, using default 50", maxIterationsObj);
                }
            }
        }
        
        // 限制最大迭代次数
        maxIterations = Math.min(Math.max(maxIterations, 1), 1000);
        
        // 执行循环
        List<Map<String, Object>> results = new ArrayList<>();
        int currentIndex = 0;
        boolean exitedEarly = false;
        
        String continueCondition = conditionObj != null ? conditionObj.toString() : null;
        String breakCondition = breakConditionObj != null ? breakConditionObj.toString() : null;
        
        while (currentIndex < maxIterations) {
            Map<String, Object> iterationResult = new HashMap<>();
            iterationResult.put("index", currentIndex);
            iterationResult.put("timestamp", System.currentTimeMillis());
            
            // 传递上下文
            Map<String, Object> loopContext = new HashMap<>(context);
            loopContext.put("currentIndex", currentIndex);
            
            results.add(iterationResult);
            
            // 检查继续条件
            if (continueCondition != null && !evaluateCondition(continueCondition, loopContext)) {
                log.info("Loop continue condition not met at index {}", currentIndex);
                exitedEarly = true;
                break;
            }
            
            // 检查退出条件
            if (breakCondition != null && evaluateCondition(breakCondition, loopContext)) {
                log.info("Loop break condition met at index {}", currentIndex);
                exitedEarly = true;
                break;
            }
            
            currentIndex++;
        }
        
        // 构建输出
        Map<String, Object> output = new HashMap<>();
        output.put("results", results);
        output.put("currentIndex", currentIndex);
        output.put("totalIterations", currentIndex);
        output.put("exitedEarly", exitedEarly);
        output.put("completed", true);
        
        log.info("LoopNode completed: {} iterations, exitedEarly={}", currentIndex, exitedEarly);
        
        return NodeExecutionResult.success(output);
    }
    
    /**
     * 评估条件表达式
     * 简单实现
     */
    private boolean evaluateCondition(String condition, Map<String, Object> context) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        
        // TODO: 实现完整的条件表达式解析
        // 当前仅支持基本场景
        
        return true;
    }
}