package com.adlin.orin.modules.collaboration.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "collab_session")
public class CollabSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(name = "title")
    private String title;

    @Column(name = "status", length = 30)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "main_agent_policy", length = 30)
    @Builder.Default
    private String mainAgentPolicy = "STATIC_THEN_BID";

    @Column(name = "quality_threshold")
    @Builder.Default
    private Double qualityThreshold = 0.82;

    @Column(name = "max_critique_rounds")
    @Builder.Default
    private Integer maxCritiqueRounds = 3;

    @Column(name = "draft_parallelism")
    @Builder.Default
    private Integer draftParallelism = 4;

    @Column(name = "main_agent_static_default")
    private String mainAgentStaticDefault;

    @Column(name = "bid_whitelist", columnDefinition = "JSON")
    private String bidWhitelist;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
