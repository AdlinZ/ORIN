package com.adlin.orin.modules.conversation.dto.tooling;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCatalogItemDto {
    private String toolId;
    private String displayName;
    private String category;
    private Map<String, Object> schema;
    private Boolean enabled;
    private String runtimeMode;
    private String healthStatus;
    private String version;
    private String source;
}
