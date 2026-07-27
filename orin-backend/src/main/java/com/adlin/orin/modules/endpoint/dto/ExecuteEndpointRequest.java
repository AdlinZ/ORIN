package com.adlin.orin.modules.endpoint.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * F05 外部调用 Endpoint 请求（REST / MCP 共用）。
 *
 * <p>通过 API Key 鉴权，走 {@code /v1/endpoints/{endpointId}/run}。
 */
@Data
public class ExecuteEndpointRequest {

    @NotBlank
    private String input;

    /** 是否 SSE 流式返回（MVP 默认 false）。 */
    private Boolean stream;

    /** 同步等待超时毫秒（默认 60000，0 = 异步立即返回 202）。 */
    private Long timeoutMs;
}
