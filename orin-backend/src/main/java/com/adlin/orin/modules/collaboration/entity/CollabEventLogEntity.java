package com.adlin.orin.modules.collaboration.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 协作事件日志实体
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "collab_event_log")
public class CollabEventLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "package_id", nullable = false, length = 64)
    private String packageId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "sub_task_id", length = 64)
    private String subTaskId;

    @Column(name = "agent_id")
    private String agentId;

    @Column(name = "event_data", columnDefinition = "JSON")
    private String eventData;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}