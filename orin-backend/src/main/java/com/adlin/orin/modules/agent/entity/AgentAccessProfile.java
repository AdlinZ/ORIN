package com.adlin.orin.modules.agent.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 智能体接入凭证
 * 存储连接到 Dify 所需的敏感信息
 */
@Entity
@Table(name = "agent_access_profiles")
public class AgentAccessProfile {

    /**
     * 来自 Dify 的 App ID (UUID)
     */
    @Id
    private String agentId;

    /**
     * Dify API 基础地址 (例如 http://localhost/v1)
     */
    private String endpointUrl;

    /**
     * Dify App Secret Key (Bearer Token)
     */
    private String apiKey;

    /**
     * Dify Dataset Secret Key (Optional)
     */
    private String datasetApiKey;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 接入状态 (VALID, INVALID)
     */
    private String connectionStatus;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    public AgentAccessProfile() {
    }

    public AgentAccessProfile(String agentId, String endpointUrl, String apiKey, String datasetApiKey,
            LocalDateTime createdAt, String connectionStatus) {
        this.agentId = agentId;
        this.endpointUrl = endpointUrl;
        this.apiKey = apiKey;
        this.datasetApiKey = datasetApiKey;
        this.createdAt = createdAt;
        this.connectionStatus = connectionStatus;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getDatasetApiKey() {
        return datasetApiKey;
    }

    public void setDatasetApiKey(String datasetApiKey) {
        this.datasetApiKey = datasetApiKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static AgentAccessProfileBuilder builder() {
        return new AgentAccessProfileBuilder();
    }

    public static class AgentAccessProfileBuilder {
        private String agentId;
        private String endpointUrl;
        private String apiKey;
        private String datasetApiKey;
        private LocalDateTime createdAt;
        private String connectionStatus;

        public AgentAccessProfileBuilder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public AgentAccessProfileBuilder endpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
            return this;
        }

        public AgentAccessProfileBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public AgentAccessProfileBuilder datasetApiKey(String datasetApiKey) {
            this.datasetApiKey = datasetApiKey;
            return this;
        }

        public AgentAccessProfileBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public AgentAccessProfileBuilder connectionStatus(String connectionStatus) {
            this.connectionStatus = connectionStatus;
            return this;
        }

        public AgentAccessProfile build() {
            return new AgentAccessProfile(agentId, endpointUrl, apiKey, datasetApiKey, createdAt, connectionStatus);
        }
    }
}
