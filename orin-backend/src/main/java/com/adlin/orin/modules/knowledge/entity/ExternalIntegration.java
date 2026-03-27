package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一外部集成配置实体
 * 定义所有外部知识源（Notion、RAGFlow、Dify、Web、Database 等）的统一抽象
 */
@Entity
@Table(name = "knowledge_external_integration")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 集成名称
     */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /**
     * 集成类型
     * NOTION / RAGFLOW / DIFY / WEB / DATABASE / SHAREPOINT / CONFLOUENCE / CUSTOM
     */
    @Column(name = "integration_type", length = 20, nullable = false)
    private String integrationType;

    /**
     * 关联的知识库 ID
     */
    @Column(name = "knowledge_base_id", length = 64)
    private String knowledgeBaseId;

    /**
     * 认证类型
     * API_KEY / OAUTH2 / BASIC / BEARER_TOKEN / CUSTOM
     */
    @Column(name = "auth_type", length = 20)
    private String authType;

    /**
     * 认证信息（加密存储）
     * 包含 API Key、Token 等敏感信息
     */
    @Column(name = "auth_config", columnDefinition = "TEXT")
    private String authConfig;

    /**
     * 基础 URL / Endpoint
     */
    @Column(name = "base_url", length = 500)
    private String baseUrl;

    /**
     * 同步方向
     * PULL / PUSH / BIDIRECTIONAL
     */
    @Column(name = "sync_direction", length = 20)
    private String syncDirection;

    /**
     * 同步状态
     * DISABLED / ENABLED / ERROR
     */
    @Column(name = "status", length = 20)
    private String status;

    /**
     * 健康状态
     * HEALTHY / UNHEALTHY / UNKNOWN
     */
    @Column(name = "health_status", length = 20)
    private String healthStatus;

    /**
     * 上次同步时间
     */
    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    /**
     * 上次健康检查时间
     */
    @Column(name = "last_health_check")
    private LocalDateTime lastHealthCheck;

    /**
     * 连续失败次数
     */
    @Column(name = "consecutive_failures")
    @Builder.Default
    private Integer consecutiveFailures = 0;

    /**
     * 能力类型（JSON 数组）
     * ["RETRIEVAL", "SYNC", "UPLOAD", "DELETE"]
     */
    @Column(name = "capabilities", columnDefinition = "TEXT")
    private String capabilities;

    /**
     * 扩展配置（JSON）
     * 特定集成类型的额外配置
     */
    @Column(name = "extra_config", columnDefinition = "TEXT")
    private String extraConfig;

    /**
     * 错误状态信息
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
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

    /**
     * 能力类型枚举
     */
    public enum Capability {
        RETRIEVAL,    // 向量检索
        SYNC,         // 双向同步
        UPLOAD,       // 上传文档
        DELETE        // 删除同步
    }

    /**
     * 集成类型枚举
     */
    public enum IntegrationType {
        NOTION,
        RAGFLOW,
        DIFY,
        WEB,
        DATABASE,
        SHAREPOINT,
        CONFLUENCE,
        CUSTOM
    }

    /**
     * 认证类型枚举
     */
    public enum AuthType {
        API_KEY,
        OAUTH2,
        BASIC,
        BEARER_TOKEN,
        CUSTOM
    }

    /**
     * 同步方向枚举
     */
    public enum SyncDirection {
        PULL,          // 从外部拉取
        PUSH,          // 推送到外部
        BIDIRECTIONAL  // 双向同步
    }

    /**
     * 状态枚举
     */
    public enum Status {
        DISABLED,
        ENABLED,
        ERROR
    }

    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        HEALTHY,
        UNHEALTHY,
        UNKNOWN
    }
}