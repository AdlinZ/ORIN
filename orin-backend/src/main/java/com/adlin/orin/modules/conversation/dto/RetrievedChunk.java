package com.adlin.orin.modules.conversation.dto;

import lombok.Data;

@Data
public class RetrievedChunk {
    private String kbId;
    private String source;
    private String content;
    private Double score;
    private String docName;
    private String docId;
}
