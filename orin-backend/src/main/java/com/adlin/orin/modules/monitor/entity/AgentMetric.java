package com.adlin.orin.modules.monitor.entity;

import jakarta.persistence.*;

/**
 * 智能体监控指标实体
 * 记录某个时间点的瞬时状态
 */
@Entity
@Table(name = "agent_metrics", indexes = {
        @Index(name = "idx_agent_time", columnList = "agentId, timestamp")
})
public class AgentMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的智能体 ID (来自 Dify)
     */
    @Column(nullable = false)
    private String agentId;

    /**
     * 数据采集时间戳
     */
    @Column(nullable = false)
    private Long timestamp;

    /**
     * CPU 占用率 (0-100%) - 模拟
     */
    private Double cpuUsage;

    /**
     * 内存占用 (MB) - 模拟
     */
    private Double memoryUsage;

    /**
     * 平均推理延迟 (ms)
     */
    private Integer responseLatency;

    /**
     * Token 消耗量
     */
    private Integer tokenCost;

    /**
     * 当日调用次数
     */
    private Integer dailyRequests;

    public AgentMetric() {
    }

    public AgentMetric(Long id, String agentId, Long timestamp, Double cpuUsage, Double memoryUsage,
            Integer responseLatency, Integer tokenCost, Integer dailyRequests) {
        this.id = id;
        this.agentId = agentId;
        this.timestamp = timestamp;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.responseLatency = responseLatency;
        this.tokenCost = tokenCost;
        this.dailyRequests = dailyRequests;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(Double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public Double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(Double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public Integer getResponseLatency() {
        return responseLatency;
    }

    public void setResponseLatency(Integer responseLatency) {
        this.responseLatency = responseLatency;
    }

    public Integer getTokenCost() {
        return tokenCost;
    }

    public void setTokenCost(Integer tokenCost) {
        this.tokenCost = tokenCost;
    }

    public Integer getDailyRequests() {
        return dailyRequests;
    }

    public void setDailyRequests(Integer dailyRequests) {
        this.dailyRequests = dailyRequests;
    }

    public static AgentMetricBuilder builder() {
        return new AgentMetricBuilder();
    }

    public static class AgentMetricBuilder {
        private Long id;
        private String agentId;
        private Long timestamp;
        private Double cpuUsage;
        private Double memoryUsage;
        private Integer responseLatency;
        private Integer tokenCost;
        private Integer dailyRequests;

        public AgentMetricBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public AgentMetricBuilder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public AgentMetricBuilder timestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public AgentMetricBuilder cpuUsage(Double cpuUsage) {
            this.cpuUsage = cpuUsage;
            return this;
        }

        public AgentMetricBuilder memoryUsage(Double memoryUsage) {
            this.memoryUsage = memoryUsage;
            return this;
        }

        public AgentMetricBuilder responseLatency(Integer responseLatency) {
            this.responseLatency = responseLatency;
            return this;
        }

        public AgentMetricBuilder tokenCost(Integer tokenCost) {
            this.tokenCost = tokenCost;
            return this;
        }

        public AgentMetricBuilder dailyRequests(Integer dailyRequests) {
            this.dailyRequests = dailyRequests;
            return this;
        }

        public AgentMetric build() {
            return new AgentMetric(id, agentId, timestamp, cpuUsage, memoryUsage, responseLatency, tokenCost,
                    dailyRequests);
        }
    }
}
