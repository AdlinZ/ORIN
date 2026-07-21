package com.adlin.orin.modules.agent.freeze.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Freeze 请求体（POST /api/v1/agents/{agentId}/versions）。
 *
 * <p>草稿主体（name/description/model/systemPrompt…）已在 {@code AgentMetadata} 中，
 * <b>不</b>在 freeze 请求里重复传，避免与草稿失同步。freeze 仅控制：
 * <ul>
 *   <li>{@code secretRefs[]}：必须非空（MVP 要求至少一条 CONTROL_PLANE ref）；</li>
 *   <li>{@code changeDescription}：可选，对应 version 的 release note；</li>
 *   <li>{@code versionTag}：可选，如 "v1.0-stable"。</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FreezeAgentRequest {

    @Valid
    @NotEmpty(message = "MVP 要求至少一个 SecretReference")
    private List<FreezeSecretRefItem> secretRefs;

    @Size(max = 500)
    private String changeDescription;

    @Size(max = 50)
    private String versionTag;
}
