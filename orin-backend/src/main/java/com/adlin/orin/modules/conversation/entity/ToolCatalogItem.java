package com.adlin.orin.modules.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "tool_catalog")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCatalogItem {

    @Id
    @Column(name = "tool_id", nullable = false, length = 128)
    private String toolId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_json", columnDefinition = "JSON")
    private Map<String, Object> schema;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "runtime_mode", nullable = false, length = 32)
    @Builder.Default
    private String runtimeMode = "context_only";

    @Column(name = "health_status", length = 32)
    private String healthStatus;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "source", nullable = false, length = 32)
    @Builder.Default
    private String source = "SYSTEM";

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
}
