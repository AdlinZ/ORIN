package com.adlin.orin.modules.conversation.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatMessageRequest {
    private String message;
    private List<String> kbIds;
}

@Data
public class ChatMessageResponse {
    private String content;
    private List<RetrievedChunk> retrievedChunks;
    private Long tokens;
}

@Data
public class RetrievedChunk {
    private String source;
    private String content;
    private Double score;
}

@Data
public class CreateSessionRequest {
    private String agentId;
    private String title;
}

@Data
public class SessionResponse {
    private String id;
    private String agentId;
    private String title;
    private String createdAt;
}