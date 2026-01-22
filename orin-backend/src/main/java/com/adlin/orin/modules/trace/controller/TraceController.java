package com.adlin.orin.modules.trace.controller;

import com.adlin.orin.modules.trace.entity.WorkflowTraceEntity;
import com.adlin.orin.modules.trace.service.TraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 追踪管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/traces")
@RequiredArgsConstructor
@Tag(name = "Trace Management", description = "追踪管理 API")
public class TraceController {

    private final TraceService traceService;

    @GetMapping("/{traceId}")
    @Operation(summary = "获取完整调用链路")
    public ResponseEntity<List<WorkflowTraceEntity>> getTraceChain(@PathVariable String traceId) {
        log.info("REST request to get trace chain: {}", traceId);
        List<WorkflowTraceEntity> traces = traceService.queryTracesByTraceId(traceId);
        return ResponseEntity.ok(traces);
    }

    @GetMapping("/instance/{instanceId}")
    @Operation(summary = "获取实例的所有追踪")
    public ResponseEntity<List<WorkflowTraceEntity>> getInstanceTraces(@PathVariable Long instanceId) {
        log.info("REST request to get instance traces: {}", instanceId);
        List<WorkflowTraceEntity> traces = traceService.queryTracesByInstanceId(instanceId);
        return ResponseEntity.ok(traces);
    }

    @GetMapping("/{traceId}/stats")
    @Operation(summary = "获取追踪统计信息")
    public ResponseEntity<Map<String, Object>> getTraceStats(@PathVariable String traceId) {
        log.info("REST request to get trace stats: {}", traceId);
        Map<String, Object> stats = traceService.getTraceStats(traceId);
        return ResponseEntity.ok(stats);
    }
}
