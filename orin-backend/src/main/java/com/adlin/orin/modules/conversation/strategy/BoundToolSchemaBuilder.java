package com.adlin.orin.modules.conversation.strategy;

import com.adlin.orin.modules.conversation.dto.tooling.ToolCatalogItemDto;
import com.adlin.orin.modules.conversation.tooling.ToolCatalogService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将绑定的工具（skill:*, mcp:*, 自定义 catalog item）转换为 OpenAI 兼容的
 * tool calling schema。仅处理 enabled 且 runtimeMode = function_call 的工具。
 */
public final class BoundToolSchemaBuilder {

    private BoundToolSchemaBuilder() {}

    /**
     * @return OpenAI tool descriptor；若工具不适合 function calling 则返回 null。
     */
    public static Map<String, Object> build(ToolCatalogItemDto tool) {
        if (tool == null) return null;
        if (!Boolean.TRUE.equals(tool.getEnabled())) return null;
        if (!ToolCatalogService.MODE_FUNCTION_CALL.equalsIgnoreCase(tool.getRuntimeMode())) return null;

        String fnName = toFunctionName(tool.getToolId());
        if (fnName == null || fnName.isEmpty()) return null;

        Map<String, Object> parameters = extractParameters(tool);
        String description = buildDescription(tool);

        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", fnName);
        fn.put("description", description);
        fn.put("parameters", parameters);

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("type", "function");
        wrapper.put("function", fn);
        return wrapper;
    }

    /**
     * toolId → OpenAI 合法函数名（^[a-zA-Z0-9_-]{1,64}$），冒号被替换为下划线。
     * 例：skill:123 → skill_123
     */
    public static String toFunctionName(String toolId) {
        if (toolId == null) return null;
        String sanitized = toolId.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.length() > 64) sanitized = sanitized.substring(0, 64);
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractParameters(ToolCatalogItemDto tool) {
        Map<String, Object> schema = tool.getSchema();
        if (schema == null) return defaultParameters();

        // Skill 目录项把 JSONSchema 放在 "input" 键下
        Object input = schema.get("input");
        if (input instanceof Map<?, ?> inMap && (inMap.containsKey("type") || inMap.containsKey("properties"))) {
            return new LinkedHashMap<>((Map<String, Object>) inMap);
        }

        // 目录项直接存储 JSONSchema
        if (schema.containsKey("type") || schema.containsKey("properties") || schema.containsKey("required")) {
            return new LinkedHashMap<>(schema);
        }

        return defaultParameters();
    }

    private static String buildDescription(ToolCatalogItemDto tool) {
        StringBuilder sb = new StringBuilder();
        if (tool.getDisplayName() != null && !tool.getDisplayName().isBlank()) {
            sb.append(tool.getDisplayName());
        } else {
            sb.append(tool.getToolId());
        }
        if (tool.getCategory() != null && !tool.getCategory().isBlank()) {
            sb.append("（").append(tool.getCategory()).append("）");
        }
        return sb.toString();
    }

    private static Map<String, Object> defaultParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", new LinkedHashMap<>());
        return params;
    }
}
