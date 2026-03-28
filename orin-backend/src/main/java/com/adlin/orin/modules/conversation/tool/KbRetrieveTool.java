package com.adlin.orin.modules.conversation.tool;

import com.adlin.orin.modules.conversation.dto.ChatMessageResponse.ToolTrace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KbRetrieveTool implements AgentTool {
    private static final String TOOL_NAME = "KB_RETRIEVE";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolTrace execute(ToolExecutionContext ctx) {
        long start = System.currentTimeMillis();
        Map<String, Object> sharedState = ctx.getSharedState();

        Map<String, Object> detail = new HashMap<>();
        List<Map<String, Object>> retrievedChunks = new ArrayList<>();

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> searchResults =
                    (List<Map<String, Object>>) sharedState.get("searchResults");

            if (searchResults == null || searchResults.isEmpty()) {
                return ToolTrace.builder()
                        .type(TOOL_NAME)
                        .kbId(null)
                        .message("无检索结果可获取")
                        .status("success")
                        .durationMs(System.currentTimeMillis() - start)
                        .detail(detail)
                        .build();
            }

            int totalChunks = 0;
            for (Map<String, Object> kbResult : searchResults) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results =
                        (List<Map<String, Object>>) kbResult.get("results");
                if (results != null) {
                    for (Map<String, Object> r : results) {
                        Map<String, Object> chunkInfo = new HashMap<>();
                        chunkInfo.put("content", r.get("content"));
                        chunkInfo.put("score", r.get("score"));
                        chunkInfo.put("docId", r.get("docId"));
                        retrievedChunks.add(chunkInfo);
                        ctx.addRetrievedChunk(r);
                        totalChunks++;
                    }
                }
            }

            detail.put("retrievedChunks", retrievedChunks);
            detail.put("totalChunks", totalChunks);

            return ToolTrace.builder()
                    .type(TOOL_NAME)
                    .kbId("multiple")
                    .message("获取到 " + totalChunks + " 个文本块用于生成上下文")
                    .status("success")
                    .durationMs(System.currentTimeMillis() - start)
                    .detail(detail)
                    .build();
        } catch (Exception e) {
            return ToolTrace.builder()
                    .type(TOOL_NAME)
                    .kbId(null)
                    .message("获取检索结果失败: " + e.getMessage())
                    .status("error")
                    .durationMs(System.currentTimeMillis() - start)
                    .detail(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
