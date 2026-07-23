package com.adlin.orin.modules.agent.freeze.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AgentVersion 详情视图里的 SecretReference 行。
 *
 * <p><b>不</b>暴露明文；只输出引用句柄与摘要列；last4 由 service 在拿到 gateway_secret 后回填（可选）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentVersionSecretRefView {

    private String alias;
    private String source;

    @JsonProperty("secret_id")
    private String secretId;

    @JsonProperty("local_key")
    private String localKey;

    private boolean required;

    @JsonProperty("inject_as")
    private String injectAs;

    /** 仅展示摘要（从 GatewaySecret.key_prefix / last4 派生）；不返回明文。 */
    @JsonProperty("key_prefix")
    private String keyPrefix;

    @JsonProperty("last4")
    private String last4;
}
