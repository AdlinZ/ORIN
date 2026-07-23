package com.adlin.orin.modules.agent.freeze.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 版本列表项（GET /api/v1/agents/{agentId}/versions）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentVersionListItem {

    @JsonProperty("agent_version_id")
    private String agentVersionId;

    @JsonProperty("version_number")
    private Integer versionNumber;

    @JsonProperty("version_tag")
    private String versionTag;

    private String status;

    @JsonProperty("content_digest")
    private String contentDigest;

    @JsonProperty("snapshot_schema_version")
    private Short snapshotSchemaVersion;

    @JsonProperty("frozen_at")
    private LocalDateTime frozenAt;

    @JsonProperty("created_by")
    private String createdBy;

    @JsonProperty("is_active")
    private boolean isActive;
}
