package com.adlin.orin.modules.mcp.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class McpJsonRpcService {
    private static final String PREFIX = "orin_agent_";
    private static final String PROTOCOL = "2025-06-18";

    private final AgentMetadataRepository agentRepository;
    private final ExternalMcpAgentExecutionService executionService;

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
        String agentId = decodeToolName(tool);
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

    private List<Map<String, Object>> tools(Long owner) {
        if (owner == null) return List.of();
        return agentRepository.findByOwnerUserIdAndMcpExposedTrue(owner).stream()
                .map(a -> Map.of(
                        "name", toolName(a.getAgentId()),
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
                .toList();
    }

    private String toolName(String agentId) {
        return PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(agentId.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeToolName(String name) {
        if (name == null || !name.startsWith(PREFIX)) return null;
        try {
            return new String(Base64.getUrlDecoder().decode(name.substring(PREFIX.length())), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
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
