package com.adlin.orin.modules.workflow.service;

import com.adlin.orin.modules.workflow.entity.WorkflowNode;
import com.adlin.orin.modules.workflow.repository.WorkflowNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutor {

    private final WorkflowNodeRepository nodeRepository;
    // Inject other services like AgentService, KnowledgeService here if needed.
    // private final AgentManageService agentManageService;

    public Map<String, Object> executeWorkflow(String workflowId, Map<String, Object> input) {
        log.info("Starting execution of workflow: {}", workflowId);

        List<WorkflowNode> nodes = nodeRepository.findByWorkflowId(workflowId);
        Map<String, WorkflowNode> nodeMap = new HashMap<>();
        nodes.forEach(n -> nodeMap.put(n.getId(), n));

        WorkflowNode startNode = nodes.stream()
                .filter(n -> "START".equalsIgnoreCase(n.getType()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No START node found for workflow: " + workflowId));

        // Context to store variable state
        Map<String, Object> context = new HashMap<>(input);

        WorkflowNode currentNode = startNode;
        while (currentNode != null) {
            log.info("Executing node: {} ({})", currentNode.getName(), currentNode.getType());

            // Execute Node Logic
            executeNodeLogic(currentNode, context);

            // Determine Next Node
            String nextNodeId = determineNextNode(currentNode, context);

            if (nextNodeId == null) {
                break; // End of flow
            }

            currentNode = nodeMap.get(nextNodeId);
        }

        return context;
    }

    private void executeNodeLogic(WorkflowNode node, Map<String, Object> context) {
        switch (node.getType().toUpperCase()) {
            case "START":
                // Pass through
                break;
            case "AGENT":
                // Mock Agent Call
                // In real impl: agentManageService.invoke(agentId, context.get("input"));
                context.put("last_agent_output", "Simulated output from agent " + node.getName());
                break;
            case "KNOWLEDGE":
                // Mock Knowledge Retrieval
                context.put("retrieved_context", "Simulated knowledge context");
                break;
            case "CONDITION":
                // Logic handled in determineNextNode
                break;
            case "END":
                break;
            default:
                log.warn("Unknown node type: {}", node.getType());
        }
    }

    private String determineNextNode(WorkflowNode node, Map<String, Object> context) {
        String nextNodes = node.getNextNodes();
        if (nextNodes == null || nextNodes.isEmpty()) {
            return null;
        }

        if ("CONDITION".equalsIgnoreCase(node.getType())) {
            // nextNodes might be "true:id1,false:id2"
            // specific logic to evaluate condition using context
            // Mock: always return first
            return nextNodes.split(",")[0].split(":")[1];
        }

        // Simple linear flow: return the ID
        return nextNodes.split(",")[0];
    }
}
