package com.adlin.orin.modules.conversation.strategy;

import java.util.List;
import java.util.Map;

/**
 * 知识库 tool calling 的 schema 定义（OpenAI 兼容格式）。
 */
public final class KbToolDefinitions {

    private KbToolDefinitions() {}

    public static List<Map<String, Object>> build(List<String> kbIds) {
        return List.of(
                buildQueryKbTool(kbIds),
                buildReadDocumentTool()
        );
    }

    private static Map<String, Object> buildQueryKbTool(List<String> kbIds) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "query_kb",
                        "description", "在知识库中语义检索与问题相关的文档片段。当你需要查找具体知识时调用此工具。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of(
                                                "type", "string",
                                                "description", "检索关键词或问题"
                                        ),
                                        "kb_ids", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "string"),
                                                "description", "要搜索的知识库 ID 列表，可用值: " + kbIds
                                        )
                                ),
                                "required", List.of("query", "kb_ids")
                        )
                )
        );
    }

    private static Map<String, Object> buildReadDocumentTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "read_document",
                        "description", "读取指定文档的完整内容。当 query_kb 返回的片段不够，需要查看全文时使用。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "doc_id", Map.of(
                                                "type", "string",
                                                "description", "文档 ID，来自 query_kb 返回结果中的 doc_id 字段"
                                        )
                                ),
                                "required", List.of("doc_id")
                        )
                )
        );
    }
}
