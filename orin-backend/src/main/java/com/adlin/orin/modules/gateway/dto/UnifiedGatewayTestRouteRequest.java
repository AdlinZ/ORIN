package com.adlin.orin.modules.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedGatewayTestRouteRequest {

    private String path;

    private String method;

    private String targetUrl;

    private Integer timeoutMs;
}
