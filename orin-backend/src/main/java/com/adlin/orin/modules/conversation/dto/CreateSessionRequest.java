package com.adlin.orin.modules.conversation.dto;

import lombok.Data;

@Data
public class CreateSessionRequest {
    private String agentId;
    private String title;
}
