package com.adlin.orin.modules.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agent_tool_binding")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolBinding {

    @Id
    @Column(name = "agent_id", nullable = false, length = 128)
    private String agentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_ids", columnDefinition = "JSON")
    @Builder.Default
    private List<String> toolIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kb_ids", columnDefinition = "JSON")
    @Builder.Default
    private List<String> kbIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_ids", columnDefinition = "JSON")
    @Builder.Default
    private List<Long> skillIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mcp_ids", columnDefinition = "JSON")
    @Builder.Default
    private List<Long> mcpIds = new ArrayList<>();

    @Column(name = "enable_suggestions", nullable = false)
    @Builder.Default
    private Boolean enableSuggestions = true;

    @Column(name = "show_retrieved_context", nullable = false)
    @Builder.Default
    private Boolean showRetrievedContext = true;

    @Column(name = "auto_rename_session", nullable = false)
    @Builder.Default
    private Boolean autoRenameSession = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
