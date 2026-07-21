package com.adlin.orin.modules.agent.freeze.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Freeze 响应（POST /api/v1/agents/{agentId}/versions）。
 *
 * <p>幂等命中（同 key + 同 payload）时也返回本结构；payload 携带历史 version_id。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreezeAgentResponse {

    @JsonProperty("agent_version_id")
    private String agentVersionId;

    @JsonProperty("agent_id")
    private String agentId;

    @JsonProperty("version_number")
    private Integer versionNumber;

    @JsonProperty("status")
    private String status;

    @JsonProperty("content_digest")
    private String contentDigest;

    @JsonProperty("snapshot_schema_version")
    private Short snapshotSchemaVersion;

    @JsonProperty("frozen_at")
    private LocalDateTime frozenAt;

    @JsonProperty("frozen_by")
    private String frozenBy;

    /** true 表示幂等命中（未创建新版本），false 表示新建版本。 */
    @JsonProperty("idempotent_replay")
    private boolean idempotentReplay;
}
