package com.adlin.orin.modules.workflow.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkflowAccessResponse {
    private String webAppUrl;
    private String apiUrl;
    private String apiKey;
}
