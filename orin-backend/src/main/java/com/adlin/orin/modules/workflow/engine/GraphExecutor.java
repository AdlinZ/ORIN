package com.adlin.orin.modules.workflow.engine;

import com.adlin.orin.modules.trace.interceptor.SkillTraceInterceptor;
import com.adlin.orin.modules.trace.service.TraceService;
import com.adlin.orin.modules.workflow.engine.handler.NodeHandler;
import com.adlin.orin.modules.workflow.engine.handler.NodeExecutionResult;
import com.adlin.orin.common.exception.WorkflowExecutionException;
import com.adlin.orin.modules.workflow.service.WorkflowEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class GraphExecutor {

    private final Map<String, NodeHandler> nodeHandlers;
    private final WorkflowEventPublisher eventPublisher;
    private final TraceService traceService;

    // Thread pool for async execution
    private final ExecutorService executor = Executors.newCachedThreadPool(); // Use Spring's TaskExecutor in production

    private Long currentInstanceId;

    public void setInstanceId(Long instanceId) {
        this.currentInstanceId = instanceId;
    }

    /**
     * Executes the graph asynchronously.
     */
    public Map<String, Object> executeGraph(Map<String, Object> graphDefinition, Map<String, Object> initialContext) {
        log.info("Starting Async Graph Execution for instanceId={}", currentInstanceId);

        if (currentInstanceId != null) {
            eventPublisher.publishWorkflowStarted(currentInstanceId);
        }

        // 1. Parsing and Initialization
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graphDefinition.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graphDefinition.get("edges");

        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Graph must contain at least one node");
        }

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

        // Capture initial Trace Context from current thread to propagate to async
        // threads
        String traceId = SkillTraceInterceptor.TraceContext.getTraceId();

        // 3. Build and Trigger Futures
        try {
            // We use a "Construction" approach: create futures for all nodes, linked by
            // dependencies
            for (Map<String, Object> node : nodes) {
                String nodeId = (String) node.get("id");

                // Recursive creation with memoization (via nodeFutures check)
                createNodeFuture(nodeId, nodeMap, reverseAdjacencyList, nodeFutures, nodeStates, globalContext,
                        traceId);
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
            allFutures.get(300, TimeUnit.SECONDS);

            log.info("Graph execution finished.");

        } catch (Exception e) {
            log.error("Graph execution failed", e);
            throw new WorkflowExecutionException("Graph execution failed", e);
        }

        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("success", true);
        finalResult.put("context", new HashMap<>(globalContext)); // Snapshot

        // Find output from 'End' node if exists
        Optional<String> endNodeId = nodes.stream()
                .filter(n -> "end".equalsIgnoreCase((String) n.get("type")))
                .map(n -> (String) n.get("id"))
                .findFirst();

        if (endNodeId.isPresent()) {
            NodeExecutionResult endResult = nodeFutures.get(endNodeId.get()).getNow(null);
            if (endResult != null && endResult.getOutputs() != null) {
                finalResult.putAll(endResult.getOutputs());
            }
        }

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
            String parentTraceId // passed for context propagation
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
                    context, parentTraceId));
        }

        // Define the task for THIS node
        CompletableFuture<NodeExecutionResult> thisFuture = CompletableFuture.allOf(
                dependencyFutures.toArray(new CompletableFuture[0])).thenApplyAsync(v -> {
                    // --- INSIDE WORKER THREAD ---

                    // 1. Restore Trace Context
                    if (parentTraceId != null) {
                        SkillTraceInterceptor.TraceContext.setTraceId(parentTraceId);
                        SkillTraceInterceptor.TraceContext.setInstanceId(currentInstanceId);
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
                        if (traceService != null) {
                            // In real imp, ensure we call startTrace here or inside handler
                        }
                        eventPublisher.publishNodeStarted(currentInstanceId, nodeId, (String) nodeDef.get("type"));

                        NodeExecutionResult result = handler.execute(
                                nodeDef.get("data") != null ? (Map<String, Object>) nodeDef.get("data")
                                        : Collections.emptyMap(),
                                context);

                        // Update Context
                        if (result.isSuccess() && result.getOutputs() != null) {
                            // We might want to namespace outputs: context.put(nodeId, result.getOutputs());
                            // Or Flatten: context.putAll(result.getOutputs());
                            // Dify typically puts it under `nodeId` key in a separate state, but flat
                            // context for vars.
                            // For V1, lets put in separate `nodeOutputs` map inside context?
                            // No, existing handlers expect flat or `inputs` map.
                            // Let's stick to: Context is global variables. Nodes write to Global? No,
                            // dangerous.
                            // Correct: Context contains "sys", "vars", and "nodeId" -> output map.

                            // Write to context under nodeId
                            context.put(nodeId, result.getOutputs());
                            // Also flatten for "Start" node or explicit variable assigners
                        }

                        nodeStates.put(nodeId, NodeExecutionStatus.COMPLETED);
                        eventPublisher.publishNodeCompleted(currentInstanceId, nodeId,
                                (String) nodeDef.getOrDefault("title", ""), result.getOutputs());

                        return result;

                    } catch (Exception e) {
                        log.error("Node execution failed: " + nodeId, e);
                        nodeStates.put(nodeId, NodeExecutionStatus.FAILED);
                        throw new CompletionException(e);
                    } finally {
                        SkillTraceInterceptor.TraceContext.clear();
                    }
                }, executor);

        nodeFutures.put(nodeId, thisFuture);
        return thisFuture;
    }

    private NodeHandler getNodeHandler(Map<String, Object> node) {
        String type = (String) node.get("type");
        String beanName = type.toLowerCase() + "NodeHandler";
        // Convert camelCase or snake_case if needed.
        // Dify types: "llm", "agent", "if-else" -> "ifElse"?
        if ("if-else".equals(type) || "if_else".equals(type)) {
            beanName = "ifElseNodeHandler";
        } else if ("knowledge-retrieval".equals(type) || "knowledge_retrieval".equals(type)) {
            beanName = "knowledgeNodeHandler";
        }

        NodeHandler handler = nodeHandlers.get(beanName);
        if (handler == null) {
            log.warn("No handler found for type: {}, using generic/mock", type);
            // Fallback or throw
            return (data, ctx) -> NodeExecutionResult.success(Collections.emptyMap());
        }
        return handler;
    }

    private Long getNumericId(String nodeId) {
        try {
            return Long.parseLong(nodeId.replaceAll("\\D+", ""));
        } catch (Exception e) {
            return (long) nodeId.hashCode();
        }
    }
}
