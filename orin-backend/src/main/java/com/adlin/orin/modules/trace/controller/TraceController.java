package com.adlin.orin.modules.trace.controller;

import com.adlin.orin.modules.observability.service.LangfuseObservabilityService;
import com.adlin.orin.modules.trace.entity.WorkflowTraceEntity;
import com.adlin.orin.modules.trace.service.TraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 追踪管理控制器
 */
@Slf4j
@RestController
@RequestMapping({"/api/traces", "/api/v1/traces"})
@RequiredArgsConstructor
@Tag(name = "Trace Management", description = "追踪管理 API")
public class TraceController {

    private final TraceService traceService;
    private final LangfuseObservabilityService langfuseService;

    @GetMapping("/recent")
    @Operation(summary = "获取最近调用链路摘要")
    public ResponseEntity<List<Map<String, Object>>> getRecentTraces(
            @RequestParam(required = false, defaultValue = "20") int size) {
        log.info("REST request to get recent traces: size={}", size);
        return ResponseEntity.ok(traceService.getRecentTraceSummaries(size));
    }

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

    @GetMapping("/search")
    @Operation(summary = "按 traceId 搜索追踪记录")
    public ResponseEntity<Map<String, Object>> searchByTraceId(
            @RequestParam String traceId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size) {
        log.info("Search traces by traceId: {}", traceId);

        List<WorkflowTraceEntity> traces = traceService.queryTracesByTraceId(traceId);

        Map<String, Object> result = new HashMap<>();
        result.put("traceId", traceId);
        result.put("found", !traces.isEmpty());
        result.put("count", traces.size());
        result.put("traces", traces);

        // 如果 Langfuse 可用，生成深链
        if (langfuseService.isEnabled()) {
            result.put("langfuseLink", langfuseService.getDashboardUrl() + "/project/traces?filter=trace_id:" + traceId);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{traceId}/link")
    @Operation(summary = "获取 Langfuse 深链跳转")
    public ResponseEntity<Map<String, Object>> getLangfuseLink(@PathVariable String traceId) {
        Map<String, Object> result = new HashMap<>();

        if (!langfuseService.isEnabled()) {
            result.put("available", false);
            result.put("message", "Langfuse 未启用");
            return ResponseEntity.ok(result);
        }

        result.put("available", true);
        result.put("traceId", traceId);
        result.put("link", langfuseService.getDashboardUrl() + "/project/traces?filter=trace_id:" + traceId);
        return ResponseEntity.ok(result);
    }
}
