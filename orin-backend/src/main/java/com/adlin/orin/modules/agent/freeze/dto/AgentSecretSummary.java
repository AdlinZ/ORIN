package com.adlin.orin.modules.agent.freeze.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 给前端"添加 SecretReference"下拉框用的 GatewaySecret 摘要。
 * 来源：apikey 模块 gateway_secrets 表（status=ACTIVE）。
 *
 * <p>仅暴露引用形态（secret_id、secret_type、provider、key_prefix、last4），
 * 不暴露 encrypted_secret / key_hash / user_id。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSecretSummary {

    @JsonProperty("secret_id")
    private String secretId;

    @JsonProperty("secret_type")
    private String secretType;

    private String provider;

    @JsonProperty("key_prefix")
    private String keyPrefix;

    @JsonProperty("last4")
    private String last4;

    @JsonProperty("base_url")
    private String baseUrl;
}
