package com.adlin.orin.modules.run.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * F03 创建 Run 请求。
 */
@Data
public class CreateRunRequest {

    @NotBlank
    private String agentId;

    @NotBlank
    private String agentVersionId;

    /** 可选：指定 Runner；为空则自动 lease。 */
    private String runnerId;

    /** 用户输入 / prompt。 */
    private String input;

    /** 幂等键（可选）。 */
    private String idempotencyKey;
}
