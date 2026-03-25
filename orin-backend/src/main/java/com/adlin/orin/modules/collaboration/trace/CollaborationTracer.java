package com.adlin.orin.modules.collaboration.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协作链路追踪服务 - 为协作任务创建分布式追踪 span
 */
@Slf4j
@Component
public class CollaborationTracer {

    private Tracer tracer;
    private final Map<String, Span> activeSpans = new ConcurrentHashMap<>();

    @Autowired(required = false)
    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
        if (tracer != null) {
            log.info("CollaborationTracer initialized with OpenTelemetry tracer");
        } else {
            log.info("CollaborationTracer initialized without tracer (OpenTelemetry disabled)");
        }
    }

    /**
     * 开始任务包追踪
     */
    public Span startPackageSpan(String packageId, String traceId) {
        if (tracer == null) {
            log.debug("Tracer not available, skipping span creation for package: {}", packageId);
            return null;
        }
        Span span = tracer.spanBuilder("collaboration.package:" + packageId)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("collaboration.packageId", packageId)
                .setAttribute("collaboration.type", "package")
                .setAttribute("trace.id", traceId != null ? traceId : "")
                .startSpan();

        activeSpans.put(packageId, span);
        log.debug("Started collaboration package span: {}", packageId);

        return span;
    }

    /**
     * 开始子任务追踪
     */
    public Span startSubtaskSpan(String packageId, String subTaskId, String role) {
        if (tracer == null) {
            log.debug("Tracer not available, skipping span creation for subtask: {}", subTaskId);
            return null;
        }
        Span parentSpan = activeSpans.get(packageId);

        Span span = tracer.spanBuilder("collaboration.subtask:" + subTaskId)
                .setSpanKind(SpanKind.CLIENT)
                .setParent(io.opentelemetry.context.Context.current().with(parentSpan))
                .setAttribute("collaboration.packageId", packageId)
                .setAttribute("collaboration.subtaskId", subTaskId)
                .setAttribute("collaboration.role", role != null ? role : "")
                .setAttribute("collaboration.type", "subtask")
                .startSpan();

        log.debug("Started collaboration subtask span: {}", subTaskId);

        return span;
    }

    /**
     * 开始 Agent 执行追踪
     */
    public Span startAgentSpan(String packageId, String subTaskId, String agentId, String role) {
        if (tracer == null) {
            log.debug("Tracer not available, skipping span creation for agent: {}", agentId);
            return null;
        }
        Span span = tracer.spanBuilder("collaboration.agent:" + agentId)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("collaboration.packageId", packageId)
                .setAttribute("collaboration.subtaskId", subTaskId != null ? subTaskId : "")
                .setAttribute("collaboration.agentId", agentId)
                .setAttribute("collaboration.role", role != null ? role : "")
                .setAttribute("collaboration.type", "agent")
                .startSpan();

        log.debug("Started collaboration agent span: {}", agentId);

        return span;
    }

    /**
     * 记录子任务分配事件
     */
    public void recordSubtaskAssigned(String packageId, String subTaskId, String agentId) {
        Span span = activeSpans.get(packageId);
        if (span != null) {
            span.addEvent("subtask.assigned");
            span.setAttribute("subtaskId", subTaskId);
            span.setAttribute("agentId", agentId);
        }
    }

    /**
     * 记录子任务完成事件
     */
    public void recordSubtaskCompleted(String packageId, String subTaskId, boolean success) {
        Span span = activeSpans.get(packageId);
        if (span != null) {
            span.addEvent("subtask.completed");
            span.setAttribute("subtaskId", subTaskId);
            span.setAttribute("success", success);
        }
    }

    /**
     * 记录共识达成
     */
    public void recordConsensus(String packageId, String strategy, boolean reached) {
        Span span = activeSpans.get(packageId);
        if (span != null) {
            span.addEvent("consensus.reached");
            span.setAttribute("strategy", strategy);
            span.setAttribute("reached", reached);
        }
    }

    /**
     * 记录回退触发
     */
    public void recordFallback(String packageId, String reason) {
        Span span = activeSpans.get(packageId);
        if (span != null) {
            span.setStatus(StatusCode.ERROR, "Fallback triggered: " + reason);
            span.addEvent("fallback.triggered");
            span.setAttribute("reason", reason);
        }
    }

    /**
     * 记录错误
     */
    public void recordError(String packageId, String message, Throwable throwable) {
        Span span = activeSpans.get(packageId);
        if (span != null) {
            span.setStatus(StatusCode.ERROR, message);
            span.recordException(throwable);
        }
    }

    /**
     * 完成任务包追踪
     */
    public void endPackageSpan(String packageId, String status, String result) {
        Span span = activeSpans.remove(packageId);
        if (span != null) {
            if ("COMPLETED".equals(status)) {
                span.setStatus(StatusCode.OK);
            } else {
                span.setStatus(StatusCode.ERROR, status);
            }

            if (result != null) {
                span.setAttribute("collaboration.result", result);
            }

            span.end();
            log.debug("Ended collaboration package span: {}", packageId);
        }
    }

    /**
     * 完成子任务追踪
     */
    public void endSubtaskSpan(Span span, boolean success, String result, String error) {
        if (span != null) {
            if (success) {
                span.setStatus(StatusCode.OK);
                if (result != null) {
                    span.setAttribute("collaboration.result", result);
                }
            } else {
                span.setStatus(StatusCode.ERROR, error != null ? error : "Failed");
            }
            span.end();
        }
    }

    /**
     * 获取当前活跃的 span
     */
    public Span getActiveSpan(String packageId) {
        return activeSpans.get(packageId);
    }

    /**
     * 创建异步执行的子 span
     */
    public Span createChildSpan(String parentPackageId, String operationName) {
        if (tracer == null) {
            return null;
        }
        Span parentSpan = activeSpans.get(parentPackageId);
        if (parentSpan != null) {
            return tracer.spanBuilder(operationName)
                    .setParent(io.opentelemetry.context.Context.current().with(parentSpan))
                    .startSpan();
        }
        return tracer.spanBuilder(operationName).startSpan();
    }
}