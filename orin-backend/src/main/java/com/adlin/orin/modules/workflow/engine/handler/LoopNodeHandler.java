package com.adlin.orin.modules.workflow.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern CONDITION_PATTERN = Pattern.compile("^\\s*(\\{\\{)?([a-zA-Z_][\\w.]*)\\}?\\}?\\s*(==|!=|>=|<=|>|<)\\s*(.+?)\\s*$");

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
        output.put("totalIterations", results.size());
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

        Matcher matcher = CONDITION_PATTERN.matcher(condition);
        if (!matcher.matches()) {
            log.warn("Unsupported loop condition format: {}", condition);
            return false;
        }

        String key = matcher.group(2);
        String operator = matcher.group(3);
        String expectedRaw = matcher.group(4).trim();

        Object actual = context.get(key);
        if (actual == null) {
            return false;
        }

        Object expected = parseExpectedValue(expectedRaw);
        return compare(actual, expected, operator);
    }

    private Object parseExpectedValue(String raw) {
        if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
            return raw.substring(1, raw.length() - 1);
        }
        if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
            return Boolean.parseBoolean(raw);
        }
        try {
            if (raw.contains(".")) {
                return Double.parseDouble(raw);
            }
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }

    private boolean compare(Object actual, Object expected, String operator) {
        if (actual instanceof Number && expected instanceof Number) {
            double a = ((Number) actual).doubleValue();
            double b = ((Number) expected).doubleValue();
            return switch (operator) {
                case "==" -> Double.compare(a, b) == 0;
                case "!=" -> Double.compare(a, b) != 0;
                case ">" -> a > b;
                case "<" -> a < b;
                case ">=" -> a >= b;
                case "<=" -> a <= b;
                default -> false;
            };
        }

        if (actual instanceof Boolean && expected instanceof Boolean) {
            boolean a = (Boolean) actual;
            boolean b = (Boolean) expected;
            return switch (operator) {
                case "==" -> a == b;
                case "!=" -> a != b;
                default -> false;
            };
        }

        String a = String.valueOf(actual);
        String b = String.valueOf(expected);
        int cmp = a.compareTo(b);
        return switch (operator) {
            case "==" -> a.equals(b);
            case "!=" -> !a.equals(b);
            case ">" -> cmp > 0;
            case "<" -> cmp < 0;
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            default -> false;
        };
    }
}
