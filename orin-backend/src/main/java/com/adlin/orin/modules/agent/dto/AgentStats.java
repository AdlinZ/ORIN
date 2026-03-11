package com.adlin.orin.modules.agent.dto;

import lombok.Data;

/**
 * 智能体统计信息
 */
@Data
public class AgentStats {
    
    private String agentId;
    private String agentName;
    
    // 对话统计
    private Long totalConversations;
    private Long totalMessages;
    private Long avgResponseTimeMs;
    
    // Token 统计
    private Long totalInputTokens;
    private Long totalOutputTokens;
    private Double totalCost;
    
    // 知识库统计
    private Long knowledgeBaseCount;
    private Long documentCount;
    private Long vectorCount;
    
    // 健康状态
    private String healthStatus;
    private String lastActiveTime;
}
