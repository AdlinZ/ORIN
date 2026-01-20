package com.adlin.orin.modules.knowledge.entity.meta;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_memories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMemory {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "agent_id")
    private String agentId;

    @Column(name = "memory_key")
    private String key;

    @Column(name = "memory_value", columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
