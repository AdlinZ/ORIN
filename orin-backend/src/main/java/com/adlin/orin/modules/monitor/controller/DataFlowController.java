package com.adlin.orin.modules.monitor.controller;

import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/monitor/dataflow")
@RequiredArgsConstructor
@Tag(name = "Phase 4: Data Flow Management", description = "数据流与链路追踪")
@CrossOrigin(origins = "*")
public class DataFlowController {

    private final AuditLogRepository auditLogRepository;

    @Operation(summary = "获取请求链路追踪数据")
    @GetMapping("/{traceId}")
    public TraceView getTrace(@PathVariable String traceId) {
        // In this implementation, traceId corresponds to the ID of the AuditLog
        AuditLog log = auditLogRepository.findById(traceId)
                .orElseThrow(() -> new RuntimeException("Trace not found for ID: " + traceId));

        return buildTraceView(log);
    }

    private TraceView buildTraceView(AuditLog log) {
        TraceView view = TraceView.builder()
                .traceId(log.getId())
                .status(Boolean.TRUE.equals(log.getSuccess()) ? "SUCCESS" : "FAILURE")
                .totalDurationMs(log.getResponseTime())
                .stages(new ArrayList<>())
                .build();

        // Stage 1: Request Received
        view.getStages().add(TraceStage.builder()
                .name("Request Gateway")
                .timestamp(log.getCreatedAt())
                .details("Received " + log.getMethod() + " request from " + log.getIpAddress())
                .status("SUCCESS")
                .build());

        // Stage 2: Processing (Simulated time, as we don't capture start/end separately
        // in audit log yet, assuming mostly linear)
        view.getStages().add(TraceStage.builder()
                .name("Agent Processing")
                .timestamp(log.getCreatedAt().plusNanos(log.getResponseTime() * 1000000 / 4)) // arbitrary point
                .details("Agent: " + log.getModel() + " (" + log.getProviderType() + ")")
                .status("SUCCESS")
                .build());

        // Stage 3: LLM Interaction
        view.getStages().add(TraceStage.builder()
                .name("LLM Provider")
                .timestamp(log.getCreatedAt().plusNanos(log.getResponseTime() * 1000000 / 2))
                .details("Provider: " + log.getProviderType() + ", Tokens: " + log.getTotalTokens())
                .status("SUCCESS")
                .build());

        // Stage 4: Response
        view.getStages().add(TraceStage.builder()
                .name("Response")
                .timestamp(log.getCreatedAt().plusNanos(log.getResponseTime() * 1000000))
                .details("Sent " + log.getStatusCode() + " response")
                .status(Boolean.TRUE.equals(log.getSuccess()) ? "SUCCESS" : "FAILURE")
                .build());

        return view;
    }

    @Data
    @Builder
    public static class TraceView {
        private String traceId;
        private String status;
        private Long totalDurationMs;
        private List<TraceStage> stages;
    }

    @Data
    @Builder
    public static class TraceStage {
        private String name;
        private LocalDateTime timestamp;
        private String details;
        private String status;
    }
}
