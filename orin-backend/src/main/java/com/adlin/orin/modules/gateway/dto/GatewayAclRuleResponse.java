package com.adlin.orin.modules.gateway.dto;

import com.adlin.orin.modules.gateway.entity.GatewayAclRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayAclRuleResponse {

    private Long id;
    private String name;
    private String type;
    private String ipPattern;
    private String pathPattern;
    private Boolean apiKeyRequired;
    private String description;
    private Integer priority;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GatewayAclRuleResponse fromEntity(GatewayAclRule entity) {
        return GatewayAclRuleResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .ipPattern(entity.getIpPattern())
                .pathPattern(entity.getPathPattern())
                .apiKeyRequired(entity.getApiKeyRequired())
                .description(entity.getDescription())
                .priority(entity.getPriority())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
