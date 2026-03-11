package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库同步记录
 */
@Entity
@Table(name = "knowledge_sync_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", length = 64)
    private String agentId;

    @Column(name = "sync_type", length = 20)
    private String syncType; // FULL, INCREMENTAL

    @Column(name = "status", length = 20)
    private String status; // RUNNING, COMPLETED, FAILED

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "added_count")
    private Integer addedCount = 0;

    @Column(name = "updated_count")
    private Integer updatedCount = 0;

    @Column(name = "deleted_count")
    private Integer deletedCount = 0;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details; // JSON 格式的详细信息
}
