package com.adlin.orin.modules.skill.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MCP 服务实体类
 */
@Entity
@Table(name = "mcp_services")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "tool_key", length = 100)
    private String toolKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    @Builder.Default
    private McpType type = McpType.STDIO;

    @Column(name = "command", columnDefinition = "TEXT")
    private String command;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "env_vars", columnDefinition = "TEXT")
    private String envVars;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private McpStatus status = McpStatus.DISCONNECTED;

    @Column(name = "last_connected")
    private LocalDateTime lastConnected;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "health_score")
    @Builder.Default
    private Integer healthScore = 100;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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
     * MCP 服务类型
     */
    public enum McpType {
        STDIO,  // 标准输入输出类型
        SSE     // Server-Sent Events 类型
    }

    /**
     * MCP 服务状态
     */
    public enum McpStatus {
        CONNECTED,     // 已连接
        DISCONNECTED,  // 未连接
        ERROR,         // 错误
        TESTING        // 测试中
    }
}
