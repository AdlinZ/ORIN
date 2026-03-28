package com.adlin.orin.modules.conversation.dto;

import lombok.Data;

@Data
public class RetrievedChunk {
    private String source;
    private String content;
    private Double score;
}
