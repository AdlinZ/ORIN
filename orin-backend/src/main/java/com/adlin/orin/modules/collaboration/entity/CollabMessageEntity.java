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
@Table(name = "collab_message")
public class CollabMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "turn_id", length = 64)
    private String turnId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "stage", length = 50)
    private String stage;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
