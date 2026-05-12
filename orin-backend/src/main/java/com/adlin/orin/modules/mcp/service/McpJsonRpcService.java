package com.adlin.orin.modules.mcp.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.workflow.dsl.OrinWorkflowDslNormalizer;
import com.adlin.orin.modules.workflow.dto.WorkflowExecutionSubmissionResponse;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.repository.WorkflowRepository;
import com.adlin.orin.modules.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class McpJsonRpcService {
    private static final String LEGACY_AGENT_PREFIX = "orin_agent_";
    private static final String AGENT_PREFIX = "agent.";
    private static final String WORKFLOW_PREFIX = "workflow.";
    private static final String PROTOCOL = "2025-06-18";

    private final AgentMetadataRepository agentRepository;
    private final WorkflowRepository workflowRepository;
    private final ExternalMcpAgentExecutionService executionService;
    private final WorkflowService workflowService;
    private final OrinWorkflowDslNormalizer workflowDslNormalizer;

    public Map<String, Object> handle(Object body, GatewaySecret secret) {
        if (!(body instanceof Map<?, ?> req)) return error(null, -32600, "Invalid Request");
        Object id = req.get("id");
        Object rawMethod = req.get("method");
        String method = rawMethod == null ? "" : String.valueOf(rawMethod);
        if ("notifications/initialized".equals(method)) return null;
        return switch (method) {
            case "initialize" -> ok(id, Map.of(
                    "protocolVersion", PROTOCOL,
                    "capabilities", Map.of("tools", Map.of("listChanged", false)),
                    "serverInfo", Map.of("name", "ORIN", "version", "0.1.0")
            ));
            case "tools/list" -> ok(id, Map.of("tools", tools(owner(secret))));
            case "tools/call" -> call(id, map(req.get("params")), secret);
            default -> error(id, -32601, "Method not found");
        };
    }

    private Map<String, Object> call(Object id, Map<String, Object> params, GatewaySecret secret) {
        String tool = string(params.get("name"));
        Map<String, Object> args = map(params.get("arguments"));
        if (tool != null && tool.startsWith(WORKFLOW_PREFIX)) return callWorkflow(id, tool, args, secret);
        String agentId = decodeAgentToolName(tool);
        if (agentId == null) return error(id, -32602, "Invalid tool name");
        AgentMetadata agent = agentRepository.findById(agentId).orElse(null);
        Long owner = owner(secret);
        if (agent == null || !agent.isMcpExposed() || owner == null || !owner.equals(agent.getOwnerUserId())) {
            return error(id, -32003, "Forbidden");
        }
        String message = string(args.get("message"));
        if (message == null || message.isBlank()) return error(id, -32602, "message is required");
        Integer maxTokens = args.get("max_tokens") instanceof Number n ? n.intValue() : null;
        try {
            String text = safe(executionService.execute(agent, message, string(args.get("context")), maxTokens, secret.getUserId()), "");
            return ok(id, Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", false));
        } catch (Exception e) {
            return ok(id, Map.of("content", List.of(Map.of("type", "text", "text", safe(e.getMessage(), "Agent execution failed"))), "isError", true));
        }
    }

    private Map<String, Object> callWorkflow(Object id, String tool, Map<String, Object> args, GatewaySecret secret) {
        Long workflowId = decodeWorkflowToolName(tool);
        Long owner = owner(secret);
        if (workflowId == null || owner == null) return error(id, -32602, "Invalid tool name");
        WorkflowEntity workflow = workflowRepository.findById(workflowId).orElse(null);
        if (workflow == null || !workflow.isMcpExposed() || !owner.equals(workflow.getOwnerUserId())) {
            return error(id, -32003, "Forbidden");
        }
        try {
            WorkflowExecutionSubmissionResponse submission = workflowService.submitWorkflowExecution(
                    workflowId, args, TaskEntity.TaskPriority.NORMAL, secret.getUserId(), "external_mcp");
            String text = "Workflow submitted: taskId=%s, workflowInstanceId=%s, traceId=%s, status=%s, statusUrl=%s"
                    .formatted(
                            submission.getTaskId(),
                            submission.getWorkflowInstanceId(),
                            safe(submission.getTraceId(), ""),
                            submission.getStatus(),
                            submission.getStatusUrl());
            return ok(id, Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", false));
        } catch (Exception e) {
            return ok(id, Map.of("content", List.of(Map.of("type", "text", "text", safe(e.getMessage(), "Workflow execution failed"))), "isError", true));
        }
    }

    private List<Map<String, Object>> tools(Long owner) {
        if (owner == null) return List.of();
        List<Map<String, Object>> tools = new ArrayList<>();
        List<AgentMetadata> agents = agentRepository.findByOwnerUserIdAndMcpExposedTrue(owner);
        if (agents == null) agents = List.of();
        agents.stream()
                .map(a -> Map.<String, Object>of(
                        "name", agentToolName(a.getAgentId()),
                        "title", safe(a.getName(), a.getAgentId()),
                        "description", safe(a.getDescription(), "ORIN Agent") + " provider=" + safe(a.getProviderType(), "unknown"),
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "message", Map.of("type", "string"),
                                        "context", Map.of("type", "string"),
                                        "max_tokens", Map.of("type", "integer", "minimum", 1)
                                ),
                                "required", List.of("message")
                        )
                ))
                .forEach(tools::add);
        List<WorkflowEntity> workflows = workflowRepository.findByOwnerUserIdAndMcpExposedTrue(owner);
        if (workflows == null) workflows = List.of();
        workflows.stream()
                .map(w -> Map.<String, Object>of(
                        "name", workflowToolName(w.getId()),
                        "title", safe(w.getWorkflowName(), "Workflow " + w.getId()),
                        "description", safe(w.getDescription(), "ORIN Workflow"),
                        "inputSchema", workflowInputSchema(w)
                ))
                .forEach(tools::add);
        return tools;
    }

    private Map<String, Object> workflowInputSchema(WorkflowEntity workflow) {
        Map<String, Object> normalized = workflowDslNormalizer.normalize(workflow.getWorkflowDefinition(), "ORIN");
        Object graph = normalized.get("graph");
        if (graph instanceof Map<?, ?> graphMap && graphMap.get("nodes") instanceof List<?> nodes) {
            for (Object rawNode : nodes) {
                if (rawNode instanceof Map<?, ?> node
                        && "start".equals(string(node.get("type")))
                        && node.get("data") instanceof Map<?, ?> data
                        && data.get("variables") instanceof List<?> variables
                        && !variables.isEmpty()) {
                    return schemaFromVariables(variables);
                }
            }
        }
        return Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query")
        );
    }

    private Map<String, Object> schemaFromVariables(List<?> variables) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Object rawVariable : variables) {
            if (!(rawVariable instanceof Map<?, ?> variable)) continue;
            String name = safe(firstNonBlank(variable.get("name"), variable.get("variable"), variable.get("key"), variable.get("id")), "");
            if (name.isBlank()) continue;
            properties.put(name, Map.of("type", jsonSchemaType(string(variable.get("type")))));
            if (Boolean.TRUE.equals(variable.get("required"))) required.add(name);
        }
        if (properties.isEmpty()) properties.put("query", Map.of("type", "string"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required);
        return schema;
    }

    private String jsonSchemaType(String type) {
        if (type == null) return "string";
        return switch (type.toLowerCase()) {
            case "number", "integer", "boolean", "array", "object" -> type.toLowerCase();
            default -> "string";
        };
    }

    private String agentToolName(String agentId) {
        return AGENT_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(agentId.getBytes(StandardCharsets.UTF_8));
    }

    private String workflowToolName(Long workflowId) {
        return WORKFLOW_PREFIX + workflowId;
    }

    private String decodeAgentToolName(String name) {
        if (name == null) return null;
        String prefix = name.startsWith(LEGACY_AGENT_PREFIX) ? LEGACY_AGENT_PREFIX : AGENT_PREFIX;
        if (!name.startsWith(prefix)) return null;
        try {
            return new String(Base64.getUrlDecoder().decode(name.substring(prefix.length())), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Long decodeWorkflowToolName(String name) {
        try {
            return Long.valueOf(name.substring(WORKFLOW_PREFIX.length()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Long owner(GatewaySecret secret) {
        try {
            return secret == null || secret.getUserId() == null ? null : Long.valueOf(secret.getUserId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String candidate = string(value);
            if (candidate != null && !candidate.isBlank()) return candidate;
        }
        return null;
    }

    private Map<String, Object> ok(Object id, Map<String, Object> result) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("jsonrpc", "2.0");
        res.put("id", id);
        res.put("result", result);
        return res;
    }

    private Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("jsonrpc", "2.0");
        res.put("id", id);
        res.put("error", Map.of("code", code, "message", message));
        return res;
    }
}
