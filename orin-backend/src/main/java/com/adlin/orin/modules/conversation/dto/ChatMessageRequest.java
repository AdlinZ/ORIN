package com.adlin.orin.modules.conversation.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatMessageRequest {
    private String message;
    private List<String> kbIds;

    /** kbId -> documentIds mapping for filtering retrieval scope */
    private Map<String, List<String>> kbDocFilters;
}
