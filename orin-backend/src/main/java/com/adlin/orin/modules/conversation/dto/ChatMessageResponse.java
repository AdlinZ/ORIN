package com.adlin.orin.modules.conversation.dto;

import lombok.Data;
import lombok.Builder;

import java.util.List;

@Data
public class ChatMessageResponse {
    private String content;
    private List<RetrievedChunk> retrievedChunks;
    private Long tokens;

    // Usage info
    private Integer promptTokens;
    private Integer completionTokens;
    private String model;
    private String provider;

    // Timestamp
    private String createdAt;

    // Tool execution traces
    private List<ToolTrace> toolTraces;

    @Data
    @Builder
    public static class ToolTrace {
        private String type;
        private String kbId;
        private String message;
        private String status;
        private Long durationMs;
        private Object detail;
    }
}
