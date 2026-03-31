package com.adlin.orin.modules.gateway.dto;

import com.adlin.orin.modules.gateway.entity.GatewayService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayServiceResponse {

    private Long id;
    private String serviceKey;
    private String serviceName;
    private String protocol;
    private String basePath;
    private String description;
    private Boolean enabled;
    private Integer instanceCount;
    private Integer healthyInstanceCount;
    private String status;
    private List<GatewayServiceInstanceResponse> instances;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GatewayServiceResponse fromEntity(GatewayService entity) {
        return GatewayServiceResponse.builder()
                .id(entity.getId())
                .serviceKey(entity.getServiceKey())
                .serviceName(entity.getServiceName())
                .protocol(entity.getProtocol())
                .basePath(entity.getBasePath())
                .description(entity.getDescription())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
