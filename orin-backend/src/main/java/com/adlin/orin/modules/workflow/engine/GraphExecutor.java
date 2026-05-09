package com.adlin.orin.modules.workflow.engine;

import com.adlin.orin.modules.trace.interceptor.SkillTraceInterceptor;
import com.adlin.orin.modules.trace.service.TraceService;
import com.adlin.orin.modules.workflow.engine.handler.NodeHandler;
import com.adlin.orin.modules.workflow.engine.handler.NodeExecutionResult;
import com.adlin.orin.common.exception.WorkflowExecutionException;
import com.adlin.orin.modules.workflow.service.WorkflowEventPublisher;
import com.adlin.orin.modules.observability.service.LangfuseObservabilityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Enhanced Graph Execution Engine.
 * Supports:
 * 1. Async parallel execution
 * 2. True branching (If/Else) with handle awareness
 * 3. State propagation (SKIPPED nodes)
 */
@Slf4j
@Component
public class GraphExecutor {

    private final Map<String, NodeHandler> nodeHandlers;
    private final WorkflowEventPublisher eventPublisher;
    private final TraceService traceService;
    private final LangfuseObservabilityService langfuseService;
    private final Executor taskExecutor;

    private static final long DEFAULT_TIMEOUT_SECONDS = 300L;
    private static final Map<String, String> NODE_HANDLER_BY_TYPE = Map.ofEntries(
            Map.entry("start", "startNodeHandler"),
            Map.entry("end", "endNodeHandler"),
            Map.entry("answer", "answerNodeHandler"),
            Map.entry("llm", "llmNodeHandler"),
            Map.entry("agent", "agentNodeHandler"),
            Map.entry("code", "codeNodeHandler"),
            Map.entry("if-else", "ifElseNodeHandler"),
            Map.entry("if_else", "ifElseNodeHandler"),
            Map.entry("knowledge-retrieval", "knowledgeNodeHandler"),
            Map.entry("knowledge_retrieval", "knowledgeNodeHandler"),
            Map.entry("tool", "skillNodeHandler"),
            Map.entry("http_request", "httpRequestNodeHandler"),
            Map.entry("iteration", "iterationNodeHandler"),
            Map.entry("loop", "loopNodeHandler"),
            Map.entry("variable_assigner", "variableAssignerNodeHandler"),
            Map.entry("variable-assigner", "variableAssignerNodeHandler"),
            Map.entry("skill", "skillNodeHandler"));

    /**
     * Compatibility only for older tests/callers. New production calls must pass
     * instanceId into executeGraph so singleton executor state cannot leak across
     * concurrent workflow instances.
     */
    @Deprecated
    private volatile Long compatibilityInstanceId;

    @Autowired
    public GraphExecutor(
            Map<String, NodeHandler> nodeHandlers,
            WorkflowEventPublisher eventPublisher,
            TraceService traceService,
            LangfuseObservabilityService langfuseService,
            @Qualifier("taskExecutor") Executor taskExecutor) {
        this.nodeHandlers = nodeHandlers;
        this.eventPublisher = eventPublisher;
        this.traceService = traceService;
        this.langfuseService = langfuseService;
        this.taskExecutor = taskExecutor;
    }

    public GraphExecutor(
            Map<String, NodeHandler> nodeHandlers,
            WorkflowEventPublisher eventPublisher,
            TraceService traceService,
            LangfuseObservabilityService langfuseService) {
        this(nodeHandlers, eventPublisher, traceService, langfuseService, ForkJoinPool.commonPool());
    }

    @Deprecated
    public void setInstanceId(Long instanceId) {
        this.compatibilityInstanceId = instanceId;
    }

    /**
     * Executes the graph asynchronously.
     */
    public Map<String, Object> executeGraph(Map<String, Object> graphDefinition, Map<String, Object> initialContext) {
        return executeGraph(graphDefinition, initialContext, compatibilityInstanceId, DEFAULT_TIMEOUT_SECONDS);
    }

