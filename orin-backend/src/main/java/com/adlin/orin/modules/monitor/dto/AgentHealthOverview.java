package com.adlin.orin.modules.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 智能体健康状态概览DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentHealthOverview {

    /**
     * 总智能体数量
     */
    private Integer totalAgents;

    /**
     * 健康智能体数量
     */
    private Integer healthyAgents;

    /**
     * 异常智能体数量
     */
    private Integer unhealthyAgents;

    /**
     * 离线智能体数量
     */
    private Integer offlineAgents;

    /**
     * 健康率百分比
     */
    private Double healthRate;

    /**
     * 各状态智能体列表
     */
    private Map<String, List<AgentHealthItem>> agentsByStatus;

    /**
     * 智能体健康项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentHealthItem {
        private String agentId;
        private String agentName;
        private String status;
        private Long lastHeartbeat;
        private Double cpuUsage;
        private Double memoryUsage;
        private Integer errorCount;
    }
}
