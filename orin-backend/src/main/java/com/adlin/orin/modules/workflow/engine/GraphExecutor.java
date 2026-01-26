package com.adlin.orin.modules.workflow.engine;

import com.adlin.orin.modules.agent.service.AgentExecutor;
import com.adlin.orin.modules.skill.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 图结构工作流执行器
 * 支持基于节点和边的工作流执行，实现 Dify 风格的可视化编排
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphExecutor {

    private final AgentExecutor agentExecutor;
    private final SkillService skillService;
    private final com.adlin.orin.modules.workflow.service.WorkflowEventPublisher eventPublisher;

    // Instance ID for event publishing (set before execution)
    private Long currentInstanceId;

    /**
     * 设置当前执行的实例 ID（用于事件发布）
     */
    public void setInstanceId(Long instanceId) {
        this.currentInstanceId = instanceId;
    }

    /**
     * 执行图结构工作流
     *
     * @param graphDefinition 包含 nodes 和 edges 的图定义
     * @param context         执行上下文
     * @return 执行结果
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeGraph(Map<String, Object> graphDefinition, Map<String, Object> context) {
        log.info("Executing graph-based workflow");

        // Emit workflow started event
        if (currentInstanceId != null) {
            eventPublisher.publishWorkflowStarted(currentInstanceId);
        }

        // 1. 解析节点和边
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graphDefinition.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graphDefinition.get("edges");

        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Graph must contain at least one node");
        }

        // 2. 构建邻接表（包含条件边）
        Map<String, List<Map<String, Object>>> adjacencyListWithConditions = buildAdjacencyListWithConditions(edges);

        // 3. 构建简单邻接表用于拓扑排序
        Map<String, List<String>> adjacencyList = buildAdjacencyList(edges);

        // 4. 拓扑排序获取执行顺序
        List<String> executionOrder = topologicalSort(nodes, adjacencyList);

        // 5. 按顺序执行节点（考虑条件边）
        Map<String, Object> nodeOutputs = new HashMap<>();
        Map<String, Object> finalResult = new HashMap<>();
        Set<String> executedNodes = new HashSet<>();

        for (String nodeId : executionOrder) {
            // 检查是否应该执行此节点（基于条件边）
            if (!shouldExecuteNode(nodeId, executedNodes, adjacencyListWithConditions, context, nodeOutputs)) {
                log.info("Skipping node due to conditional edge: {}", nodeId);
                continue;
            }

            Map<String, Object> node = findNodeById(nodes, nodeId);
            if (node == null) {
                log.warn("Node not found: {}", nodeId);
                continue;
            }

            String nodeType = (String) node.get("type");

            // 跳过 start 和 end 节点
            if ("start".equals(nodeType) || "end".equals(nodeType)) {
                log.debug("Skipping {} node: {}", nodeType, nodeId);
                executedNodes.add(nodeId);
                continue;
            }

            // 执行节点
            Map<String, Object> nodeResult = executeNode(node, context, nodeOutputs);
            nodeOutputs.put(nodeId, nodeResult);
            executedNodes.add(nodeId);

            // 更新上下文
            context.putAll(nodeResult);
        }

        finalResult.put("success", true);
        finalResult.put("nodeOutputs", nodeOutputs);
        finalResult.put("context", context);

        return finalResult;
    }

    /**
     * 判断节点是否应该执行（基于条件边）
     */
    @SuppressWarnings("unchecked")
    private boolean shouldExecuteNode(
            String nodeId,
            Set<String> executedNodes,
            Map<String, List<Map<String, Object>>> adjacencyListWithConditions,
            Map<String, Object> context,
            Map<String, Object> nodeOutputs) {

        // 查找所有指向此节点的边
        for (Map.Entry<String, List<Map<String, Object>>> entry : adjacencyListWithConditions.entrySet()) {
            String sourceNode = entry.getKey();
            List<Map<String, Object>> targetEdges = entry.getValue();

            for (Map<String, Object> edge : targetEdges) {
                String target = (String) edge.get("target");
                if (nodeId.equals(target)) {
                    // 如果源节点已执行
                    if (executedNodes.contains(sourceNode)) {
                        // 检查边的条件
                        String condition = (String) edge.get("condition");
                        if (condition != null && !condition.isEmpty()) {
                            // 评估条件
                            boolean conditionMet = evaluateEdgeCondition(condition, context, nodeOutputs);
                            if (!conditionMet) {
                                return false; // 条件不满足，不执行
                            }
                        }
                        // 条件满足或无条件，可以执行
                        return true;
                    }
                }
            }
        }

        // 如果没有入边，或者是起始节点，默认执行
        return true;
    }

    /**
     * 评估边条件
     */
    private boolean evaluateEdgeCondition(String condition, Map<String, Object> context,
            Map<String, Object> nodeOutputs) {
        // 支持简单的条件表达式，如 "${node_1.success} == true"
        if (condition.contains("==")) {
            String[] parts = condition.split("==");
            if (parts.length == 2) {
                String left = parts[0].trim();
                String right = parts[1].trim();

                Object leftValue = resolveConditionValue(left, context, nodeOutputs);
                Object rightValue = resolveConditionValue(right, context, nodeOutputs);

                return Objects.equals(leftValue, rightValue);
            }
        }

        // 默认解析为布尔表达式
        Object value = resolveConditionValue(condition, context, nodeOutputs);
        return Boolean.TRUE.equals(value);
    }

    /**
     * 解析条件值
     */
    private Object resolveConditionValue(String value, Map<String, Object> context, Map<String, Object> nodeOutputs) {
        value = value.trim();

        // 处理 ${} 表达式
        if (value.startsWith("${") && value.endsWith("}")) {
            String expression = value.substring(2, value.length() - 1);
            return resolveExpression(expression, context, nodeOutputs);
        }

        // 处理布尔值
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }

        // 处理数字
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // 不是数字，返回字符串
            return value.replace("\"", "").replace("'", "");
        }
    }

    /**
     * 构建带条件的邻接表
     */
    private Map<String, List<Map<String, Object>>> buildAdjacencyListWithConditions(List<Map<String, Object>> edges) {
        Map<String, List<Map<String, Object>>> adjacencyList = new HashMap<>();

        if (edges != null) {
            for (Map<String, Object> edge : edges) {
                String source = (String) edge.get("source");
                adjacencyList.computeIfAbsent(source, k -> new ArrayList<>()).add(edge);
            }
        }

        return adjacencyList;
    }

    /**
     * 执行单个节点
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeNode(
            Map<String, Object> node,
            Map<String, Object> context,
            Map<String, Object> nodeOutputs) {

        String nodeId = (String) node.get("id");
        String nodeType = (String) node.get("type");
        Map<String, Object> nodeData = (Map<String, Object>) node.get("data");

        log.info("Executing node: {} (type: {})", nodeId, nodeType);

        try {
            Map<String, Object> inputs = resolveNodeInputs(nodeData, context, nodeOutputs);

            switch (nodeType) {
                case "agent":
                    Long agentId = getLongValue(nodeData, "agentId");
                    if (agentId == null) {
                        throw new IllegalStateException("Agent node requires agentId: " + nodeId);
                    }
                    return agentExecutor.executeAgent(agentId, inputs);

                case "skill":
                    Long skillId = getLongValue(nodeData, "skillId");
                    if (skillId == null) {
                        throw new IllegalStateException("Skill node requires skillId: " + nodeId);
                    }
                    return skillService.executeSkill(skillId, inputs);

                case "llm":
                    // Support for Dify 'llm' nodes
                    log.info("Executing LLM node: {}", nodeId);
                    // In a real implementation, this would call ModelService using inputs which
                    // contains 'prompt', 'model', etc.
                    Map<String, Object> llmResult = new HashMap<>();
                    llmResult.put("text", "LLM Output (Simulation): Input was " + inputs);
                    // If prompt is in inputs, echo it
                    if (inputs.containsKey("prompt_template")) {
                        // Dify usually puts prompt in data, and we mapped data's inputMapping.
                        // But Dify config is in data.
                        // We can access nodeData here if needed, but inputs should be resolved.
                    }
                    return llmResult;

                case "condition":
                    // 条件节点：评估条件并返回结果
                    return evaluateCondition(nodeData, context, nodeOutputs);

                default:
                    log.warn("Unknown node type: {}", nodeType);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("skipped", true);
                    return result;
            }

        } catch (Exception e) {
            log.error("Node execution failed: {}, error={}", nodeId, e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    /**
     * 解析节点输入参数
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveNodeInputs(
            Map<String, Object> nodeData,
            Map<String, Object> context,
            Map<String, Object> nodeOutputs) {

        if (nodeData == null) {
            return new HashMap<>();
        }

        Map<String, Object> inputMapping = (Map<String, Object>) nodeData.get("inputMapping");
        if (inputMapping == null) {
            return new HashMap<>();
        }

        Map<String, Object> resolved = new HashMap<>();
        for (Map.Entry<String, Object> entry : inputMapping.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                String strValue = (String) value;
                // 支持 ${nodeId.field} 语法
                if (strValue.startsWith("${") && strValue.endsWith("}")) {
                    String expression = strValue.substring(2, strValue.length() - 1);
                    Object resolvedValue = resolveExpression(expression, context, nodeOutputs);
                    resolved.put(key, resolvedValue);
                } else {
                    resolved.put(key, value);
                }
            } else {
                resolved.put(key, value);
            }
        }

        return resolved;
    }

    /**
     * 解析表达式
     */
    @SuppressWarnings("unchecked")
    private Object resolveExpression(String expression, Map<String, Object> context, Map<String, Object> nodeOutputs) {
        if (expression.contains(".")) {
            String[] parts = expression.split("\\.", 2);
            String nodeId = parts[0];
            String field = parts[1];

            Map<String, Object> nodeOutput = (Map<String, Object>) nodeOutputs.get(nodeId);
            if (nodeOutput != null) {
                return nodeOutput.get(field);
            }
        }

        // 从上下文获取
        return context.get(expression);
    }

    /**
     * 评估条件节点
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> evaluateCondition(
            Map<String, Object> nodeData,
            Map<String, Object> context,
            Map<String, Object> nodeOutputs) {

        String condition = (String) nodeData.get("condition");
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("conditionMet", evaluateSimpleCondition(condition, context, nodeOutputs));
        return result;
    }

    /**
     * 简单条件评估（可以后续扩展为 SpEL）
     */
    private boolean evaluateSimpleCondition(String condition, Map<String, Object> context,
            Map<String, Object> nodeOutputs) {
        // 简单实现：检查某个值是否为 true
        if (condition == null) {
            return true;
        }

        Object value = resolveExpression(condition, context, nodeOutputs);
        return Boolean.TRUE.equals(value);
    }

    /**
     * 构建邻接表
     */
    private Map<String, List<String>> buildAdjacencyList(List<Map<String, Object>> edges) {
        Map<String, List<String>> adjacencyList = new HashMap<>();

        if (edges != null) {
            for (Map<String, Object> edge : edges) {
                String source = (String) edge.get("source");
                String target = (String) edge.get("target");

                adjacencyList.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
            }
        }

        return adjacencyList;
    }

    /**
     * 拓扑排序
     */
    private List<String> topologicalSort(List<Map<String, Object>> nodes, Map<String, List<String>> adjacencyList) {
        // 计算入度
        Map<String, Integer> inDegree = new HashMap<>();
        for (Map<String, Object> node : nodes) {
            String nodeId = (String) node.get("id");
            inDegree.put(nodeId, 0);
        }

        for (List<String> targets : adjacencyList.values()) {
            for (String target : targets) {
                inDegree.put(target, inDegree.getOrDefault(target, 0) + 1);
            }
        }

        // Kahn 算法
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);

            List<String> neighbors = adjacencyList.getOrDefault(current, Collections.emptyList());
            for (String neighbor : neighbors) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        // 检测循环
        if (result.size() != nodes.size()) {
            throw new IllegalStateException("Cycle detected in workflow graph");
        }

        return result;
    }

    /**
     * 根据 ID 查找节点
     */
    private Map<String, Object> findNodeById(List<Map<String, Object>> nodes, String nodeId) {
        return nodes.stream()
                .filter(node -> nodeId.equals(node.get("id")))
                .findFirst()
                .orElse(null);
    }

    /**
     * 安全获取 Long 值
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
