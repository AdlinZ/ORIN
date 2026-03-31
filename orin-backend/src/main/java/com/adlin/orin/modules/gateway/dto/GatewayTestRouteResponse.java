package com.adlin.orin.modules.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayTestRouteResponse {

    private Boolean success;
    private Integer statusCode;
    private Long latencyMs;
    private String message;
    private String matchedRoute;
    private String targetUrl;
}
