package com.adlin.orin.modules.conversation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessageRequest {
    private String message;
    private String fileId;
    private List<String> toolIds;
    private List<String> kbIds;
    private List<Long> skillIds;
    private List<Long> mcpIds;
    private List<Map<String, Object>> conversationContextMessages;

    /** kbId -> documentIds mapping for filtering retrieval scope */
    private Map<String, List<String>> kbDocFilters;
}
