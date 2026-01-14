package com.adlin.orin.modules.monitor.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * 智能体健康状态聚合实体
 * 用于列表页快速展示
 */
@Entity
@Table(name = "agent_health_status")
public class AgentHealthStatus {

    /**
     * 关联的智能体 ID (来自 Dify)
     * 同时作为主键，因为一个 Agent 只有一个实时状态
     */
    @Id
    private String agentId;

    /**
     * 智能体名称 (冗余字段，方便查询)
     */
    private String agentName;

    /**
     * 健康评分 (0-100)
     */
    private Integer healthScore;

    /**
     * 运行状态
     */
    @Enumerated(EnumType.STRING)
    private Status status;

    /**
     * 最后心跳/更新时间
     */
    private Long lastHeartbeat;

    /**
     * Provider 类型 (冗余自 Metadata，方便前端渲染)
     */
    private String providerType;

    /**
     * 运行模式 (冗余自 Metadata，方便前端渲染)
     */
    private String mode;

    /**
     * 模型名称 (冗余自 Metadata，方便前端渲染)
     */
    private String modelName;

    public enum Status {
        RUNNING,
        STOPPED,
        HIGH_LOAD, // 高负载
        ERROR, // 异常
        UNKNOWN // 未知
    }

    public AgentHealthStatus() {
    }

    public AgentHealthStatus(String agentId, String agentName, Integer healthScore, Status status, Long lastHeartbeat,
            String providerType, String mode, String modelName) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.healthScore = healthScore;
        this.status = status;
        this.lastHeartbeat = lastHeartbeat;
        this.providerType = providerType;
        this.mode = mode;
        this.modelName = modelName;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public Integer getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(Integer healthScore) {
        this.healthScore = healthScore;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public static AgentHealthStatusBuilder builder() {
        return new AgentHealthStatusBuilder();
    }

    public static class AgentHealthStatusBuilder {
        private String agentId;
        private String agentName;
        private Integer healthScore;
        private Status status;
        private Long lastHeartbeat;
        private String providerType;
        private String mode;
        private String modelName;

        public AgentHealthStatusBuilder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public AgentHealthStatusBuilder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public AgentHealthStatusBuilder healthScore(Integer healthScore) {
            this.healthScore = healthScore;
            return this;
        }

        public AgentHealthStatusBuilder status(Status status) {
            this.status = status;
            return this;
        }

        public AgentHealthStatusBuilder lastHeartbeat(Long lastHeartbeat) {
            this.lastHeartbeat = lastHeartbeat;
            return this;
        }

        public AgentHealthStatusBuilder providerType(String providerType) {
            this.providerType = providerType;
            return this;
        }

        public AgentHealthStatusBuilder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public AgentHealthStatusBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public AgentHealthStatus build() {
            return new AgentHealthStatus(agentId, agentName, healthScore, status, lastHeartbeat, providerType, mode,
                    modelName);
        }
    }
}
