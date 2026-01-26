package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "kb_skills")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "trigger_name", nullable = false)
    private String triggerName;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "definition", columnDefinition = "TEXT")
    private String definition; // DSL JSON

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
        if (updatedAt == null)
            updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
