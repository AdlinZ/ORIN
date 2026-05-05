package com.adlin.orin.modules.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedGatewayServiceRequest {

    private Long id;

    private String serviceKey;

    private String serviceName;

    private String protocol;

    private String basePath;

    private String description;

    private Boolean enabled;
}
