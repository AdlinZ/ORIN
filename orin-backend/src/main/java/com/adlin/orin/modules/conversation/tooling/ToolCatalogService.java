package com.adlin.orin.modules.conversation.tooling;

import com.adlin.orin.modules.conversation.dto.tooling.ToolCatalogItemDto;
import com.adlin.orin.modules.conversation.dto.tooling.ToolCatalogUpdateRequest;
import com.adlin.orin.modules.conversation.entity.ToolCatalogItem;
import com.adlin.orin.modules.conversation.repository.ToolCatalogItemRepository;
import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.entity.SkillEntity;
import com.adlin.orin.modules.skill.repository.McpServiceRepository;
import com.adlin.orin.modules.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ToolCatalogService {

    public static final String CATEGORY_BUILTIN_KB = "BUILTIN_KB";
    public static final String CATEGORY_SKILL = "SKILL";
    public static final String CATEGORY_MCP = "MCP";
    public static final String CATEGORY_WORKFLOW_TOOL = "WORKFLOW_TOOL";

    public static final String MODE_FUNCTION_CALL = "function_call";
    public static final String MODE_CONTEXT_ONLY = "context_only";

    private final ToolCatalogItemRepository toolCatalogItemRepository;
    private final SkillRepository skillRepository;
    private final McpServiceRepository mcpServiceRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<Long, CachedMcpTools> mcpToolsCache = new ConcurrentHashMap<>();

    @Value("${orin.ai-engine.url:http://localhost:8000}")
    private String aiEngineUrl;

    public List<ToolCatalogItemDto> listCatalog(String category, boolean includeDisabled) {
        Map<String, ToolCatalogItemDto> merged = new LinkedHashMap<>();

        for (ToolCatalogItemDto item : defaultBuiltinTools()) {
            merged.put(item.getToolId(), item);
        }
        for (ToolCatalogItemDto item : buildSkillTools()) {
            merged.put(item.getToolId(), item);
        }
        for (ToolCatalogItemDto item : buildMcpTools()) {
            merged.put(item.getToolId(), item);
        }
        for (ToolCatalogItem item : toolCatalogItemRepository.findAll()) {
            merged.put(item.getToolId(), mergeOverride(merged.get(item.getToolId()), item));
        }

        return merged.values().stream()
                .filter(item -> includeDisabled || Boolean.TRUE.equals(item.getEnabled()))
                .filter(item -> category == null || category.isBlank() || category.equalsIgnoreCase(item.getCategory()))
                .sorted(Comparator.comparing(ToolCatalogItemDto::getCategory).thenComparing(ToolCatalogItemDto::getToolId))
                .collect(Collectors.toList());
    }

    public ToolCatalogItemDto upsertCatalogItem(String toolId, ToolCatalogUpdateRequest request) {
        ToolCatalogItem item = toolCatalogItemRepository.findById(toolId)
                .orElseGet(() -> ToolCatalogItem.builder().toolId(toolId).displayName(toolId).category(CATEGORY_WORKFLOW_TOOL).build());

        if (request.getDisplayName() != null) item.setDisplayName(request.getDisplayName());
        if (request.getCategory() != null) item.setCategory(request.getCategory());
        if (request.getSchema() != null) item.setSchema(request.getSchema());
        if (request.getEnabled() != null) item.setEnabled(request.getEnabled());
        if (request.getRuntimeMode() != null) item.setRuntimeMode(request.getRuntimeMode());
        if (request.getHealthStatus() != null) item.setHealthStatus(request.getHealthStatus());
        if (request.getVersion() != null) item.setVersion(request.getVersion());
        if (request.getSource() != null) item.setSource(request.getSource());

        return toDto(toolCatalogItemRepository.save(item));
    }

    public Map<String, ToolCatalogItemDto> getCatalogMap() {
        return listCatalog(null, true).stream().collect(Collectors.toMap(ToolCatalogItemDto::getToolId, i -> i));
    }

    private ToolCatalogItemDto mergeOverride(ToolCatalogItemDto base, ToolCatalogItem override) {
        ToolCatalogItemDto merged = base != null ? base : ToolCatalogItemDto.builder().toolId(override.getToolId()).build();
        if (override.getDisplayName() != null) merged.setDisplayName(override.getDisplayName());
        if (override.getCategory() != null) merged.setCategory(override.getCategory());
        if (override.getSchema() != null) merged.setSchema(override.getSchema());
        if (override.getEnabled() != null) merged.setEnabled(override.getEnabled());
        if (override.getRuntimeMode() != null) merged.setRuntimeMode(override.getRuntimeMode());
        if (override.getHealthStatus() != null) merged.setHealthStatus(override.getHealthStatus());
        if (override.getVersion() != null) merged.setVersion(override.getVersion());
        merged.setSource(override.getSource() != null ? override.getSource() : merged.getSource());
        return merged;
    }

    private ToolCatalogItemDto toDto(ToolCatalogItem item) {
        return ToolCatalogItemDto.builder()
                .toolId(item.getToolId())
                .displayName(item.getDisplayName())
                .category(item.getCategory())
                .schema(item.getSchema())
                .enabled(item.getEnabled())
                .runtimeMode(item.getRuntimeMode())
                .healthStatus(item.getHealthStatus())
                .version(item.getVersion())
                .source(item.getSource())
                .build();
    }

    private List<ToolCatalogItemDto> buildSkillTools() {
        List<SkillEntity> skills = skillRepository.findAll();
        List<ToolCatalogItemDto> result = new ArrayList<>();
        for (SkillEntity skill : skills) {
            Map<String, Object> schema = new HashMap<>();
            schema.put("input", skill.getInputSchema());
            schema.put("output", skill.getOutputSchema());
            schema.put("skillType", skill.getSkillType() != null ? skill.getSkillType().name() : "UNKNOWN");

            result.add(ToolCatalogItemDto.builder()
                    .toolId("skill:" + skill.getId())
                    .displayName(skill.getSkillName())
                    .category(CATEGORY_SKILL)
                    .schema(schema)
                    .enabled(skill.getStatus() == SkillEntity.SkillStatus.ACTIVE)
                    .runtimeMode(MODE_FUNCTION_CALL)
                    .healthStatus(skill.getStatus() != null ? skill.getStatus().name() : "UNKNOWN")
                    .version(skill.getVersion())
                    .source("SKILL")
                    .build());
        }
        return result;
    }

    private List<ToolCatalogItemDto> buildMcpTools() {
        List<McpService> services = mcpServiceRepository.findAll();
        List<ToolCatalogItemDto> result = new ArrayList<>();
        for (McpService service : services) {
            Map<String, Object> schema = buildMcpServiceSchema(service);

            result.add(ToolCatalogItemDto.builder()
                    .toolId("mcp:" + service.getId())
                    .displayName(service.getName())
                    .category(CATEGORY_MCP)
                    .schema(schema)
                    .enabled(Boolean.TRUE.equals(service.getEnabled()))
                    .runtimeMode(service.getStatus() == McpService.McpStatus.CONNECTED
                            ? MODE_FUNCTION_CALL
                            : MODE_CONTEXT_ONLY)
                    .healthStatus(service.getStatus() != null ? service.getStatus().name() : "UNKNOWN")
                    .version("1.0.0")
                    .source("MCP")
                    .build());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildMcpServiceSchema(McpService service) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        List<Map<String, Object>> tools = getLiveMcpTools(service);
        List<String> names = tools.stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .filter(name -> name != null && !name.isBlank())
                .toList();
        schema.put("properties", Map.of(
                "toolName", Map.of("type", "string", "description", "MCP tool name", "enum", names),
                "arguments", Map.of("type", "object", "description", "MCP tool arguments")
        ));
        schema.put("required", List.of("toolName"));
        schema.put("mcpTools", tools);
        return schema;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getLiveMcpTools(McpService service) {
        if (service.getId() == null || !Boolean.TRUE.equals(service.getEnabled())) {
            return List.of();
        }
        CachedMcpTools cached = mcpToolsCache.get(service.getId());
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAtMillis < 60_000L) {
            return cached.tools;
        }
        try {
            String url = aiEngineUrl.replaceAll("/+$", "") + "/api/mcp/services/" + service.getId() + "/tools";
            Map<String, Object> payload = restTemplate.getForObject(url, Map.class);
            Object rawTools = payload != null ? payload.get("tools") : null;
            List<Map<String, Object>> tools = rawTools instanceof List<?> values
                    ? values.stream()
                            .filter(Map.class::isInstance)
                            .map(item -> (Map<String, Object>) item)
                            .toList()
                    : List.of();
            mcpToolsCache.put(service.getId(), new CachedMcpTools(now, tools));
            return tools;
        } catch (Exception e) {
            return cached != null ? cached.tools : List.of();
        }
    }

    private record CachedMcpTools(long loadedAtMillis, List<Map<String, Object>> tools) {
    }

    private List<ToolCatalogItemDto> defaultBuiltinTools() {
        List<ToolCatalogItemDto> defaults = new ArrayList<>();
        defaults.add(buildBuiltin("query_kb", "知识库语义检索", CATEGORY_BUILTIN_KB, MODE_FUNCTION_CALL,
                Map.of("type", "object", "required", List.of("query", "kb_ids"))));
        defaults.add(buildBuiltin("read_document", "文档全文读取", CATEGORY_BUILTIN_KB, MODE_FUNCTION_CALL,
                Map.of("type", "object", "required", List.of("doc_id"))));
        defaults.add(buildBuiltin("kb_structure_scan", "知识库结构扫描", CATEGORY_BUILTIN_KB, MODE_FUNCTION_CALL,
                Map.of("type", "object", "required", List.of("kb_ids"))));
        defaults.add(buildBuiltin("sql_query_safe", "只读 SQL 查询", CATEGORY_BUILTIN_KB, MODE_CONTEXT_ONLY,
                Map.of("type", "object", "required", List.of("sql"), "readonly", true)));
        defaults.add(buildBuiltin("list_knowledge_bases", "列出所有知识库", CATEGORY_BUILTIN_KB, MODE_FUNCTION_CALL,
                Map.of("type", "object")));
        defaults.add(buildBuiltin("list_documents", "列出知识库文档", CATEGORY_BUILTIN_KB, MODE_FUNCTION_CALL,
                Map.of("type", "object", "required", List.of("kb_id"))));
        defaults.add(buildBuiltin("get_document_metadata", "获取文档元数据", CATEGORY_BUILTIN_KB, MODE_FUNCTION_CALL,
                Map.of("type", "object", "required", List.of("doc_id"))));
        defaults.add(buildBuiltin("get_kb_info", "获取知识库详情", CATEGORY_BUILTIN_KB, MODE_FUNCTION_CALL,
                Map.of("type", "object", "required", List.of("kb_id"))));
        defaults.add(buildBuiltin("list_knowledge_graphs", "列出所有知识图谱", CATEGORY_BUILTIN_KB, MODE_FUNCTION_CALL,
                Map.of("type", "object")));
        defaults.add(buildBuiltin("get_graph_info", "获取图谱详情", CATEGORY_BUILTIN_KB, MODE_FUNCTION_CALL,
                Map.of("type", "object", "required", List.of("graph_id"))));
        defaults.add(buildBuiltin("list_graph_entities", "列出图谱实体", CATEGORY_BUILTIN_KB, MODE_FUNCTION_CALL,
                Map.of("type", "object", "required", List.of("graph_id"))));
        defaults.add(buildBuiltin("search_graph_entities", "搜索图谱实体", CATEGORY_BUILTIN_KB, MODE_FUNCTION_CALL,
                Map.of("type", "object", "required", List.of("graph_id", "keyword"))));

        defaults.add(buildBuiltin("workflow_run", "执行工作流", CATEGORY_WORKFLOW_TOOL, MODE_CONTEXT_ONLY,
                Map.of("type", "object", "required", List.of("workflow_id", "input"))));
        defaults.add(buildBuiltin("http_fetch", "受控 HTTP 抓取", CATEGORY_WORKFLOW_TOOL, MODE_CONTEXT_ONLY,
                Map.of("type", "object", "required", List.of("url"))));
        defaults.add(buildBuiltin("code_search", "代码检索", CATEGORY_WORKFLOW_TOOL, MODE_CONTEXT_ONLY,
                Map.of("type", "object", "required", List.of("query"))));
        defaults.add(buildBuiltin("mcp_proxy_call", "MCP 统一代理调用", CATEGORY_WORKFLOW_TOOL, MODE_CONTEXT_ONLY,
                Map.of("type", "object", "required", List.of("service_id", "method", "args"))));

        defaults.add(buildBuiltin("rerank_chunks", "检索片段重排", CATEGORY_WORKFLOW_TOOL, MODE_CONTEXT_ONLY,
                Map.of("type", "object", "required", List.of("query", "chunks"))));
        defaults.add(buildBuiltin("summarize_docs", "文档摘要", CATEGORY_WORKFLOW_TOOL, MODE_CONTEXT_ONLY,
                Map.of("type", "object", "required", List.of("doc_ids"))));
        defaults.add(buildBuiltin("task_memory_upsert", "任务记忆写入", CATEGORY_WORKFLOW_TOOL, MODE_CONTEXT_ONLY,
                Map.of("type", "object", "required", List.of("key", "value"))));
        defaults.add(buildBuiltin("task_memory_query", "任务记忆查询", CATEGORY_WORKFLOW_TOOL, MODE_CONTEXT_ONLY,
                Map.of("type", "object", "required", List.of("key"))));

        return defaults;
    }

    private ToolCatalogItemDto buildBuiltin(String toolId, String displayName, String category, String runtimeMode,
            Map<String, Object> schema) {
        return ToolCatalogItemDto.builder()
                .toolId(toolId.toLowerCase(Locale.ROOT))
                .displayName(displayName)
                .category(category)
                .schema(schema)
                .enabled(true)
                .runtimeMode(runtimeMode)
                .healthStatus("HEALTHY")
                .version("1.0.0")
                .source("SYSTEM")
                .build();
    }
}
