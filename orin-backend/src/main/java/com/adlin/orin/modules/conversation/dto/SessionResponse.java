package com.adlin.orin.modules.conversation.dto;

import lombok.Data;

@Data
public class SessionResponse {
    private String id;
    private String agentId;
    private String title;
    private String createdAt;
}
