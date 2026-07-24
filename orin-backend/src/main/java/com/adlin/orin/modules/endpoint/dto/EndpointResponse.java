package com.adlin.orin.modules.endpoint.dto;

import com.adlin.orin.modules.endpoint.entity.AgentEndpoint;
import lombok.Builder;
import lombok.Data;

/**
 * F05 端点响应。
 */
@Data
@Builder
public class EndpointResponse {

    private String id;
    private String agentId;
    private String agentVersionId;
    private String name;
    private String endpointType;
    private String status;
    private String endpointPath;
    private String description;
    private String createdBy;
    private Long createdAt;

    public static EndpointResponse from(AgentEndpoint ep) {
        return EndpointResponse.builder()
                .id(ep.getId())
                .agentId(ep.getAgentId())
                .agentVersionId(ep.getAgentVersionId())
                .name(ep.getName())
                .endpointType(ep.getEndpointType().name())
                .status(ep.getStatus().name())
                .endpointPath(ep.getEndpointPath())
                .description(ep.getDescription())
                .createdBy(ep.getCreatedBy())
                .createdAt(ep.getCreatedAt())
                .build();
    }
}
