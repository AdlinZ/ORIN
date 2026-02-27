package com.adlin.orin.modules.zeroclaw.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * ZeroClaw 配置实体
 * 存储与轻量化 Agent 的连接配置
 */
@Entity
@Table(name = "zeroclaw_configs")
public class ZeroClawConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 配置名称
     */
    @Column(nullable = false)
    private String configName;

    /**
     * ZeroClaw 服务地址
     */
    @Column(nullable = false)
    private String endpointUrl;

    /**
     * 访问令牌
     */
    private String accessToken;

    /**
     * 是否启用
     */
    @Column(nullable = false)
    private Boolean enabled = true;

    /**
     * 是否启用智能分析
     */
    private Boolean enableAnalysis = true;

    /**
     * 是否启用主动维护
     */
    private Boolean enableSelfHealing = true;

    /**
     * 心跳间隔 (秒)
     */
    private Integer heartbeatInterval = 60;

    /**
     * 创建时间
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Boolean getEnableAnalysis() { return enableAnalysis; }
    public void setEnableAnalysis(Boolean enableAnalysis) { this.enableAnalysis = enableAnalysis; }

    public Boolean getEnableSelfHealing() { return enableSelfHealing; }
    public void setEnableSelfHealing(Boolean enableSelfHealing) { this.enableSelfHealing = enableSelfHealing; }

    public Integer getHeartbeatInterval() { return heartbeatInterval; }
    public void setHeartbeatInterval(Integer heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static ZeroClawConfigBuilder builder() {
        return new ZeroClawConfigBuilder();
    }

    public static class ZeroClawConfigBuilder {
        private String configName;
        private String endpointUrl;
        private String accessToken;
        private Boolean enabled = true;
        private Boolean enableAnalysis = true;
        private Boolean enableSelfHealing = true;
        private Integer heartbeatInterval = 60;

        public ZeroClawConfigBuilder configName(String configName) {
            this.configName = configName;
            return this;
        }

        public ZeroClawConfigBuilder endpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
            return this;
        }

        public ZeroClawConfigBuilder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public ZeroClawConfigBuilder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ZeroClawConfigBuilder enableAnalysis(Boolean enableAnalysis) {
            this.enableAnalysis = enableAnalysis;
            return this;
        }

        public ZeroClawConfigBuilder enableSelfHealing(Boolean enableSelfHealing) {
            this.enableSelfHealing = enableSelfHealing;
            return this;
        }

        public ZeroClawConfigBuilder heartbeatInterval(Integer heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
            return this;
        }

        public ZeroClawConfig build() {
            ZeroClawConfig config = new ZeroClawConfig();
            config.setConfigName(configName);
            config.setEndpointUrl(endpointUrl);
            config.setAccessToken(accessToken);
            config.setEnabled(enabled);
            config.setEnableAnalysis(enableAnalysis);
            config.setEnableSelfHealing(enableSelfHealing);
            config.setHeartbeatInterval(heartbeatInterval);
            return config;
        }
    }
}
