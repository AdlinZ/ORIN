package com.adlin.orin.modules.endpoint.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * AgentEndpoint（F05）— 将已冻结 AgentVersion 发布为 REST API 或 MCP Server。
 */
@Entity
@Table(name = "agent_endpoints", indexes = {
        @Index(name = "idx_agent_endpoints_agent", columnList = "agent_id"),
        @Index(name = "idx_agent_endpoints_type_status", columnList = "endpoint_type, status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEndpoint {

    @Id
    @Column(length = 40, nullable = false)
    private String id;

    @Column(name = "agent_id", nullable = false, length = 50)
    private String agentId;

    @Column(name = "agent_version_id", nullable = false, length = 40)
    private String agentVersionId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "endpoint_type", nullable = false, length = 20)
    @Builder.Default
    private EndpointType endpointType = EndpointType.REST_API;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EndpointStatus status = EndpointStatus.ACTIVE;

    @Column(name = "endpoint_path", nullable = false, length = 255)
    private String endpointPath;

    @Column(columnDefinition = "JSON")
    private String config;

    @Column(length = 500)
    private String description;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

    @PrePersist
    protected void onCreate() {
        long now = Instant.now().toEpochMilli();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (endpointType == null) endpointType = EndpointType.REST_API;
        if (status == null) status = EndpointStatus.ACTIVE;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now().toEpochMilli();
    }
}
