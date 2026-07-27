package com.adlin.orin.modules.endpoint.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * F05 外部调用 Endpoint 响应（REST / MCP 共用）。
 */
@Data
@Builder
public class ExecuteEndpointResponse {

    private String runId;
    private String traceId;
    private String status;
    private String output;
    private String statusUrl;

    /** 中间态事件列表（同步返回时附带）。 */
    private List<Map<String, Object>> events;
}
