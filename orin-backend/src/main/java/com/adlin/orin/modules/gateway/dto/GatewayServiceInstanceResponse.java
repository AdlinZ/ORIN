package com.adlin.orin.modules.gateway.dto;

import com.adlin.orin.modules.gateway.entity.GatewayServiceInstance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayServiceInstanceResponse {

    private Long id;
    private Long serviceId;
    private String host;
    private Integer port;
    private Integer weight;
    private String healthCheckPath;
    private String status;
    private LocalDateTime lastHeartbeat;
    private Integer consecutiveFailures;
    private Boolean enabled;
    private Long latencyMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GatewayServiceInstanceResponse fromEntity(GatewayServiceInstance entity) {
        return GatewayServiceInstanceResponse.builder()
                .id(entity.getId())
                .serviceId(entity.getServiceId())
                .host(entity.getHost())
                .port(entity.getPort())
                .weight(entity.getWeight())
                .healthCheckPath(entity.getHealthCheckPath())
                .status(entity.getStatus())
                .lastHeartbeat(entity.getLastHeartbeat())
                .consecutiveFailures(entity.getConsecutiveFailures())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
