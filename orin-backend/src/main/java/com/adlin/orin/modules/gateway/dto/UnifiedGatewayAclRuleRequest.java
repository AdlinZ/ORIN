package com.adlin.orin.modules.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedGatewayAclRuleRequest {

    private Long id;

    private String name;

    private String type;

    private String ipPattern;

    private String pathPattern;

    private Boolean apiKeyRequired;

    private String description;

    private Integer priority;

    private Boolean enabled;
}