    public Map<String, Object> executeGraph(
            Map<String, Object> graphDefinition,
            Map<String, Object> initialContext,
            Long instanceId,
            long timeoutSeconds) {
        GraphExecutionContext executionContext = new GraphExecutionContext(
                instanceId,
                timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS,
                SkillTraceInterceptor.TraceContext.getTraceId());
        log.info("Starting Async Graph Execution for instanceId={}", executionContext.instanceId());

        if (executionContext.instanceId() != null) {
            eventPublisher.publishWorkflowStarted(executionContext.instanceId());
        }

        // 1. Parsing and Initialization
        GraphParts graphParts = parseAndValidateGraph(graphDefinition);
        List<Map<String, Object>> nodes = graphParts.nodes();
        List<Map<String, Object>> edges = graphParts.edges();

        // Shared Context (Thread-Safe)
        Map<String, Object> globalContext = new ConcurrentHashMap<>(initialContext);

        // Node States (Thread-Safe)
        Map<String, NodeExecutionStatus> nodeStates = new ConcurrentHashMap<>();
        nodes.forEach(n -> nodeStates.put((String) n.get("id"), NodeExecutionStatus.PENDING));

        // Build Index for fast lookup
        Map<String, Map<String, Object>> nodeMap = nodes.stream()
                .collect(Collectors.toMap(n -> (String) n.get("id"), n -> n));

        // Build Dependency Graph (Downstream)
        Map<String, List<Map<String, Object>>> adjacencyList = new HashMap<>(); // Source -> [Edges]
        // Build Reverse Dependency Graph (Upstream)
        Map<String, List<Map<String, Object>>> reverseAdjacencyList = new HashMap<>(); // Target -> [Edges]

        for (Map<String, Object> edge : edges) {
            String source = (String) edge.get("source");
            String target = (String) edge.get("target");
            adjacencyList.computeIfAbsent(source, k -> new ArrayList<>()).add(edge);
            reverseAdjacencyList.computeIfAbsent(target, k -> new ArrayList<>()).add(edge);
        }

        // Futures Map to track node completion
        Map<String, CompletableFuture<NodeExecutionResult>> nodeFutures = new ConcurrentHashMap<>();

        // Context propagation

        // 3. Build and Trigger Futures
        try {
            // We use a "Construction" approach: create futures for all nodes, linked by
            // dependencies
            for (Map<String, Object> node : nodes) {
                String nodeId = (String) node.get("id");

                // Recursive creation with memoization (via nodeFutures check)
                createNodeFuture(nodeId, nodeMap, reverseAdjacencyList, nodeFutures, nodeStates, globalContext,
                        executionContext);
            }

            // Wait for all "Leaf" nodes or End nodes to complete?
            // Actually, we should wait for all logical End nodes.
            // Or simpler: Wait for ALL node futures to complete (SKIPPED ones also
            // complete).
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    nodeFutures.values().toArray(new CompletableFuture[0]));

            // Block until done (or timeout)
            // In a real reactive system, we wouldn't block, but here the method signature
            // is sync.
            allFutures.get(executionContext.timeoutSeconds(), TimeUnit.SECONDS);

            log.info("Graph execution finished.");
            if (executionContext.instanceId() != null) {
                eventPublisher.publishWorkflowCompleted(executionContext.instanceId());
            }

        } catch (Exception e) {
            log.error("Graph execution failed", e);
            if (executionContext.instanceId() != null) {
                eventPublisher.publishWorkflowFailed(executionContext.instanceId(), e.getMessage());
            }
            throw new WorkflowExecutionException("Graph execution failed", e);
        }

