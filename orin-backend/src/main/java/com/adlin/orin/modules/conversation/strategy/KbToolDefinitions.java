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
                buildReadDocumentTool(),
                buildKbStructureScanTool(kbIds),
                buildSqlQuerySafeTool(),
                buildListKnowledgeBasesTool(),
                buildListDocumentsTool(kbIds),
                buildGetDocumentMetadataTool(),
                buildGetKbInfoTool(kbIds),
                buildListKnowledgeGraphsTool(),
                buildGetGraphInfoTool(),
                buildListGraphEntitiesTool(),
                buildSearchGraphEntitiesTool()
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

    private static Map<String, Object> buildKbStructureScanTool(List<String> kbIds) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "kb_structure_scan",
                        "description", "扫描知识库结构并返回每个知识库的文档数量等元信息。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "kb_ids", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "string"),
                                                "description", "要扫描的知识库 ID 列表，可用值: " + kbIds
                                        )
                                ),
                                "required", List.of("kb_ids")
                        )
                )
        );
    }

    private static Map<String, Object> buildSqlQuerySafeTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "sql_query_safe",
                        "description", "只读 SQL 查询工具（白名单模式，当前版本仅返回执行建议）。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "sql", Map.of(
                                                "type", "string",
                                                "description", "待执行的 SQL（仅允许 SELECT）"
                                        )
                                ),
                                "required", List.of("sql")
                        )
                )
        );
    }

    private static Map<String, Object> buildListKnowledgeBasesTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "list_knowledge_bases",
                        "description", "列出系统中所有可用的知识库，返回知识库 ID、名称、描述、文档数量等信息。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of()
                        )
                )
        );
    }

    private static Map<String, Object> buildListDocumentsTool(List<String> kbIds) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "list_documents",
                        "description", "列出指定知识库中的所有文档（支持分页），返回文档 ID、文件名、状态等概要信息。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "kb_id", Map.of(
                                                "type", "string",
                                                "description", "知识库 ID，可用值: " + kbIds
                                        ),
                                        "page", Map.of(
                                                "type", "integer",
                                                "description", "页码，从 1 开始，默认 1"
                                        ),
                                        "page_size", Map.of(
                                                "type", "integer",
                                                "description", "每页数量，默认 20，最大 100"
                                        )
                                ),
                                "required", List.of("kb_id")
                        )
                )
        );
    }

    private static Map<String, Object> buildGetDocumentMetadataTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_document_metadata",
                        "description", "获取指定文档的元数据信息（不含全文），包括文件名、类型、大小、解析状态、向量化状态、分块数量、上传时间等。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "doc_id", Map.of(
                                                "type", "string",
                                                "description", "文档 ID"
                                        )
                                ),
                                "required", List.of("doc_id")
                        )
                )
        );
    }

    private static Map<String, Object> buildGetKbInfoTool(List<String> kbIds) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_kb_info",
                        "description", "获取指定知识库的详细配置信息，包括名称、描述、检索参数（topK、alpha、similarityThreshold）、分块配置等。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "kb_id", Map.of(
                                                "type", "string",
                                                "description", "知识库 ID，可用值: " + kbIds
                                        )
                                ),
                                "required", List.of("kb_id")
                        )
                )
        );
    }

    private static Map<String, Object> buildListKnowledgeGraphsTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "list_knowledge_graphs",
                        "description", "列出系统中所有知识图谱，返回图谱 ID、名称、描述、实体数、关系数、构建状态等信息。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of()
                        )
                )
        );
    }

    private static Map<String, Object> buildGetGraphInfoTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_graph_info",
                        "description", "获取指定知识图谱的详细信息，包括名称、描述、关联知识库、构建状态、实体数、关系数、最后构建时间等。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "graph_id", Map.of(
                                                "type", "string",
                                                "description", "图谱 ID"
                                        )
                                ),
                                "required", List.of("graph_id")
                        )
                )
        );
    }

    private static Map<String, Object> buildListGraphEntitiesTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "list_graph_entities",
                        "description", "列出指定知识图谱中的实体（节点），支持分页。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "graph_id", Map.of(
                                                "type", "string",
                                                "description", "图谱 ID"
                                        ),
                                        "page", Map.of(
                                                "type", "integer",
                                                "description", "页码，从 1 开始，默认 1"
                                        ),
                                        "page_size", Map.of(
                                                "type", "integer",
                                                "description", "每页数量，默认 20，最大 100"
                                        )
                                ),
                                "required", List.of("graph_id")
                        )
                )
        );
    }

    private static Map<String, Object> buildSearchGraphEntitiesTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "search_graph_entities",
                        "description", "在指定知识图谱中按名称关键词搜索实体，返回匹配的实体列表。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "graph_id", Map.of(
                                                "type", "string",
                                                "description", "图谱 ID"
                                        ),
                                        "keyword", Map.of(
                                                "type", "string",
                                                "description", "实体名称关键词（不区分大小写）"
                                        )
                                ),
                                "required", List.of("graph_id", "keyword")
                        )
                )
        );
    }
}
