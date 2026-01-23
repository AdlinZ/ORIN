package com.adlin.orin.modules.agent.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 智能体元数据档案
 * 存储智能体的静态描述信息 (名称、描述、模式等)
 */
@Entity
@Table(name = "agent_metadata")
public class AgentMetadata {

    @Id
    private String agentId;

    private String name;

    private String description;

    /**
     * 图标 URL 或 Emoji 字符
     */
    private String icon;

    /**
     * 运行模式 (agent, chat, completion, workflow)
     */
    private String mode;

    /**
     * 关联的模型配置 (如 gpt-4, calm2-7b)
     */
    private String modelName;

    /**
     * Provider 类型 (Dify, SiliconFlow, Local, etc.)
     */
    private String providerType;

    private Double temperature;
    private Double topP;
    private Integer maxTokens;

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String systemPrompt;

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    private LocalDateTime syncTime;

    public AgentMetadata() {
    }

    public AgentMetadata(String agentId, String name, String description, String icon, String mode, String modelName,
            String providerType, LocalDateTime syncTime) {
        this.agentId = agentId;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.mode = mode;
        this.modelName = modelName;
        this.providerType = providerType;
        this.syncTime = syncTime;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
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

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public LocalDateTime getSyncTime() {
        return syncTime;
    }

    public void setSyncTime(LocalDateTime syncTime) {
        this.syncTime = syncTime;
    }

    public static AgentMetadataBuilder builder() {
        return new AgentMetadataBuilder();
    }

    public static class AgentMetadataBuilder {
        private String agentId;
        private String name;
        private String description;
        private String icon;
        private String mode;
        private String modelName;
        private String providerType;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private String systemPrompt;
        private LocalDateTime syncTime;

        public AgentMetadataBuilder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public AgentMetadataBuilder name(String name) {
            this.name = name;
            return this;
        }

        public AgentMetadataBuilder description(String description) {
            this.description = description;
            return this;
        }

        public AgentMetadataBuilder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public AgentMetadataBuilder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public AgentMetadataBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public AgentMetadataBuilder providerType(String providerType) {
            this.providerType = providerType;
            return this;
        }

        public AgentMetadataBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public AgentMetadataBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public AgentMetadataBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public AgentMetadataBuilder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public AgentMetadataBuilder syncTime(LocalDateTime syncTime) {
            this.syncTime = syncTime;
            return this;
        }

        public AgentMetadata build() {
            AgentMetadata metadata = new AgentMetadata(agentId, name, description, icon, mode, modelName, providerType,
                    syncTime);
            metadata.setTemperature(temperature);
            metadata.setTopP(topP);
            metadata.setMaxTokens(maxTokens);
            metadata.setSystemPrompt(systemPrompt);
            return metadata;
        }
    }
}
