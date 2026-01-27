package com.adlin.orin.modules.workflow.engine;

import com.adlin.orin.modules.agent.service.AgentExecutor;
import com.adlin.orin.modules.knowledge.service.RetrievalService;
import com.adlin.orin.modules.apikey.service.ProviderKeyService;
import com.adlin.orin.modules.apikey.entity.ExternalProviderKey;
import com.adlin.orin.modules.model.entity.ModelMetadata;
import com.adlin.orin.modules.model.service.DeepSeekIntegrationService;
import com.adlin.orin.modules.model.service.ModelManageService;
import com.adlin.orin.modules.skill.service.SkillService;
import com.adlin.orin.modules.trace.service.TraceService;
import com.adlin.orin.exception.WorkflowExecutionException;
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
    private final RetrievalService retrievalService;
    private final DeepSeekIntegrationService deepSeekService;
    private final ModelManageService modelManageService;
    private final ProviderKeyService providerKeyService;
    private final TraceService traceService;

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

        // 处理 {{}} 表达式
        if (value.startsWith("{{") && value.endsWith("}}")) {
            String expression = value.substring(2, value.length() - 2);
            return resolveVariable(expression, context, nodeOutputs);
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
        String nodeTitle = (String) nodeData.getOrDefault("title", nodeType);

        log.info("Executing node: {} ({})", nodeTitle, nodeId);

        // 1. 准备 Tracing 信息
        String traceId = (String) context.getOrDefault("__traceId", UUID.randomUUID().toString());
        Long instanceId = (Long) context.getOrDefault("__instanceId", currentInstanceId);

        // 尝试从 ID 解析数字部分作为 Step ID，如果失败则用 Hash
        Long stepId = null;
        try {
            // 假设 nodeId 格式可能包含数字
            stepId = Long.parseLong(nodeId.replaceAll("\\D+", ""));
        } catch (Exception e) {
            stepId = (long) nodeId.hashCode();
        }

        com.adlin.orin.modules.trace.entity.WorkflowTraceEntity traceEntity = null;

        try {
            // 2. 解析输入
            Map<String, Object> inputs = resolveNodeInputs(nodeData, context, nodeOutputs);

            // 3. 开始 Trace
            if (traceService != null) {
                traceEntity = traceService.startTrace(
                        traceId,
                        instanceId,
                        stepId,
                        nodeTitle,
                        null, null, // skill info
                        inputs);
            }

            // 发布 Node Started 事件
            eventPublisher.publishNodeStarted(instanceId, nodeId, nodeType);

            Map<String, Object> output = new HashMap<>();

            // 4. 执行节点逻辑
            switch (nodeType.toLowerCase()) {
                case "start":
                    output.put("status", "completed");
                    break;

                case "end":
                    // end 节点通常收集输入作为最终输出
                    output.putAll(inputs);
                    break;

                case "agent":
                    Long agentId = getLongValue(nodeData, "agentId");
                    if (agentId == null)
                        throw new IllegalArgumentException("Agent ID required");
                    output = agentExecutor.executeAgent(agentId, inputs);
                    break;

                case "skill":
                    Long skillId = getLongValue(nodeData, "skillId");
                    if (skillId == null)
                        throw new IllegalArgumentException("Skill ID required");
                    output = skillService.executeSkill(skillId, inputs);
                    break;

                case "llm":
                    output = executeLLLNode(inputs);
                    break;

                case "knowledge":
                    output = executeKnowledgeNode(inputs);
                    break;

                case "condition":
                    output = evaluateCondition(nodeData, context, nodeOutputs);
                    break;

                default:
                    log.warn("Unknown node type: {}", nodeType);
                    output.put("status", "skipped");
            }

            // 5. 完成 Trace
            if (traceEntity != null) {
                traceService.completeTrace(traceEntity.getId(), output);
            }

            // 发布 Node Completed 事件
            eventPublisher.publishNodeCompleted(instanceId, nodeId, nodeTitle, output);

            return output;

        } catch (Exception e) {
            log.error("Node execution failed: {}", nodeId, e);

            // 6. 失败 Trace
            if (traceEntity != null) {
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("stackTrace", Arrays.toString(e.getStackTrace()));
                traceService.failTrace(traceEntity.getId(), "EXECUTION_ERROR", e.getMessage(), errorDetails);
            }

            // 发布 Node Failed 事件 (如果 WorkflowEventPublisher 支持)
            // eventPublisher.publishNodeFailed(instanceId, nodeId, e.getMessage());

            // 抛出运行时异常，中断流程或由上层捕获
            throw new WorkflowExecutionException("Node " + nodeId + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * 执行 LLM 节点逻辑
     */
    private Map<String, Object> executeLLLNode(Map<String, Object> inputs) {
        String modelName = (String) inputs.getOrDefault("model", "deepseek-chat");
        String prompt = (String) inputs.getOrDefault("prompt_template", ""); // Dify key
        if (prompt.isEmpty()) {
            prompt = (String) inputs.get("prompt"); // Fallback
        }

        // 查找模型配置
        ModelMetadata modelMeta = modelManageService.getAllModels().stream()
                .filter(m -> m.getName().equals(modelName) || m.getModelId().equals(modelName))
                .findFirst()
                .orElse(null);

        String responseText = "";

        if (modelMeta != null) {
            // 查找 Provider Key
            String providerName = modelMeta.getProvider();
            ExternalProviderKey providerKey = providerKeyService.getActiveKeys().stream()
                    .filter(k -> k.getProvider().equals(providerName))
                    .findFirst()
                    .orElse(null);

            if (providerKey != null && "deepseek".equalsIgnoreCase(providerName)) {
                // 调用 DeepSeek
                Optional<Object> response = deepSeekService.sendMessage(
                        providerKey.getBaseUrl(),
                        providerKey.getApiKey(),
                        modelMeta.getModelId(), // Pass the model ID (e.g. deepseek-chat)
                        prompt);

                if (response.isPresent()) {
                    Map<String, Object> respMap = (Map<String, Object>) response.get();
                    responseText = extractContentFromResponse(respMap);
                } else {
                    responseText = "Error: No response from LLM provider.";
                }
            } else {
                responseText = "Error: Provider key not found or unsupported provider (" + providerName + ")";
            }

        } else {
            // 模拟或未找到配置
            responseText = "Simulated LLM Output for [" + modelName + "]: " + prompt;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("text", responseText);
        result.put("output", responseText); // Dify sometimes uses output
        return result;
    }

    @SuppressWarnings("unchecked")
    private String extractContentFromResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }
        } catch (Exception e) {
            log.warn("Failed to parse LLM response", e);
        }
        return response.toString();
    }

    /**
     * 执行知识检索节点逻辑
     */
    private Map<String, Object> executeKnowledgeNode(Map<String, Object> inputs) {
        String query = (String) inputs.get("query");
        // dataset_ids 可能是一个 List 或者是逗号分隔字符串
        Object datasetIdsObj = inputs.get("dataset_ids");
        String kbId = "";

        if (datasetIdsObj instanceof List) {
            List<?> list = (List<?>) datasetIdsObj;
            if (!list.isEmpty())
                kbId = list.get(0).toString();
        } else if (datasetIdsObj instanceof String) {
            kbId = (String) datasetIdsObj;
        }

        if (query == null || kbId.isEmpty()) {
            throw new IllegalArgumentException("Knowledge node requires 'query' and 'dataset_ids'");
        }

        var results = retrievalService.hybridSearch(kbId, query, 4); // Default Top 4

        // 格式化输出
        List<Map<String, Object>> docList = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder();

        for (var res : results) {
            Map<String, Object> doc = new HashMap<>();
            doc.put("content", res.getContent());
            doc.put("score", res.getScore());
            doc.put("metadata", res.getMetadata());
            docList.add(doc);

            contextBuilder.append(res.getContent()).append("\n\n");
        }

        Map<String, Object> output = new HashMap<>();
        output.put("result", docList); // List output
        output.put("output", contextBuilder.toString()); // String context output for LLM
        return output;
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
                // 支持 {{nodeId.field}} 语法
                if (strValue.startsWith("{{") && strValue.endsWith("}}")) {
                    String variable = strValue.substring(2, strValue.length() - 2);
                    Object resolvedValue = resolveVariable(variable, context, nodeOutputs);
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
     * 解析变量 (支持 {{node.output}} 和 {{sys.query}})
     */
    private Object resolveVariable(String variable, Map<String, Object> context, Map<String, Object> nodeOutputs) {
        if (variable == null)
            return null;
        variable = variable.trim();

        // 1. 系统变量 (sys.xxx)
        if (variable.startsWith("sys.")) {
            String key = variable.substring(4); // remove "sys."
            return context.get(key);
        }

        // 2. 节点变量 (nodeId.field)
        if (variable.contains(".")) {
            String[] parts = variable.split("\\.", 2);
            String nodeId = parts[0];
            String field = parts[1];

            @SuppressWarnings("unchecked")
            Map<String, Object> nodeOutput = (Map<String, Object>) nodeOutputs.get(nodeId);
            if (nodeOutput != null) {
                // 如果是直接取 .output，尝试取 text 或 payload
                if ("output".equals(field)) {
                    if (nodeOutput.containsKey("text"))
                        return nodeOutput.get("text");
                    if (nodeOutput.containsKey("answer"))
                        return nodeOutput.get("answer");
                    return nodeOutput; // Fallback
                }
                return nodeOutput.get(field);
            }
        }

        // 3. Fallback: 直接从 context 查找 (兼容旧习)
        return context.get(variable);
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

        Object value = resolveVariable(condition, context, nodeOutputs);
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
