package com.adlin.orin.modules.agent.freeze.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 草稿查询响应（GET /api/v1/agents/{id}）。
 * <p>
 * 含 active_version_id 指针与最近一次冻结元数据；前端在草稿页直接判断"已冻结 / 可编辑"。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentDraftResponse {

    private String agentId;

    @JsonProperty("owner_user_id")
    private Long ownerUserId;

    private String name;
    private String description;
    private String icon;
    private String mode;
    private String modelName;
    private String providerType;
    private String viewType;

    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    @JsonProperty("tool_calling_override")
    private Boolean toolCallingOverride;
    @JsonProperty("mcp_exposed")
    private Boolean mcpExposed;

    @JsonProperty("system_prompt")
    private String systemPrompt;
    private String parameters;

    /** null 表示尚未冻结过任何版本。 */
    @JsonProperty("active_version_id")
    private String activeVersionId;

    @JsonProperty("active_version_number")
    private Integer activeVersionNumber;

    @JsonProperty("active_version_digest")
    private String activeVersionDigest;

    @JsonProperty("active_version_status")
    private String activeVersionStatus;

    /**
     * F02 R3：草稿上的 SecretReference（持久化在 metadata.pending_secret_refs 列）。
     * JSON 字符串；前端反序列化为表格行。
     */
    @JsonProperty("pending_secret_refs")
    private String pendingSecretRefs;

    @JsonProperty("sync_time")
    private LocalDateTime syncTime;
}
