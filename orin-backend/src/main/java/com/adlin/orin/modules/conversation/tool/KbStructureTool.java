package com.adlin.orin.modules.conversation.tool;

import com.adlin.orin.modules.conversation.dto.ChatMessageResponse.ToolTrace;
import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class KbStructureTool implements AgentTool {
    private static final String TOOL_NAME = "KB_STRUCTURE";

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolTrace execute(ToolExecutionContext ctx) {
        long start = System.currentTimeMillis();
        List<String> kbIds = ctx.getKbIds();
        Map<String, Object> detail = new HashMap<>();
        int totalDocs = 0;

        try {
            if (kbIds == null || kbIds.isEmpty()) {
                return ToolTrace.builder()
                        .type(TOOL_NAME)
                        .kbId(null)
                        .message("未附加任何知识库")
                        .status("success")
                        .durationMs(System.currentTimeMillis() - start)
                        .detail(detail)
                        .build();
            }

            for (String kbId : kbIds) {
                Map<String, Object> kbInfo = new HashMap<>();
                KnowledgeBase kb = knowledgeBaseRepository.findById(kbId).orElse(null);
                if (kb != null) {
                    kbInfo.put("name", kb.getName());
                    long docCount = documentRepository.countByKnowledgeBaseId(kbId);
                    kbInfo.put("documentCount", docCount);
                    totalDocs += docCount;
                    detail.put(kbId, kbInfo);
                } else {
                    kbInfo.put("error", "知识库不存在");
                    detail.put(kbId, kbInfo);
                }
            }

            ctx.putSharedState("kbStructureChecked", true);
            ctx.putSharedState("totalDocuments", totalDocs);
            ctx.putSharedState("kbStructureDetail", detail);

            return ToolTrace.builder()
                    .type(TOOL_NAME)
                    .kbId(kbIds.size() > 1 ? "multiple" : kbIds.get(0))
                    .message("知识库结构检查完成，共 " + kbIds.size() + " 个知识库，" + totalDocs + " 个文档")
                    .status("success")
                    .durationMs(System.currentTimeMillis() - start)
                    .detail(detail)
                    .build();
        } catch (Exception e) {
            return ToolTrace.builder()
                    .type(TOOL_NAME)
                    .kbId(kbIds != null && !kbIds.isEmpty() ? kbIds.get(0) : null)
                    .message("知识库结构检查失败: " + e.getMessage())
                    .status("error")
                    .durationMs(System.currentTimeMillis() - start)
                    .detail(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
