package com.adlin.orin.modules.mcp.service;

import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.endpoint.dto.ExecuteEndpointRequest;
import com.adlin.orin.modules.endpoint.dto.ExecuteEndpointResponse;
import com.adlin.orin.modules.endpoint.entity.AgentEndpoint;
import com.adlin.orin.modules.endpoint.entity.EndpointStatus;
import com.adlin.orin.modules.endpoint.repository.AgentEndpointRepository;
import com.adlin.orin.modules.endpoint.service.EndpointExecutionService;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.workflow.dsl.OrinWorkflowDslNormalizer;
import com.adlin.orin.modules.workflow.dto.WorkflowExecutionSubmissionResponse;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.repository.WorkflowRepository;
import com.adlin.orin.modules.workflow.service.WorkflowService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final String ENDPOINT_PREFIX = "endpoint.";
    private static final String WORKFLOW_PREFIX = "workflow.";
    private static final String PROTOCOL = "2025-06-18";

    private final WorkflowRepository workflowRepository;
    private final AgentEndpointRepository endpointRepository;
    private final EndpointExecutionService endpointExecutionService;
    private final WorkflowService workflowService;
    private final OrinWorkflowDslNormalizer workflowDslNormalizer;
    private final AuditHelper auditHelper;
    private final ObjectMapper objectMapper;

    public Map<String, Object> handle(Object body, GatewaySecret secret) {
        if (body instanceof List<?>) return error(null, -32600, "Invalid Request: batch is not supported");
        if (!(body instanceof Map<?, ?> req)) return error(null, -32600, "Invalid Request");
        Object id = req.get("id");
        Object rawMethod = req.get("method");
        String method = rawMethod == null ? "" : String.valueOf(rawMethod);
        if ("notifications/initialized".equals(method)) return null;
        return switch (method) {
            case "initialize" -> ok(id, Map.of(
                    "protocolVersion", PROTOCOL,
                    "capabilities", Map.of("tools", Map.of("listChanged", false)),
                    "serverInfo", Map.of("name", "ORIN", "version", "0.3.0-rc.1")
            ));
            case "tools/list" -> listTools(id, secret);
            case "tools/call" -> call(id, map(req.get("params")), secret);
            default -> error(id, -32601, "Method not found");
        };
    }

    private Map<String, Object> listTools(Object id, GatewaySecret secret) {
        List<Map<String, Object>> exposedTools = tools(secret);
        audit(secret, "tools/list", null, null, null, true, null);
        return ok(id, Map.of("tools", exposedTools));
    }

    private Map<String, Object> call(Object id, Map<String, Object> params, GatewaySecret secret) {
        String tool = string(params.get("name"));
        Map<String, Object> args = map(params.get("arguments"));
        if (tool != null && tool.startsWith(WORKFLOW_PREFIX)) return callWorkflow(id, tool, args, secret);

        // F05: endpoint tools → EndpointExecutionService（REST / MCP 同一路径）
        if (tool != null && tool.startsWith(ENDPOINT_PREFIX)) {
            return callEndpoint(id, tool, args, secret);
        }

        // Legacy agent tools (backward compat — delegates to endpoint if published)
        String agentId = decodeAgentToolName(tool);
        if (agentId == null) {
            audit(secret, "tools/call", tool, null, null, false, "-32602");
            return error(id, -32602, "Invalid tool name");
        }
        // Find a published endpoint for this agent → delegate to EndpointExecutionService
        List<AgentEndpoint> eps = endpointRepository.findByAgentId(agentId);
        AgentEndpoint activeEp = eps.stream()
                .filter(e -> e.getStatus() == EndpointStatus.ACTIVE)
                .findFirst().orElse(null);
        if (activeEp != null && isKeyAllowed(activeEp, secret)) {
            return callEndpointById(id, activeEp, args, secret);
        }
        audit(secret, "tools/call", tool, null, null, false, "-32003");
        return error(id, -32003, "Agent not published as endpoint. Use F05 publish first.");
    }

    /** F05: endpoint tool → EndpointExecutionService。 */
    private Map<String, Object> callEndpoint(Object id, String tool, Map<String, Object> args,
                                              GatewaySecret secret) {
        String endpointId = decodeEndpointToolName(tool);
        if (endpointId == null) {
            audit(secret, "tools/call", tool, null, null, false, "-32602");
            return error(id, -32602, "Invalid endpoint tool name");
        }
        AgentEndpoint ep = endpointRepository.findById(endpointId).orElse(null);
        if (ep == null) {
            audit(secret, "tools/call", tool, null, null, false, "-32602");
            return error(id, -32602, "Endpoint not found");
        }
        return callEndpointById(id, ep, args, secret);
    }

    private Map<String, Object> callEndpointById(Object id, AgentEndpoint ep,
                                                  Map<String, Object> args, GatewaySecret secret) {
        String input = string(args.get("input"));
        if (input == null || input.isBlank()) {
            // backward compat: accept "message" as alias for "input"
            input = string(args.get("message"));
        }
        if (input == null || input.isBlank()) {
            audit(secret, "tools/call", "endpoint." + encodeEndpointId(ep.getId()),
                    null, null, false, "-32602");
            return error(id, -32602, "input is required");
        }
        try {
            ExecuteEndpointRequest req = new ExecuteEndpointRequest();
            req.setInput(input);
            req.setTimeoutMs(args.get("timeoutMs") instanceof Number n ? n.longValue() : null);
            ExecuteEndpointResponse resp = endpointExecutionService.execute(ep.getId(), req, secret);
            String text = resp.getOutput() != null ? resp.getOutput()
                    : "Run " + resp.getRunId() + " status=" + resp.getStatus()
                    + " traceId=" + resp.getTraceId();
            audit(secret, "tools/call", "endpoint." + encodeEndpointId(ep.getId()),
                    resp.getTraceId(), resp.getRunId(), true, null);
            return ok(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text", text)),
                    "isError", false));
        } catch (Exception e) {
            audit(secret, "tools/call", "endpoint." + encodeEndpointId(ep.getId()),
                    null, null, false, "TOOL_ERROR");
            return ok(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text",
                            safe(e.getMessage(), "Endpoint execution failed"))),
                    "isError", true));
        }
    }

    private Map<String, Object> callWorkflow(Object id, String tool, Map<String, Object> args, GatewaySecret secret) {
        Long workflowId = decodeWorkflowToolName(tool);
        Long owner = owner(secret);
        if (workflowId == null || owner == null) {
            audit(secret, "tools/call", tool, null, null, false, "-32602");
            return error(id, -32602, "Invalid tool name");
        }
        WorkflowEntity workflow = workflowRepository.findById(workflowId).orElse(null);
        if (workflow == null || !workflow.isMcpExposed() || !owner.equals(workflow.getOwnerUserId())) {
            audit(secret, "tools/call", tool, null, null, false, "-32003");
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
            audit(secret, "tools/call", tool, safe(submission.getTraceId(), null), null, true, null);
            return ok(id, Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", false));
        } catch (Exception e) {
            audit(secret, "tools/call", tool, null, null, false, "TOOL_ERROR");
            return ok(id, Map.of("content", List.of(Map.of("type", "text", "text", safe(e.getMessage(), "Workflow execution failed"))), "isError", true));
        }
    }

    // ---- F05 endpoint tool helpers ----

    private String endpointToolName(String endpointId) {
        return ENDPOINT_PREFIX + encodeEndpointId(endpointId);
    }

    private String encodeEndpointId(String endpointId) {
        return endpointId; // endpoint IDs are already safe (ep_xxx)
    }

    private String decodeEndpointToolName(String name) {
        if (name == null || !name.startsWith(ENDPOINT_PREFIX)) return null;
        return name.substring(ENDPOINT_PREFIX.length());
    }

    private boolean isKeyAllowed(AgentEndpoint ep, GatewaySecret secret) {
        return isKeyAllowedByConfig(ep.getConfig(), secret);
    }

    private boolean isKeyAllowedByConfig(String configJson, GatewaySecret secret) {
        if (configJson == null || configJson.isBlank()) return false;
        try {
            Map<String, Object> config = objectMapper.readValue(configJson,
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<String> allowed = (List<String>) config.get("allowedApiKeyIds");
            return allowed != null && allowed.contains(secret.getSecretId());
        } catch (Exception e) {
            return false;
        }
    }

    // ---- tools list ----

    private List<Map<String, Object>> tools(GatewaySecret secret) {
        Long owner = owner(secret);
        if (owner == null) return List.of();
        List<Map<String, Object>> tools = new ArrayList<>();

        // F05: list published endpoints (replaces raw agent listing)
        List<AgentEndpoint> allEndpoints = endpointRepository.findAll();
        allEndpoints.stream()
                .filter(e -> e.getStatus() == EndpointStatus.ACTIVE)
                // A tool name is itself a capability disclosure.  Listing an endpoint
                // merely because another key is assigned to it would let unrelated
                // CLIENT_ACCESS keys discover and attempt to invoke that endpoint.
                .filter(e -> isKeyAllowedByConfig(e.getConfig(), secret))
                .map(e -> Map.<String, Object>of(
                        "name", endpointToolName(e.getId()),
                        "title", safe(e.getName(), e.getId()),
                        "description", safe(e.getDescription(), "Published Agent Endpoint"),
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "input", Map.of("type", "string",
                                                "description", "User input / prompt"),
                                        "timeoutMs", Map.of("type", "integer",
                                                "description", "Max wait time in milliseconds (0 = async)")
                                ),
                                "required", List.of("input")
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

    private void audit(GatewaySecret secret, String method, String tool, String traceId, String packageId,
                       boolean success, String errorCode) {
        if (auditHelper == null) return;
        StringBuilder detail = new StringBuilder("method=").append(method);
        detail.append(";secretId=").append(safe(secret == null ? null : secret.getSecretId(), ""));
        if (tool != null && !tool.isBlank()) detail.append(";toolName=").append(tool);
        if (traceId != null && !traceId.isBlank()) detail.append(";traceId=").append(traceId);
        if (packageId != null && !packageId.isBlank()) detail.append(";packageId=").append(packageId);
        if (errorCode != null && !errorCode.isBlank()) detail.append(";errorCode=").append(errorCode);
        String operation = "tools/list".equals(method) ? "MCP_TOOLS_LIST" : "MCP_TOOLS_CALL";
        auditHelper.log(
                secret == null ? null : secret.getUserId(),
                operation,
                "/v1/mcp",
                detail.toString(),
                success,
                success ? null : safe(errorCode, "MCP tool call failed"));
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
