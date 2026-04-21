package com.adlin.orin.modules.conversation.dto.tooling;

import lombok.Data;

import java.util.Map;

@Data
public class ToolCatalogUpdateRequest {
    private String displayName;
    private String category;
    private Map<String, Object> schema;
    private Boolean enabled;
    private String runtimeMode;
    private String healthStatus;
    private String version;
    private String source;
}
