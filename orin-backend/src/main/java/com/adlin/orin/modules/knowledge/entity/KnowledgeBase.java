package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_bases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBase {

    @Id
    @Column(name = "id")
    private String id; // Dify Dataset ID

    @Column(name = "name")
    private String name;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "type")
    @Builder.Default
    private KnowledgeType type = KnowledgeType.UNSTRUCTURED;

    @Column(name = "description")
    private String description;

    @Column(name = "doc_count")
    private Integer docCount;

    @Column(name = "total_size_mb")
    private Double totalSizeMb;

    @Column(name = "status")
    private String status; // ENABLED, DISABLED

    @Column(name = "source_agent_id")
    private String sourceAgentId; // ID of the agent this KB was synced from (optional binding)

    @Column(name = "sync_time")
    private LocalDateTime syncTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Store type-specific configuration (JSON).
     * e.g., SQL Connection String, Graph DB Endpoint, or custom Tool definitions.
     */
    @Column(name = "configuration", columnDefinition = "TEXT")
    private String configuration;
}
