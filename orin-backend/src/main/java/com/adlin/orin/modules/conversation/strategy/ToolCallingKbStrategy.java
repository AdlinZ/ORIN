package com.adlin.orin.modules.conversation.strategy;

import com.adlin.orin.modules.conversation.dto.ChatMessageResponse.ToolTrace;
import com.adlin.orin.modules.conversation.dto.tooling.ToolCatalogItemDto;
import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.GraphEntity;
import com.adlin.orin.modules.knowledge.entity.KnowledgeGraph;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.GraphEntityRepository;
import com.adlin.orin.modules.knowledge.repository.GraphRelationRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeGraphRepository;
import com.adlin.orin.modules.knowledge.service.KnowledgeManageService;
import com.adlin.orin.modules.knowledge.service.KnowledgeGraphService;
import com.adlin.orin.modules.knowledge.service.RetrievalService;
import com.adlin.orin.modules.model.service.OllamaIntegrationService;
import com.adlin.orin.modules.skill.component.AiEngineMcpClient;
import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.repository.McpServiceRepository;
import com.adlin.orin.modules.skill.service.SkillService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * 支持 tool calling 的模型的 RAG 策略：通过 function calling 让模型自主决定
 * 何时检索片段、何时读取全文，适用于任何兼容 OpenAI tool calling 协议的 provider。
 */
@Slf4j
@RequiredArgsConstructor
public class ToolCallingKbStrategy {

    private static final int MAX_TOOL_ROUNDS = 5;

    private final OllamaIntegrationService ollamaService;
    private final RetrievalService retrievalService;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeGraphRepository knowledgeGraphRepository;
    private final GraphEntityRepository graphEntityRepository;
    private final GraphRelationRepository graphRelationRepository;
    private final KnowledgeManageService knowledgeManageService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final SkillService skillService;
    private final McpServiceRepository mcpServiceRepository;
    private final AiEngineMcpClient aiEngineMcpClient;
    private final ObjectMapper objectMapper;
    private Integer retrievalTopK;
    private Double retrievalThreshold;
    private Double retrievalAlpha;
    private String retrievalEmbeddingModel;
    private Boolean retrievalEnableRerank;
    private String retrievalRerankModel;

    public void configureRetrieval(Integer topK, Double threshold, Double alpha, String embeddingModel,
            Boolean enableRerank, String rerankModel) {
        this.retrievalTopK = topK;
        this.retrievalThreshold = threshold;
        this.retrievalAlpha = alpha;
        this.retrievalEmbeddingModel = embeddingModel;
        this.retrievalEnableRerank = enableRerank;
        this.retrievalRerankModel = rerankModel;
    }

