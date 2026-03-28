package com.adlin.orin.modules.conversation.tool;

import com.adlin.orin.modules.conversation.dto.ChatMessageResponse.ToolTrace;

import java.util.List;
import java.util.Map;

public interface AgentTool {
    String getName();

    ToolTrace execute(ToolExecutionContext ctx);

    default boolean supports(Map<String, List<String>> kbDocFilters) {
        return true;
    }
}
