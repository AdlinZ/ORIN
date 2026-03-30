package com.adlin.orin.modules.conversation.tool;

import com.adlin.orin.modules.conversation.dto.ChatMessageResponse.ToolTrace;
import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import com.adlin.orin.modules.knowledge.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class KbSearchTool implements AgentTool {
    private static final String TOOL_NAME = "KB_SEARCH";

    private final RetrievalService retrievalService;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolTrace execute(ToolExecutionContext ctx) {
        long start = System.currentTimeMillis();
        List<String> kbIds = ctx.getKbIds();
        Map<String, List<String>> docFilters = ctx.getKbDocFilters();
        String query = ctx.getQuery();

        Map<String, Object> detail = new HashMap<>();
        int totalResults = 0;

        try {
            if (kbIds == null || kbIds.isEmpty()) {
                return ToolTrace.builder()
                        .type(TOOL_NAME)
                        .kbId(null)
                        .message("未配置知识库检索")
                        .status("success")
                        .durationMs(System.currentTimeMillis() - start)
                        .detail(detail)
                        .build();
            }

            List<Map<String, Object>> kbResults = new ArrayList<>();

            for (String kbId : kbIds) {
                List<String> docIds = docFilters != null ? docFilters.get(kbId) : null;
                List<VectorStoreProvider.SearchResult> results =
                        retrievalService.hybridSearch(kbId, query, 5, docIds);

                // Build structured results for KbRetrieveTool
                List<Map<String, Object>> structuredResults = new ArrayList<>();
                for (VectorStoreProvider.SearchResult r : results) {
                    Map<String, Object> meta = r.getMetadata();
                    Map<String, Object> structured = new HashMap<>();
                    structured.put("content", r.getContent());
                    structured.put("score", r.getScore());
                    structured.put("docId", meta != null ? meta.get("doc_id") : null);
                    structured.put("title", meta != null ? meta.get("title") : null);
                    structured.put("source", meta != null ? meta.get("source") : null);
                    structuredResults.add(structured);
                }

                Map<String, Object> kbResult = new HashMap<>();
                kbResult.put("kbId", kbId);
                kbResult.put("resultCount", results.size());
                kbResult.put("documentIds", docIds);
                kbResult.put("results", structuredResults);

                totalResults += results.size();
                kbResults.add(kbResult);

                log.info("KbSearchTool: kbId={}, docIds={}, query={}, results.size={}", kbId, docIds, query, results.size());
                if (!results.isEmpty()) {
                    Object firstContent = results.get(0).getContent();
                    log.info("KbSearchTool 首个结果: kbId={}, content前100字={}", kbId,
                            firstContent != null ? firstContent.toString().substring(0, Math.min(100, firstContent.toString().length())) : "null");
                }
            }

            detail.put("kbResults", kbResults);
            ctx.putSharedState("searchResults", kbResults);
            ctx.putSharedState("totalSearchResults", totalResults);

            String docFilterInfo = "";
            if (docFilters != null && !docFilters.isEmpty()) {
                long filteredKbs = docFilters.values().stream().filter(v -> v != null && !v.isEmpty()).count();
                if (filteredKbs > 0) {
                    docFilterInfo = "（" + filteredKbs + " 个知识库已按文档过滤）";
                }
            }

            return ToolTrace.builder()
                    .type(TOOL_NAME)
                    .kbId(kbIds.size() > 1 ? "multiple" : kbIds.get(0))
                    .message("知识库检索完成，共返回 " + totalResults + " 条结果" + docFilterInfo)
                    .status("success")
                    .durationMs(System.currentTimeMillis() - start)
                    .detail(detail)
                    .build();
        } catch (Exception e) {
            return ToolTrace.builder()
                    .type(TOOL_NAME)
                    .kbId(kbIds != null && !kbIds.isEmpty() ? kbIds.get(0) : null)
                    .message("知识库检索失败: " + e.getMessage())
                    .status("error")
                    .durationMs(System.currentTimeMillis() - start)
                    .detail(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @Override
    public boolean supports(Map<String, List<String>> kbDocFilters) {
        return true;
    }
}
