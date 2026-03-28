package com.adlin.orin.modules.conversation.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatMessageRequest {
    private String message;
    private List<String> kbIds;
}
