package com.adlin.orin.modules.system.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统基础配置实体
 */
@Data
@Entity
@Table(name = "sys_system_config")
public class SystemConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 配置键
     */
    @Column(name = "config_key", unique = true, nullable = false)
    private String configKey;

    /**
     * 配置值
     */
    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    /**
     * 配置描述
     */
    @Column(name = "description")
    private String description;

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
}