        Map<String, Object> finalResult = new LinkedHashMap<>();
        finalResult.put("success", true);
        Map<String, Object> outputs = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            String type = String.valueOf(node.get("type")).toLowerCase(Locale.ROOT);
            if (!"end".equals(type) && !"answer".equals(type)) {
                continue;
            }
            String nodeId = (String) node.get("id");
            NodeExecutionResult terminalResult = nodeFutures.get(nodeId).getNow(null);
            if (terminalResult != null && terminalResult.getOutputs() != null) {
                outputs.putAll(terminalResult.getOutputs());
            }
        }
        finalResult.put("outputs", outputs);
        finalResult.put("context", new HashMap<>(globalContext)); // Snapshot

        return finalResult;
    }

    /**
     * Recursive method to create a CompletableFuture for a node.
     */
    private CompletableFuture<NodeExecutionResult> createNodeFuture(
            String nodeId,
            Map<String, Map<String, Object>> nodeMap,
            Map<String, List<Map<String, Object>>> reverseAdjacencyList,
            Map<String, CompletableFuture<NodeExecutionResult>> nodeFutures,
            Map<String, NodeExecutionStatus> nodeStates,
            Map<String, Object> context,
            GraphExecutionContext executionContext
    ) {
        if (nodeFutures.containsKey(nodeId)) {
            return nodeFutures.get(nodeId);
        }

        // Get Input Dependencies
        List<Map<String, Object>> incomingEdges = reverseAdjacencyList.getOrDefault(nodeId, Collections.emptyList());

        List<CompletableFuture<NodeExecutionResult>> dependencyFutures = new ArrayList<>();
        for (Map<String, Object> edge : incomingEdges) {
            String source = (String) edge.get("source");
            dependencyFutures.add(createNodeFuture(source, nodeMap, reverseAdjacencyList, nodeFutures, nodeStates,
                    context, executionContext));
        }

        // Define the task for THIS node
        CompletableFuture<NodeExecutionResult> thisFuture = CompletableFuture.allOf(
                dependencyFutures.toArray(new CompletableFuture[0])).thenApplyAsync(v -> {
                    // --- INSIDE WORKER THREAD ---

                    // 1. Restore Trace Context
                    if (executionContext.traceId() != null) {
                        SkillTraceInterceptor.TraceContext.setTraceId(executionContext.traceId());
                        SkillTraceInterceptor.TraceContext.setInstanceId(executionContext.instanceId());
                        // StepId/Name set later
                    }

                    try {
                        // 2. Check Dependencies Status (Game of State Propagation)
                        boolean isSkipped = false;

                        // If NO incoming edges, it's a Start node (or floating), ALWAYS RUN.
                        if (!incomingEdges.isEmpty()) {
                            // Check if ALL incoming paths are valid.
                            // A path is valid if:
                            // a) Upstream node COMPLETED successfully AND
                            // b) Upstream node selected a handle that connects to THIS edge.

                            boolean hasActiveInput = false;
                            boolean allInputsSkipped = true;

                            for (Map<String, Object> edge : incomingEdges) {
                                String sourceId = (String) edge.get("source");
                                String sourceHandle = (String) edge.get("sourceHandle"); // The handle this edge comes
                                                                                         // FROM

                                // Get upstream result (already completed because of allOf)
                                NodeExecutionResult sourceResult = nodeFutures.get(sourceId).join();
                                NodeExecutionStatus sourceStatus = nodeStates.get(sourceId);

                                if (sourceStatus == NodeExecutionStatus.SKIPPED) {
                                    // This path is dead.
                                    continue;
                                }

                                if (sourceStatus == NodeExecutionStatus.COMPLETED) {
                                    allInputsSkipped = false;
                                    // Check handle matching
                                    String activeHandle = sourceResult.getSelectedHandle();
                                    // Dify/Orin logic: If sourceHandle is defined on Edge, it MUST match the Active
                                    // Handle.
                                    // If edge has no sourceHandle, it matches "source" (default).

                                    boolean handleMatch = true;
                                    if (sourceHandle != null && !sourceHandle.isEmpty()) {
                                        handleMatch = Objects.equals(sourceHandle, activeHandle);
                                    } else {
                                        // Default match? For IfElse, default might not match explicit handles.
                                        // If activeHandle is "if" and edge has no handle... ambiguous.
                                        // Assume strict matching if activeHandle is special.
                                    }

                                    if (handleMatch) {
                                        hasActiveInput = true;
                                    }
                                }
                            }

                            // Decision Logic:
                            // 1. If ALL upstream nodes were SKIPPED -> I am SKIPPED.
                            if (allInputsSkipped) {
                                isSkipped = true;
                            }
                            // 2. If valid inputs exist but NONE activated the path to me -> I am SKIPPED
                            // (Branch not taken).
                            else if (!hasActiveInput) {
                                isSkipped = true;
                            }
                        }

                        if (isSkipped) {
                            nodeStates.put(nodeId, NodeExecutionStatus.SKIPPED);
                            log.info("Node SKIPPED: {}", nodeId);
                            return new NodeExecutionResult(null, null, true);
                        }

                        // 3. Execute Node
                        nodeStates.put(nodeId, NodeExecutionStatus.RUNNING);
                        Map<String, Object> nodeDef = nodeMap.get(nodeId);

                        // Resolve Inputs (Variable Resolution) - Simplified for now
                        // Ideally we resolve {{node.var}} from 'context'.
                        // Since 'context' is concurrent updated, we should see upstream writes.

                        NodeHandler handler = getNodeHandler(nodeDef);

                        // Trace Start
                        SkillTraceInterceptor.TraceContext.setStepId(getNumericId(nodeId));
                        SkillTraceInterceptor.TraceContext
                                .setStepName((String) nodeDef.getOrDefault("title", nodeDef.get("type")));
                        String nodeType = (String) nodeDef.getOrDefault("type", "unknown");
                        String nodeTitle = (String) nodeDef.getOrDefault("title", nodeType);

                        // Record trace start to local workflow_traces table
                        com.adlin.orin.modules.trace.entity.WorkflowTraceEntity traceEntity = null;
                        if (traceService != null && executionContext.traceId() != null) {
                            try {
                                traceEntity = traceService.startTrace(
                                        executionContext.traceId(),
                                        executionContext.instanceId(),
                                        getNumericId(nodeId),
                                        nodeTitle,
                                        null, // skillId not available at this level
                                        nodeType,
                                        new HashMap<>() // inputData
                                );
                            } catch (Exception ex) {
                                log.warn("Failed to start trace for node {}: {}", nodeId, ex.getMessage());
                            }
                        }
                        eventPublisher.publishNodeStarted(executionContext.instanceId(), nodeId, nodeType);

                        long nodeStartTime = System.currentTimeMillis();
                        NodeExecutionResult result = null;
                        try {
                            result = handler.execute(
                                    nodeDef.get("data") != null ? (Map<String, Object>) nodeDef.get("data")
                                            : Collections.emptyMap(),
                                    context);
                        } catch (Exception handlerEx) {
                            // Record failed trace before rethrowing
                            if (traceEntity != null) {
                                try {
                                    Map<String, Object> errorDetails = new HashMap<>();
                                    errorDetails.put("exception", handlerEx.getClass().getName());
                                    errorDetails.put("stackTrace", handlerEx.getMessage());
                                    traceService.failTrace(traceEntity.getId(), "HANDLER_EXECUTION_ERROR",
                                            handlerEx.getMessage(), errorDetails);
                                } catch (Exception ex) {
                                    log.warn("Failed to record failed trace for node {}: {}", nodeId, ex.getMessage());
                                }
                            }
                            throw handlerEx;
                        }

                        long nodeDurationMs = System.currentTimeMillis() - nodeStartTime;

                        // Record node execution to Langfuse as span
                        recordNodeSpan(executionContext.traceId(), nodeId, nodeType, nodeTitle, nodeStartTime, nodeDurationMs, result);

                        // Complete local trace record
                        if (traceEntity != null) {
                            try {
                                if (result.isSuccess()) {
                                    traceService.completeTrace(traceEntity.getId(), result.getOutputs());
                                } else {
                                    Map<String, Object> errorDetails = new HashMap<>();
                                    String errorMsg = result.getOutputs() != null
                                            ? (String) result.getOutputs().getOrDefault("error", result.getOutputs().getOrDefault("errorMessage", "Unknown error"))
                                            : "Unknown error";
                                    errorDetails.put("error", errorMsg);
                                    traceService.failTrace(traceEntity.getId(), "NODE_EXECUTION_ERROR",
                                            errorMsg, errorDetails);
                                }
                            } catch (Exception ex) {
                                log.warn("Failed to complete trace for node {}: {}", nodeId, ex.getMessage());
                            }
                        }

                        // Update Context
                        if (result.isSuccess() && result.getOutputs() != null) {
                            // Write to context under nodeId
                            context.put(nodeId, result.getOutputs());
                        }

                        nodeStates.put(nodeId, NodeExecutionStatus.COMPLETED);
                        eventPublisher.publishNodeCompleted(executionContext.instanceId(), nodeId,
                                (String) nodeDef.getOrDefault("title", ""), result.getOutputs());

                        return result;

                    } catch (Exception e) {
                        log.error("Node execution failed: " + nodeId, e);
                        nodeStates.put(nodeId, NodeExecutionStatus.FAILED);
                        Map<String, Object> nodeDef = nodeMap.getOrDefault(nodeId, Collections.emptyMap());
                        eventPublisher.publishNodeFailed(
                                executionContext.instanceId(),
                                nodeId,
                                (String) nodeDef.getOrDefault("title", nodeDef.getOrDefault("type", nodeId)),
                                e.getMessage());
                        throw new CompletionException(e);
                    } finally {
                        SkillTraceInterceptor.TraceContext.clear();
                    }
                }, taskExecutor);

        nodeFutures.put(nodeId, thisFuture);
        return thisFuture;
    }

    public void validateGraphDefinition(Map<String, Object> graphDefinition) {
        parseAndValidateGraph(graphDefinition);
    }

    @SuppressWarnings("unchecked")
    private GraphParts parseAndValidateGraph(Map<String, Object> graphDefinition) {
        if (graphDefinition == null) {
            throw new IllegalArgumentException("Graph definition is required");
        }
        Object rawNodes = graphDefinition.get("nodes");
        if (!(rawNodes instanceof List<?> rawNodeList) || rawNodeList.isEmpty()) {
            throw new IllegalArgumentException("Graph must contain at least one node");
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Object rawNode : rawNodeList) {
            if (!(rawNode instanceof Map<?, ?> rawMap)) {
                throw new IllegalArgumentException("Graph node must be an object");
            }
            Map<String, Object> node = new HashMap<>();
            rawMap.forEach((key, value) -> node.put(String.valueOf(key), value));
            nodes.add(node);
        }

        Object rawEdges = graphDefinition.get("edges");
        List<Map<String, Object>> edges = new ArrayList<>();
        if (rawEdges instanceof List<?> rawEdgeList) {
            for (Object rawEdge : rawEdgeList) {
                if (!(rawEdge instanceof Map<?, ?> rawMap)) {
                    throw new IllegalArgumentException("Graph edge must be an object");
                }
                Map<String, Object> edge = new HashMap<>();
                rawMap.forEach((key, value) -> edge.put(String.valueOf(key), value));
                edges.add(edge);
            }
        }

        validateGraph(nodes, edges);
        return new GraphParts(nodes, edges);
    }

    private void validateGraph(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Set<String> nodeIds = new HashSet<>();
        for (Map<String, Object> node : nodes) {
            Object rawId = node.get("id");
            Object rawType = node.get("type");
            if (!(rawId instanceof String id) || id.isBlank()) {
                throw new IllegalArgumentException("Graph node id is required");
            }
            if (!nodeIds.add(id)) {
                throw new IllegalArgumentException("Duplicate graph node id: " + id);
            }
            if (!(rawType instanceof String type) || type.isBlank()) {
                throw new IllegalArgumentException("Graph node type is required: " + id);
            }
            getNodeHandler(node);
        }

        Map<String, List<String>> adjacency = new HashMap<>();
        Set<String> targets = new HashSet<>();
        for (Map<String, Object> edge : edges) {
            String source = (String) edge.get("source");
            String target = (String) edge.get("target");
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("Graph edge source is required");
            }
            if (target == null || target.isBlank()) {
                throw new IllegalArgumentException("Graph edge target is required");
            }
            if (!nodeIds.contains(source)) {
                throw new IllegalArgumentException("Graph edge source not found: " + source);
            }
            if (!nodeIds.contains(target)) {
                throw new IllegalArgumentException("Graph edge target not found: " + target);
            }
            adjacency.computeIfAbsent(source, ignored -> new ArrayList<>()).add(target);
            targets.add(target);
        }

        boolean hasStartNode = nodes.stream().anyMatch(n -> "start".equalsIgnoreCase((String) n.get("type")));
        boolean hasEntryNode = nodes.stream().map(n -> (String) n.get("id")).anyMatch(id -> !targets.contains(id));
        if (!hasStartNode && !hasEntryNode) {
            throw new IllegalArgumentException("Graph must contain a start node or at least one entry node");
        }

        boolean hasEndNode = nodes.stream().anyMatch(n -> "end".equalsIgnoreCase((String) n.get("type")));
        boolean hasLeafNode = nodes.stream()
                .map(n -> (String) n.get("id"))
                .anyMatch(id -> !adjacency.containsKey(id) || adjacency.get(id).isEmpty());
        if (!hasEndNode && !hasLeafNode) {
            throw new IllegalArgumentException("Graph must contain an end node or at least one leaf node");
        }

        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String nodeId : nodeIds) {
            if (hasCycle(nodeId, adjacency, visited, visiting)) {
                throw new IllegalArgumentException("Graph contains a cycle involving node: " + nodeId);
            }
        }
    }

    private boolean hasCycle(
            String nodeId,
            Map<String, List<String>> adjacency,
            Set<String> visited,
            Set<String> visiting) {
        if (visiting.contains(nodeId)) {
            return true;
        }
        if (visited.contains(nodeId)) {
            return false;
        }
        visiting.add(nodeId);
        for (String next : adjacency.getOrDefault(nodeId, Collections.emptyList())) {
            if (hasCycle(next, adjacency, visited, visiting)) {
                return true;
            }
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
        return false;
    }

    private NodeHandler getNodeHandler(Map<String, Object> node) {
        String type = (String) node.get("type");
        String beanName = NODE_HANDLER_BY_TYPE.getOrDefault(type, type.toLowerCase() + "NodeHandler");

        NodeHandler handler = nodeHandlers.get(beanName);
        if (handler == null) {
            throw new IllegalArgumentException("No node handler found for type: " + type);
        }
        return handler;
    }

    public Set<String> getSupportedNodeTypes() {
        return NODE_HANDLER_BY_TYPE.entrySet().stream()
                .filter(entry -> nodeHandlers.containsKey(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private Long getNumericId(String nodeId) {
        try {
            return Long.parseLong(nodeId.replaceAll("\\D+", ""));
        } catch (Exception e) {
            return (long) nodeId.hashCode();
        }
    }

    /**
     * 记录工作流节点执行到 Langfuse（作为 Span）
     */
    private void recordNodeSpan(String traceId, String nodeId, String nodeType, String nodeTitle,
                                long startTime, long durationMs, NodeExecutionResult result) {
        if (traceId == null || !langfuseService.isEnabled()) {
            return;
        }

        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("nodeId", nodeId);
            metadata.put("nodeType", nodeType);
            metadata.put("success", result.isSuccess());

            if (result.getOutputs() != null) {
                metadata.put("outputKeys", result.getOutputs().keySet());
            }

            langfuseService.recordToolExecution(
                    traceId,
                    nodeTitle,
                    "", // input
                    result.isSuccess() ? "completed" : "failed",
                    startTime,
                    startTime + durationMs
            );

            log.debug("Recorded workflow node to Langfuse: traceId={}, node={}, duration={}ms",
                    traceId, nodeTitle, durationMs);

        } catch (Exception e) {
            // Langfuse 错误降级，不影响主流程
            log.warn("Failed to record Langfuse node span: {}", e.getMessage());
        }
    }

    private record GraphExecutionContext(Long instanceId, long timeoutSeconds, String traceId) {
    }

    private record GraphParts(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
    }
}
