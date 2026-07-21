package com.adlin.orin.modules.agent.freeze.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * F02 Agent 草稿 upsert 请求。
 * <p>
 * 把 {@code AgentMetadata} 中可编辑字段集中映射；secretRefs 不在草稿 DTO 中传递，
 * 必须通过 {@code POST /api/v1/agents/{id}/versions}（freeze 路径）写入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentDraftUpsertRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 1000)
    private String description;

    @Size(max = 255)
    private String icon;

    /** run mode: agent / chat / completion / workflow 等；freeze 仍接受，仅作为内联标量。 */
    @Size(max = 40)
    private String mode;

    /** 模型名（MVP 不实现 model_version 内联引用；冻结时进入 model.modelName）。 */
    @Size(max = 120)
    private String modelName;

    @Size(max = 80)
    private String providerType;

    @Size(max = 40)
    private String viewType;

    @Size(max = 50_000)
    private String systemPrompt;

    @Size(max = 8_000)
    private String parameters;

    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private Boolean toolCallingOverride;
    private Boolean mcpExposed;

    /**
     * 是否通过 AgentVersionService 上链；新前端不带这个字段。
     */
    @JsonProperty("skip_validation")
    private Boolean skipValidation;

    /**
     * 草稿上的 SecretReference（F02 R3：SecretReference 草稿可保存与恢复）。
     * MVP 仅接受 CONTROL_PLANE source；RUNNER_LOCAL 在 freeze 阶段抛 REJECT。
     * DTO 直接以 array-of-object 形式表达（不二次映射），便于与前端 UI 表格 schema 对齐。
     */
    @Size(max = 50)
    private List<FreezeSecretRefItem> pendingSecretRefs;

    /** 用户填的变更说明；写入 audit_log `change_description` 字段。 */
    @Size(max = 500)
    private String changeDescription;
}
