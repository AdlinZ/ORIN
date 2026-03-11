package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 结构化数据表元数据
 */
@Entity
@Table(name = "structured_data_table")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuredDataTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "columns", columnDefinition = "TEXT")
    private String columns; // 逗号分隔的列名

    @Column(name = "column_count")
    private Integer columnCount;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "updated_at")
    private Date updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
        updatedAt = new Date();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }
}
