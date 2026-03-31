package com.adlin.orin.modules.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayServiceInstanceRequest {

    private Long id;

    private Long serviceId;

    private String host;

    private Integer port;

    private Integer weight;

    private String healthCheckPath;

    private String status;

    private Boolean enabled;
}
