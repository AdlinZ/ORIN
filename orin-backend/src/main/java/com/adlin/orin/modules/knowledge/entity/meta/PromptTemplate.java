package com.adlin.orin.modules.knowledge.entity.meta;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "prompt_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplate {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "agent_id")
    private String agentId;

    @Column(name = "name")
    private String name;

    @Column(name = "type") // ROLE, INSTRUCTION, FEW_SHOT
    private String type;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
