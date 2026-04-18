package com.adlin.orin.modules.conversation.strategy;

import com.adlin.orin.modules.conversation.dto.ChatMessageResponse.ToolTrace;
import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.service.RetrievalService;
import com.adlin.orin.modules.model.service.OllamaIntegrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public Map<String, Object> execute(
            String endpoint, String apiKey, String model,
            String systemPrompt, String userMessage,
            List<String> kbIds,
            double temperature, int maxTokens,
            Consumer<ToolTrace> traceConsumer) {

        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        List<Map<String, Object>> tools = KbToolDefinitions.build(kbIds);
        int totalToolCalls = 0;

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
                return result("（模型未返回响应）", "");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = (Map<String, Object>) resp.get();

            Map<String, Object> message = extractMessage(respMap);
            if (message == null) {
                emitTrace(traceConsumer, ToolTrace.builder()
                        .type("KB_MODEL_TOOL_FINAL")
                        .kbId("multiple")
                        .message("无法解析模型响应，工具调用流程提前结束")
                        .status("error")
                        .durationMs(0L)
                        .detail(Map.of("round", round + 1, "totalToolCalls", totalToolCalls))
                        .build());
                return result("（无法解析模型响应）", "");
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
                return result(content != null ? content : "（模型未返回正文）", extractModel(respMap));
            }

            for (Map<String, Object> toolCall : toolCalls) {
                long toolStart = System.currentTimeMillis();
                String toolCallId = (String) toolCall.get("id");
                @SuppressWarnings("unchecked")
                Map<String, Object> fn = (Map<String, Object>) toolCall.get("function");
                if (fn == null) continue;

                String fnName = (String) fn.get("name");
                String argsJson = (String) fn.get("arguments");
                String toolResult = executeTool(fnName, argsJson, kbIds);
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
        return result("（达到最大工具调用轮数，未能生成最终回答）", "");
    }

    private String executeTool(String name, String argsJson, List<String> availableKbIds) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = objectMapper.readValue(argsJson, Map.class);
            return switch (name) {
                case "query_kb" -> executeQueryKb(args, availableKbIds);
                case "read_document" -> executeReadDocument(args);
                default -> "（未知工具: " + name + "）";
            };
        } catch (Exception e) {
            log.warn("Tool execution failed: tool={}, error={}", name, e.getMessage());
            return "（工具执行失败: " + e.getMessage() + "）";
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
            List<VectorStoreProvider.SearchResult> results =
                    retrievalService.hybridSearch(kbId, query, 5);
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
    private Map<String, Object> extractMessage(Map<String, Object> respMap) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
        if (choices == null || choices.isEmpty()) return null;
        return (Map<String, Object>) choices.get(0).get("message");
    }

    private String extractModel(Map<String, Object> respMap) {
        return (String) respMap.getOrDefault("model", "");
    }

    private Map<String, Object> result(String content, String model) {
        Map<String, Object> r = new HashMap<>();
        r.put("content", content);
        r.put("model", model);
        r.put("promptTokens", 0);
        r.put("completionTokens", 0);
        r.put("provider", "tool-calling");
        return r;
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
