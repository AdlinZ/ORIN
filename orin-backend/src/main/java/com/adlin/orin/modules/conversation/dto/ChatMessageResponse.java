package com.adlin.orin.modules.conversation.dto;

import lombok.Data;

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
}
