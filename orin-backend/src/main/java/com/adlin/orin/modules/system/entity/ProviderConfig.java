package com.adlin.orin.modules.system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 系统供应商配置实体
 * 统一管理API供应商的显示顺序和配置
 */
@Entity
@Table(name = "sys_provider_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderConfig {

    @Id
    @Column(name = "provider_key", length = 50)
    private String providerKey;

    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "icon", length = 100)
    private String icon;

    @Column(name = "created_at")
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
}
