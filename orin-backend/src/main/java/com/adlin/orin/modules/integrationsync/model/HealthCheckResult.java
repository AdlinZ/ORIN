package com.adlin.orin.modules.integrationsync.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class HealthCheckResult {
    private boolean healthy;
    private String message;
    private Map<String, Object> details;
}
