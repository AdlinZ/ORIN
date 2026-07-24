package com.adlin.orin.modules.endpoint.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * F05 发布端点请求。
 */
@Data
public class PublishEndpointRequest {

    @NotBlank
    private String agentId;

    @NotBlank
    private String agentVersionId;

    @NotBlank
    private String name;

    /** REST_API / MCP_SERVER，默认 REST_API。 */
    private String endpointType;

    private String description;
}
