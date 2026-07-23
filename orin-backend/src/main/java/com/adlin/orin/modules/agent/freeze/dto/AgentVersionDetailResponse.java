package com.adlin.orin.modules.agent.freeze.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AgentVersion 详情（GET /api/v1/agents/{agentId}/versions/{versionId}）。
 *
 * <p>FROZEN 状态下完全只读；DEPRECATED 状态额外展示 deprecation 元数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentVersionDetailResponse {

    @JsonProperty("agent_version_id")
    private String agentVersionId;

    @JsonProperty("agent_id")
    private String agentId;

    @JsonProperty("version_number")
    private Integer versionNumber;

    @JsonProperty("version_tag")
    private String versionTag;

    private String status;

    @JsonProperty("content_digest")
    private String contentDigest;

    @JsonProperty("snapshot_schema_version")
    private Short snapshotSchemaVersion;

    @JsonProperty("change_description")
    private String changeDescription;

    @JsonProperty("frozen_at")
    private LocalDateTime frozenAt;

    @JsonProperty("frozen_by")
    private String frozenBy;

    @JsonProperty("created_by")
    private String createdBy;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    /** 是否当前 active_version_id 指针指向此版本。 */
    @JsonProperty("is_active")
    private boolean isActive;

    /** FROZEN 之后 secret refs 完全只读；本字段展示引用形态。 */
    @JsonProperty("secret_refs")
    private List<AgentVersionSecretRefView> secretRefs;

    @JsonProperty("deprecation_reason")
    private String deprecationReason;

    @JsonProperty("deprecated_at")
    private LocalDateTime deprecatedAt;

    @JsonProperty("deprecated_by")
    private String deprecatedBy;
}
