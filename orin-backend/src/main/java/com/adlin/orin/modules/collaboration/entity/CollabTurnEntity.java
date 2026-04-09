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
@Table(name = "collab_turn")
public class CollabTurnEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turn_id", nullable = false, unique = true, length = 64)
    private String turnId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "package_id", length = 64)
    private String packageId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "user_message", columnDefinition = "TEXT")
    private String userMessage;

    @Column(name = "status", length = 30)
    @Builder.Default
    private String status = "RUNNING";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "selection_meta", columnDefinition = "JSON")
    private String selectionMeta;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    public void prePersist() {
        this.startedAt = LocalDateTime.now();
    }
}
