package com.adlin.orin.modules.conversation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummary {

    private String id;
    private String conversationId;
    private String agentId;
    private String userId;
    private String model;
    private String query;
    private String response;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer cumulativeTokens;  // 累计 tokens
    private Long responseTime;
    private Boolean success;
    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