    public Map<String, Object> execute(
            String endpoint, String apiKey, String model,
            String systemPrompt, String userMessage,
            List<String> kbIds,
            List<ToolCatalogItemDto> extraBoundTools,
            double temperature, int maxTokens,
            Consumer<ToolTrace> traceConsumer) {

        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        boolean hasKbs = kbIds != null && !kbIds.isEmpty();
        List<Map<String, Object>> tools = new ArrayList<>();
        if (hasKbs) {
            tools.addAll(KbToolDefinitions.build(kbIds));
        }

        // 将绑定工具（skill/mcp/自定义）转为 OpenAI tool schema，并建立分派映射
        Map<String, ToolCatalogItemDto> boundToolsByFnName = new LinkedHashMap<>();
        if (extraBoundTools != null) {
            for (ToolCatalogItemDto item : extraBoundTools) {
                Map<String, Object> schema = BoundToolSchemaBuilder.build(item);
                if (schema == null) continue;
                tools.add(schema);
                boundToolsByFnName.put(BoundToolSchemaBuilder.toFunctionName(item.getToolId()), item);
            }
        }

        if (tools.isEmpty()) {
            // 兜底：理论上调用方已做 gating，这里再保护一层
            emitTrace(traceConsumer, ToolTrace.builder()
                    .type("KB_MODEL_TOOL_FINAL")
                    .kbId("multiple")
                    .message("没有可用的 tool calling 工具，策略退出")
                    .status("warning")
                    .durationMs(0L)
                    .build());
            return result("（未配置可用工具，tool calling 模式无法进入）", "", 0, 0, 0);
        }
        int totalToolCalls = 0;
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            Optional<Object> resp = ollamaService.sendMessageWithTools(
                    endpoint, apiKey, model, messages, tools, temperature, maxTokens);

            if (resp.isEmpty()) {
                emitTrace(traceConsumer, ToolTrace.builder()
                        .type("KB_MODEL_TOOL_FINAL")
                        .kbId("multiple")
                        .message("模型未返回响应，工具调用流程提前结束")
                        .status("error")
                        .durationMs(0L)
                        .detail(Map.of("round", round + 1, "totalToolCalls", totalToolCalls))
                        .build());
                return result("（模型未返回响应）", "", promptTokens, completionTokens, totalTokens);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = (Map<String, Object>) resp.get();
            UsageMetrics usageMetrics = extractUsage(respMap);
            promptTokens += usageMetrics.prompt();
            completionTokens += usageMetrics.completion();
            totalTokens += usageMetrics.total();

            Map<String, Object> message = extractMessage(respMap);
            if (message == null) {
                String responseError = extractResponseError(respMap);
                emitTrace(traceConsumer, ToolTrace.builder()
                        .type("KB_MODEL_TOOL_FINAL")
                        .kbId("multiple")
                        .message("无法解析模型响应，工具调用流程提前结束")
                        .status("error")
                        .durationMs(0L)
                        .detail(Map.of(
                                "round", round + 1,
                                "totalToolCalls", totalToolCalls,
                                "responseKeys", respMap.keySet(),
                                "responseError", responseError != null ? responseError : ""))
                        .build());
                String errorText = responseError != null && !responseError.isBlank()
                        ? "（模型响应解析失败：" + responseError + "）"
                        : "（无法解析模型响应）";
                return result(errorText, "", promptTokens, completionTokens, totalTokens);
            }

            messages.add(new HashMap<>(message));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls == null || toolCalls.isEmpty()) {
                String content = (String) message.get("content");
                emitTrace(traceConsumer, ToolTrace.builder()
                        .type("KB_MODEL_TOOL_FINAL")
                        .kbId("multiple")
                        .message("模型完成回答，工具调用总计 " + totalToolCalls + " 次")
                        .status("success")
                        .durationMs(0L)
                        .detail(Map.of(
                                "round", round + 1,
                                "totalToolCalls", totalToolCalls,
                                "finalContentPreview", preview(content)))
                        .build());
                return result(
                        content != null ? content : "（模型未返回正文）",
                        extractModel(respMap),
                        promptTokens,
                        completionTokens,
                        totalTokens);
            }

            for (Map<String, Object> toolCall : toolCalls) {
                long toolStart = System.currentTimeMillis();
                String toolCallId = (String) toolCall.get("id");
                @SuppressWarnings("unchecked")
                Map<String, Object> fn = (Map<String, Object>) toolCall.get("function");
                if (fn == null) continue;

                String fnName = (String) fn.get("name");
                String argsJson = (String) fn.get("arguments");
                String toolResult = executeTool(fnName, argsJson, kbIds, boundToolsByFnName);
                totalToolCalls++;

                boolean failed = toolResult != null
                        && (toolResult.startsWith("（工具执行失败")
                        || toolResult.startsWith("（未知工具"));
                emitTrace(traceConsumer, ToolTrace.builder()
                        .type("KB_MODEL_TOOL_CALL")
                        .kbId("multiple")
                        .message("模型调用工具 " + fnName + "（第 " + (round + 1) + " 轮）")
                        .status(failed ? "warning" : "success")
                        .durationMs(System.currentTimeMillis() - toolStart)
                        .detail(Map.of(
                                "round", round + 1,
                                "toolCallId", toolCallId != null ? toolCallId : "",
                                "toolName", fnName != null ? fnName : "",
                                "arguments", parseArgsSafe(argsJson),
                                "resultPreview", preview(toolResult)))
                        .build());

                Map<String, Object> toolMsg = new HashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolCallId);
                toolMsg.put("content", toolResult);
                messages.add(toolMsg);
            }
        }

        emitTrace(traceConsumer, ToolTrace.builder()
                .type("KB_MODEL_TOOL_FINAL")
                .kbId("multiple")
                .message("达到最大工具调用轮数，未能得到最终回答")
                .status("warning")
                .durationMs(0L)
                .detail(Map.of("maxRounds", MAX_TOOL_ROUNDS, "totalToolCalls", totalToolCalls))
                .build());
        return result("（达到最大工具调用轮数，未能生成最终回答）", "", promptTokens, completionTokens, totalTokens);
    }

    private String executeTool(String name, String argsJson, List<String> availableKbIds,
            Map<String, ToolCatalogItemDto> boundToolsByFnName) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = argsJson == null || argsJson.isBlank()
                    ? Collections.emptyMap()
                    : objectMapper.readValue(argsJson, Map.class);

            String kbResult = executeKbTool(name, args, availableKbIds);
            if (kbResult != null) return kbResult;

            ToolCatalogItemDto bound = boundToolsByFnName != null ? boundToolsByFnName.get(name) : null;
            if (bound != null) return executeBoundTool(bound, args);

            return "（未知工具: " + name + "）";
        } catch (Exception e) {
            log.warn("Tool execution failed: tool={}, error={}", name, e.getMessage());
            return "（工具执行失败: " + e.getMessage() + "）";
        }
    }

    private String executeKbTool(String name, Map<String, Object> args, List<String> availableKbIds) {
        return switch (name) {
            case "query_kb" -> executeQueryKb(args, availableKbIds);
            case "read_document" -> executeReadDocument(args);
            case "kb_structure_scan" -> executeKbStructureScan(args, availableKbIds);
            case "sql_query_safe" -> executeSqlQuerySafe(args);
            case "list_knowledge_bases" -> executeListKnowledgeBases();
            case "list_documents" -> executeListDocuments(args, availableKbIds);
            case "get_document_metadata" -> executeGetDocumentMetadata(args);
            case "get_kb_info" -> executeGetKbInfo(args, availableKbIds);
            case "list_knowledge_graphs" -> executeListKnowledgeGraphs();
            case "get_graph_info" -> executeGetGraphInfo(args);
            case "list_graph_entities" -> executeListGraphEntities(args);
            case "search_graph_entities" -> executeSearchGraphEntities(args);
            default -> null;
        };
    }

    private String executeBoundTool(ToolCatalogItemDto tool, Map<String, Object> args) {
        String toolId = tool.getToolId();
        if (toolId == null) return "（工具缺少 toolId）";

        if (toolId.startsWith("skill:")) {
            Long skillId = parseLong(toolId.substring("skill:".length()));
            if (skillId == null) return "（无效的 skill id: " + toolId + "）";
            if (skillService == null) return "（技能执行服务未注入，无法运行 " + tool.getDisplayName() + "）";
            try {
                Map<String, Object> output = skillService.executeSkill(
                        skillId, args != null ? args : Collections.emptyMap());
                return output != null
                        ? objectMapper.writeValueAsString(output)
                        : "（技能返回空结果）";
            } catch (Exception e) {
                log.warn("Skill execution failed: skillId={}, error={}", skillId, e.getMessage());
                return "（技能执行失败: " + e.getMessage() + "）";
            }
        }
        if (toolId.startsWith("mcp:")) {
            Long mcpId = parseLong(toolId.substring("mcp:".length()));
            if (mcpId == null) return "（无效的 mcp id: " + toolId + "）";
            return executeMcpTool(mcpId, tool.getDisplayName(), args);
        }
        return "（工具 " + toolId + " 未注册执行器，已作为占位返回）";
    }

    @SuppressWarnings("unchecked")
    String executeMcpTool(Long mcpId, String displayName, Map<String, Object> args) {
        Optional<McpService> serviceOpt = mcpServiceRepository.findById(mcpId);
        if (serviceOpt.isEmpty()) {
            return "（MCP 服务不存在: " + mcpId + "）";
        }
        McpService service = serviceOpt.get();
        if (!Boolean.TRUE.equals(service.getEnabled())) {
            return "（MCP 服务已禁用: " + service.getName() + "）";
        }
        if (service.getStatus() != McpService.McpStatus.CONNECTED) {
            return "（MCP 服务未连接: " + service.getName() + "）";
        }

        String label = displayName != null ? displayName : service.getName();
        Object toolNameRaw = args != null ? args.get("toolName") : null;
        if (toolNameRaw == null || String.valueOf(toolNameRaw).isBlank()) {
            return "（MCP 服务 " + label + " 调用缺少 toolName 参数）";
        }
        String toolName = String.valueOf(toolNameRaw);

        Map<String, Object> toolArgs = Collections.emptyMap();
        Object argumentsRaw = args.get("arguments");
        if (argumentsRaw instanceof Map<?, ?> map) {
            toolArgs = (Map<String, Object>) map;
        }

        try {
            return aiEngineMcpClient.callTool(service.getId(), toolName, toolArgs);
        } catch (AiEngineMcpClient.McpToolCallException e) {
            return "（MCP 工具 " + label + "/" + toolName + " 执行失败: " + e.getMessage() + "）";
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignore) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String executeQueryKb(Map<String, Object> args, List<String> availableKbIds) {
        String query = (String) args.get("query");
        List<String> requestedKbIds = (List<String>) args.getOrDefault("kb_ids", availableKbIds);

        List<String> allowedKbIds = requestedKbIds.stream()
                .filter(availableKbIds::contains)
                .toList();

        if (query == null || query.isBlank() || allowedKbIds.isEmpty()) {
            return "没有可检索的知识库或查询词为空。";
        }

        StringBuilder sb = new StringBuilder();
        for (String kbId : allowedKbIds) {
            int topK = retrievalTopK != null ? retrievalTopK : 5;
            String rerankModel = Boolean.TRUE.equals(retrievalEnableRerank) ? retrievalRerankModel : null;
            List<VectorStoreProvider.SearchResult> results =
                    retrievalService.hybridSearch(
                            kbId,
                            query,
                            topK,
                            retrievalEmbeddingModel,
                            retrievalAlpha,
                            retrievalThreshold,
                            rerankModel);
            for (VectorStoreProvider.SearchResult r : results) {
                Map<String, Object> meta = r.getMetadata();
                String docId = meta != null ? String.valueOf(meta.getOrDefault("doc_id", "")) : "";
                String title = meta != null ? String.valueOf(meta.getOrDefault("title", "")) : "";
                sb.append("[doc_id=").append(docId)
                  .append(", title=").append(title)
                  .append(", score=").append(String.format("%.3f", r.getScore()))
                  .append("]\n")
                  .append(r.getContent())
                  .append("\n\n");
            }
        }

        return sb.isEmpty() ? "未检索到相关内容。" : sb.toString().trim();
    }

    private String executeReadDocument(Map<String, Object> args) {
        String docId = (String) args.get("doc_id");
        if (docId == null || docId.isBlank()) return "缺少 doc_id 参数。";

        Optional<KnowledgeDocument> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) return "文档不存在: " + docId;

        KnowledgeDocument doc = docOpt.get();

        if (doc.getParsedTextPath() != null) {
            try {
                String content = Files.readString(Path.of(doc.getParsedTextPath()));
                return "# " + doc.getFileName() + "\n\n" + content;
            } catch (Exception e) {
                log.warn("读取解析文本失败: path={}, error={}", doc.getParsedTextPath(), e.getMessage());
            }
        }

        if (doc.getContentPreview() != null && !doc.getContentPreview().isBlank()) {
            return "# " + doc.getFileName() + "\n\n（仅有预览内容）\n\n" + doc.getContentPreview();
        }

        return "文档 \"" + doc.getFileName() + "\" 尚未完成解析，无法读取全文。";
    }

    @SuppressWarnings("unchecked")
    private String executeKbStructureScan(Map<String, Object> args, List<String> availableKbIds) {
        List<String> requested = (List<String>) args.getOrDefault("kb_ids", availableKbIds);
        List<String> targetKbIds = requested.stream().filter(availableKbIds::contains).toList();
        if (targetKbIds.isEmpty()) {
            return "未提供有效 kb_ids。";
        }

        StringBuilder sb = new StringBuilder();
        for (String kbId : targetKbIds) {
            long docCount = documentRepository.countByKnowledgeBaseId(kbId);
            sb.append("- kb_id=").append(kbId)
                    .append(", documents=").append(docCount)
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private String executeSqlQuerySafe(Map<String, Object> args) {
        String sql = String.valueOf(args.getOrDefault("sql", "")).trim();
        if (sql.isEmpty()) {
            return "缺少 sql 参数。";
        }
        if (!sql.toLowerCase().startsWith("select")) {
            return "只允许只读 SQL（SELECT）。";
        }
        return "sql_query_safe 当前为预览模式：已通过只读校验，可接入白名单数据源后执行。SQL=" + sql;
    }

    private String executeListKnowledgeBases() {
        List<KnowledgeBase> bases = knowledgeBaseRepository.findAll();
        if (bases.isEmpty()) {
            return "系统中尚无知识库。";
        }
        StringBuilder sb = new StringBuilder();
        for (KnowledgeBase kb : bases) {
            long docCount = documentRepository.countByKnowledgeBaseId(kb.getId());
            sb.append("- kb_id=").append(kb.getId())
              .append(", name=").append(kb.getName() != null ? kb.getName() : "")
              .append(", type=").append(kb.getType() != null ? kb.getType().name() : "")
              .append(", doc_count=").append(docCount)
              .append(", status=").append(kb.getStatus() != null ? kb.getStatus() : "")
              .append("\n");
        }
        return sb.toString().trim();
    }

    private String executeListDocuments(Map<String, Object> args, List<String> availableKbIds) {
        String kbId = (String) args.get("kb_id");
        if (kbId == null || kbId.isBlank()) {
            return "缺少 kb_id 参数。";
        }
        if (!availableKbIds.contains(kbId)) {
            return "无效的 kb_id 或无权访问该知识库。";
        }
        int page = 1;
        int pageSize = 20;
        Object pageArg = args.get("page");
        if (pageArg instanceof Number) {
            page = Math.max(1, ((Number) pageArg).intValue());
        }
        Object sizeArg = args.get("page_size");
        if (sizeArg instanceof Number) {
            pageSize = Math.min(100, Math.max(1, ((Number) sizeArg).intValue()));
        }

        var docs = documentRepository.findByKnowledgeBaseIdOrderByUploadTimeDesc(kbId);
        long total = documentRepository.countByKnowledgeBaseId(kbId);
        int totalPages = (int) Math.ceil((double) total / pageSize);

        if (docs.isEmpty()) {
            return "知识库 " + kbId + " 中暂无文档。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("知识库 ").append(kbId).append(" 文档列表（第 ").append(page).append("/").append(totalPages).append(" 页，共 ").append(total).append(" 份）：\n");
        for (KnowledgeDocument doc : docs) {
            sb.append("- doc_id=").append(doc.getId())
              .append(", file_name=").append(doc.getFileName() != null ? doc.getFileName() : "")
              .append(", file_type=").append(doc.getFileType() != null ? doc.getFileType() : "")
              .append(", parse_status=").append(doc.getParseStatus() != null ? doc.getParseStatus() : "")
              .append(", vector_status=").append(doc.getVectorStatus() != null ? doc.getVectorStatus() : "")
              .append(", chunk_count=").append(doc.getChunkCount() != null ? doc.getChunkCount() : 0)
              .append(", upload_time=").append(doc.getUploadTime() != null ? doc.getUploadTime().toString() : "")
              .append("\n");
        }
        return sb.toString().trim();
    }

    private String executeGetDocumentMetadata(Map<String, Object> args) {
        String docId = (String) args.get("doc_id");
        if (docId == null || docId.isBlank()) {
            return "缺少 doc_id 参数。";
        }
        Optional<KnowledgeDocument> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return "文档不存在: " + docId;
        }
        KnowledgeDocument doc = docOpt.get();
        return "文档元数据:\n" +
               "- doc_id: " + doc.getId() + "\n" +
               "- kb_id: " + doc.getKnowledgeBaseId() + "\n" +
               "- file_name: " + nullToEmpty(doc.getFileName()) + "\n" +
               "- file_type: " + nullToEmpty(doc.getFileType()) + "\n" +
               "- file_size: " + (doc.getFileSize() != null ? doc.getFileSize() + " bytes" : "") + "\n" +
               "- file_category: " + nullToEmpty(doc.getFileCategory()) + "\n" +
               "- media_type: " + nullToEmpty(doc.getMediaType()) + "\n" +
               "- parse_status: " + nullToEmpty(doc.getParseStatus()) + "\n" +
               "- vector_status: " + nullToEmpty(doc.getVectorStatus()) + "\n" +
               "- chunk_count: " + (doc.getChunkCount() != null ? doc.getChunkCount() : 0) + "\n" +
               "- char_count: " + (doc.getCharCount() != null ? doc.getCharCount() : 0) + "\n" +
               "- chunk_method: " + nullToEmpty(doc.getChunkMethod()) + "\n" +
               "- upload_time: " + (doc.getUploadTime() != null ? doc.getUploadTime().toString() : "") + "\n" +
               "- last_modified: " + (doc.getLastModified() != null ? doc.getLastModified().toString() : "") + "\n" +
               "- content_preview: " + nullToEmpty(doc.getContentPreview());
    }

    private String executeGetKbInfo(Map<String, Object> args, List<String> availableKbIds) {
        String kbId = (String) args.get("kb_id");
        if (kbId == null || kbId.isBlank()) {
            return "缺少 kb_id 参数。";
        }
        if (!availableKbIds.contains(kbId)) {
            return "无效的 kb_id 或无权访问该知识库。";
        }
        Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findById(kbId);
        if (kbOpt.isEmpty()) {
            return "知识库不存在: " + kbId;
        }
        KnowledgeBase kb = kbOpt.get();
        long docCount = documentRepository.countByKnowledgeBaseId(kb.getId());
        Map<String, Object> retrievalConfig = knowledgeManageService.getRetrievalConfig(kbId);

        StringBuilder sb = new StringBuilder();
        sb.append("知识库信息:\n");
        sb.append("- kb_id: ").append(kb.getId()).append("\n");
        sb.append("- name: ").append(nullToEmpty(kb.getName())).append("\n");
        sb.append("- type: ").append(kb.getType() != null ? kb.getType().name() : "").append("\n");
        sb.append("- description: ").append(nullToEmpty(kb.getDescription())).append("\n");
        sb.append("- status: ").append(nullToEmpty(kb.getStatus())).append("\n");
        sb.append("- doc_count: ").append(docCount).append("\n");
        sb.append("- total_size_mb: ").append(kb.getTotalSizeMb() != null ? kb.getTotalSizeMb() : "").append("\n");
        sb.append("- parsing_enabled: ").append(kb.getParsingEnabled()).append("\n");
        sb.append("- chunk_size: ").append(kb.getChunkSize() != null ? kb.getChunkSize() : "系统默认").append("\n");
        sb.append("- chunk_overlap: ").append(kb.getChunkOverlap() != null ? kb.getChunkOverlap() : "系统默认").append("\n");
        if (retrievalConfig != null) {
            retrievalConfig.forEach((k, v) -> sb.append("- retrieval.").append(k).append(": ").append(v).append("\n"));
        }
        return sb.toString().trim();
    }

    private String nullToEmpty(Object val) {
        return val != null ? val.toString() : "";
    }

    private String executeListKnowledgeGraphs() {
        List<KnowledgeGraph> graphs = knowledgeGraphRepository.findAll();
        if (graphs.isEmpty()) {
            return "系统中尚无知识图谱。";
        }
        StringBuilder sb = new StringBuilder();
        for (KnowledgeGraph g : graphs) {
            long entityCount = graphEntityRepository.countByGraphId(g.getId());
            long relationCount = graphRelationRepository.countByGraphId(g.getId());
            sb.append("- graph_id=").append(g.getId())
              .append(", name=").append(nullToEmpty(g.getName()))
              .append(", kb_id=").append(nullToEmpty(g.getKnowledgeBaseId()))
              .append(", build_status=").append(g.getBuildStatus() != null ? g.getBuildStatus().name() : "")
              .append(", entities=").append(entityCount)
              .append(", relations=").append(relationCount)
              .append("\n");
        }
        return sb.toString().trim();
    }

    private String executeGetGraphInfo(Map<String, Object> args) {
        String graphId = (String) args.get("graph_id");
        if (graphId == null || graphId.isBlank()) {
            return "缺少 graph_id 参数。";
        }
        Optional<KnowledgeGraph> gOpt = knowledgeGraphRepository.findById(graphId);
        if (gOpt.isEmpty()) {
            return "图谱不存在: " + graphId;
        }
        KnowledgeGraph g = gOpt.get();
        long entityCount = graphEntityRepository.countByGraphId(graphId);
        long relationCount = graphRelationRepository.countByGraphId(graphId);
        return "图谱信息:\n" +
               "- graph_id: " + g.getId() + "\n" +
               "- name: " + nullToEmpty(g.getName()) + "\n" +
               "- description: " + nullToEmpty(g.getDescription()) + "\n" +
               "- kb_id: " + nullToEmpty(g.getKnowledgeBaseId()) + "\n" +
               "- build_status: " + (g.getBuildStatus() != null ? g.getBuildStatus().name() : "") + "\n" +
               "- entity_count: " + entityCount + "\n" +
               "- relation_count: " + relationCount + "\n" +
               "- last_build_at: " + (g.getLastBuildAt() != null ? g.getLastBuildAt().toString() : "") + "\n" +
               "- last_success_build_at: " + (g.getLastSuccessBuildAt() != null ? g.getLastSuccessBuildAt().toString() : "") + "\n" +
               "- error_message: " + nullToEmpty(g.getErrorMessage());
    }

    private String executeListGraphEntities(Map<String, Object> args) {
        String graphId = (String) args.get("graph_id");
        if (graphId == null || graphId.isBlank()) {
            return "缺少 graph_id 参数。";
        }
        int page = 1;
        int pageSize = 20;
        Object pageArg = args.get("page");
        if (pageArg instanceof Number) {
            page = Math.max(1, ((Number) pageArg).intValue());
        }
        Object sizeArg = args.get("page_size");
        if (sizeArg instanceof Number) {
            pageSize = Math.min(100, Math.max(1, ((Number) sizeArg).intValue()));
        }

        var pageable = PageRequest.of(page - 1, pageSize);
        var entities = graphEntityRepository.findByGraphId(graphId, pageable);
        long total = graphEntityRepository.countByGraphId(graphId);
        int totalPages = (int) Math.ceil((double) total / pageSize);

        if (entities.isEmpty()) {
            return "图谱 " + graphId + " 中尚无实体。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("图谱 ").append(graphId).append(" 实体列表（第 ").append(page).append("/").append(totalPages).append(" 页，共 ").append(total).append(" 个）：\n");
        for (GraphEntity e : entities) {
            sb.append("- entity_id=").append(e.getId())
              .append(", name=").append(nullToEmpty(e.getName()))
              .append(", type=").append(nullToEmpty(e.getEntityType()))
              .append(", description=").append(truncate(e.getDescription(), 80))
              .append("\n");
        }
        return sb.toString().trim();
    }

    private String executeSearchGraphEntities(Map<String, Object> args) {
        String graphId = (String) args.get("graph_id");
        String keyword = (String) args.get("keyword");
        if (graphId == null || graphId.isBlank()) {
            return "缺少 graph_id 参数。";
        }
        if (keyword == null || keyword.isBlank()) {
            return "缺少 keyword 参数。";
        }
        List<GraphEntity> entities = graphEntityRepository.findByGraphIdAndNameContainingIgnoreCase(graphId, keyword);
        if (entities.isEmpty()) {
            return "图谱 " + graphId + " 中未找到名称含「" + keyword + "」的实体。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("在图谱 ").append(graphId).append(" 中找到 ").append(entities.size()).append(" 个匹配的实体：\n");
        for (GraphEntity e : entities) {
            sb.append("- entity_id=").append(e.getId())
              .append(", name=").append(nullToEmpty(e.getName()))
              .append(", type=").append(nullToEmpty(e.getEntityType()))
              .append(", description=").append(truncate(e.getDescription(), 80))
              .append("\n");
        }
        return sb.toString().trim();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMessage(Map<String, Object> respMap) {
        if (respMap == null || respMap.isEmpty()) {
            return null;
        }

        Object choicesObj = respMap.get("choices");
        if (choicesObj instanceof List<?> choices && !choices.isEmpty()) {
            Object first = choices.get(0);
            if (first instanceof Map<?, ?> firstMapAny) {
                Map<String, Object> firstMap = (Map<String, Object>) firstMapAny;
                Object messageObj = firstMap.get("message");
                if (messageObj instanceof Map<?, ?> msgMapAny) {
                    Map<String, Object> msgMap = new HashMap<>((Map<String, Object>) msgMapAny);
                    Object altToolCalls = msgMap.get("toolCalls");
                    if (msgMap.get("tool_calls") == null && altToolCalls instanceof List<?>) {
                        msgMap.put("tool_calls", altToolCalls);
                    }
                    return msgMap;
                }
                if (messageObj instanceof String msgStr) {
                    return Map.of("content", msgStr);
                }
                if (firstMap.get("text") instanceof String text) {
                    return Map.of("content", text);
                }
                if (firstMap.get("content") instanceof String content) {
                    return Map.of("content", content);
                }
            }
        }

        Object messageObj = respMap.get("message");
        if (messageObj instanceof Map<?, ?> msgMapAny) {
            Map<String, Object> msgMap = new HashMap<>((Map<String, Object>) msgMapAny);
            Object altToolCalls = msgMap.get("toolCalls");
            if (msgMap.get("tool_calls") == null && altToolCalls instanceof List<?>) {
                msgMap.put("tool_calls", altToolCalls);
            }
            return msgMap;
        }
        if (messageObj instanceof String msgStr) {
            return Map.of("content", msgStr);
        }
        if (respMap.get("content") instanceof String content) {
            return Map.of("content", content);
        }
        return null;
    }

    private String extractResponseError(Map<String, Object> respMap) {
        if (respMap == null || respMap.isEmpty()) {
            return "";
        }
        Object err = respMap.get("error");
        if (err instanceof Map<?, ?> errMap) {
            Object msg = errMap.get("message");
            if (msg != null) return String.valueOf(msg);
            return String.valueOf(errMap);
        }
        if (err != null) return String.valueOf(err);
        Object msg = respMap.get("errorMessage");
        if (msg != null) return String.valueOf(msg);
        Object status = respMap.get("status");
        if (status != null && !"ok".equalsIgnoreCase(String.valueOf(status))) {
            return "status=" + status;
        }
        return "";
    }

    private String extractModel(Map<String, Object> respMap) {
        return (String) respMap.getOrDefault("model", "");
    }

    private Map<String, Object> result(String content, String model, int promptTokens, int completionTokens, int totalTokens) {
        Map<String, Object> r = new HashMap<>();
        r.put("content", content);
        r.put("model", model);
        r.put("promptTokens", promptTokens);
        r.put("completionTokens", completionTokens);
        r.put("totalTokens", totalTokens > 0 ? totalTokens : (promptTokens + completionTokens));
        r.put("provider", "tool-calling");
        return r;
    }

    @SuppressWarnings("unchecked")
    private UsageMetrics extractUsage(Map<String, Object> respMap) {
        if (respMap == null) {
            return UsageMetrics.ZERO;
        }

        int prompt = 0;
        int completion = 0;
        int total = 0;

        Object usageObj = respMap.get("usage");
        if (usageObj instanceof Map) {
            Map<String, Object> usage = (Map<String, Object>) usageObj;
            prompt = firstNonZero(
                    toInt(usage.get("prompt_tokens")),
                    toInt(usage.get("promptTokens")),
                    toInt(usage.get("prompt_eval_count")));
            completion = firstNonZero(
                    toInt(usage.get("completion_tokens")),
                    toInt(usage.get("completionTokens")),
                    toInt(usage.get("eval_count")));
            total = firstNonZero(
                    toInt(usage.get("total_tokens")),
                    toInt(usage.get("totalTokens")),
                    toInt(usage.get("tokens")));
        }

        prompt = firstNonZero(prompt, toInt(respMap.get("prompt_eval_count")));
        completion = firstNonZero(completion, toInt(respMap.get("eval_count")));
        total = firstNonZero(total, toInt(respMap.get("total_tokens")), toInt(respMap.get("tokens")));

        if (total <= 0) {
            total = prompt + completion;
        }
        return new UsageMetrics(prompt, completion, total);
    }

    private int firstNonZero(int... values) {
        for (int value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignore) {
            return 0;
        }
    }

    private record UsageMetrics(int prompt, int completion, int total) {
        private static final UsageMetrics ZERO = new UsageMetrics(0, 0, 0);
    }

    private void emitTrace(Consumer<ToolTrace> traceConsumer, ToolTrace trace) {
        if (traceConsumer == null || trace == null) {
            return;
        }
        try {
            traceConsumer.accept(trace);
        } catch (Exception e) {
            log.warn("Emit tool trace failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgsSafe(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(argsJson, Map.class);
        } catch (Exception e) {
            return Map.of("raw", argsJson);
        }
    }

    private String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace("\n", " ").trim();
        return normalized.length() > 180 ? normalized.substring(0, 180) + "..." : normalized;
    }
}
