package com.adlin.orin.modules.skill.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 技能实体类
 */
@Entity
@Table(name = "skills")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_type", nullable = false, length = 50)
    private SkillType skillType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // MCP 标准字段
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mcp_metadata", columnDefinition = "JSON")
    private Map<String, Object> mcpMetadata;

    @Column(name = "skill_md_content", columnDefinition = "TEXT")
    private String skillMdContent;

    // API 类型技能配置
    @Column(name = "api_endpoint", length = 500)
    private String apiEndpoint;

    @Column(name = "api_method", length = 10)
    private String apiMethod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "api_headers", columnDefinition = "JSON")
    private Map<String, String> apiHeaders;

    // 知识库类型技能配置
    @Column(name = "knowledge_config_id")
    private Long knowledgeConfigId;

    // 复合技能配置
    @Column(name = "workflow_id")
    private Long workflowId;

    // 外部平台引用
    @Column(name = "external_platform", length = 50)
    private String externalPlatform;

    @Column(name = "external_reference", length = 500)
    private String externalReference;

    // Shell 类型技能配置
    @Column(name = "shell_command", columnDefinition = "TEXT")
    private String shellCommand;

    // 输入输出定义
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_schema", columnDefinition = "JSON")
    private Map<String, Object> inputSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_schema", columnDefinition = "JSON")
    private Map<String, Object> outputSchema;

    // 状态和元信息
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private SkillStatus status = SkillStatus.ACTIVE;

    @Column(name = "version", length = 20)
    @Builder.Default
    private String version = "1.0.0";

    @Column(name = "created_by", length = 100)
    private String createdBy;

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
     * 技能类型枚举
     */
    public enum SkillType {
        API, // API 调用类型
        KNOWLEDGE, // 知识库检索类型
        COMPOSITE, // 复合类型 (引用其他工作流)
        SHELL // 系统命令类型
    }

    /**
     * 技能状态枚举
     */
    public enum SkillStatus {
        ACTIVE, // 活跃
        INACTIVE, // 未激活
        DEPRECATED // 已废弃
    }
}
