package com.adlin.orin.modules.agent.freeze.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Freeze 请求中的 SecretReference 项（ADR-002 §D-2.6）。
 *
 * <p>MVP 仅接受 {@code source = CONTROL_PLANE} + {@code secretId}；
 * {@code RUNNER_LOCAL} 由 service 在 freeze 阶段抛 {@code RUNNER_LOCAL_SECRET_MISSING} 拒绝。
 * 名义命名约束：
 * <ul>
 *   <li>alias / injectAs：{@code [A-Za-z0-9_.-]} 字符集，长度 1..64 / 1..64；</li>
 *   <li>secretId 与 gateway_secrets.secret_id 长度约束保持一致（≤100）；</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FreezeSecretRefItem {

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "alias 仅允许字母数字 _ . -")
    private String alias;

    /** MVP 仅允许 CONTROL_PLANE；RUNNER_LOCAL 在 service 抛 reject。 */
    @NotBlank
    @Pattern(regexp = "^(CONTROL_PLANE|RUNNER_LOCAL)$",
            message = "source 仅允许 CONTROL_PLANE 或 RUNNER_LOCAL")
    private String source;

    @Size(max = 100)
    @JsonProperty("secret_id")
    private String secretId;

    @Size(max = 120)
    @JsonProperty("local_key")
    private String localKey;

    @Builder.Default
    private boolean required = true;

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "injectAs 仅允许大写字母数字下划线")
    @JsonProperty("inject_as")
    private String injectAs;
}
