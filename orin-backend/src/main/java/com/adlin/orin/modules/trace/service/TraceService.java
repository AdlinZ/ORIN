package com.adlin.orin.modules.trace.service;

import com.adlin.orin.modules.trace.entity.WorkflowTraceEntity;
import com.adlin.orin.modules.trace.repository.WorkflowTraceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * 追踪服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceService {

    private final WorkflowTraceRepository traceRepository;
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    /**
     * 记录追踪开始
     */
    @Transactional
    public WorkflowTraceEntity startTrace(
            String traceId,
            Long instanceId,
            Long stepId,
            String stepName,
            Long skillId,
            String skillName,
            Map<String, Object> inputData) {

        log.debug("Starting trace: traceId={}, stepName={}", traceId, stepName);

        WorkflowTraceEntity trace = WorkflowTraceEntity.builder()
                .traceId(traceId)
                .instanceId(instanceId)
                .stepId(stepId)
                .stepName(stepName)
                .skillId(skillId)
                .skillName(skillName)
                .status(WorkflowTraceEntity.TraceStatus.RUNNING)
                .inputData(inputData)
                .startedAt(LocalDateTime.now())
                .build();

        return traceRepository.save(trace);
    }

    /**
     * 记录追踪成功
     */
    @Transactional
    public void completeTrace(Long traceEntityId, Map<String, Object> outputData) {
        log.debug("Completing trace: id={}", traceEntityId);

        WorkflowTraceEntity trace = traceRepository.findById(traceEntityId)
                .orElseThrow(() -> new IllegalArgumentException("Trace not found: " + traceEntityId));

        trace.setStatus(WorkflowTraceEntity.TraceStatus.SUCCESS);
        trace.setOutputData(outputData);
        trace.setCompletedAt(LocalDateTime.now());
        trace.setDurationMs(calculateDuration(trace.getStartedAt(), trace.getCompletedAt()));

        // 记录性能指标
        recordPerformanceMetrics(trace);

        traceRepository.save(trace);
    }

    /**
     * 记录追踪失败
     */
    @Transactional
    public void failTrace(Long traceEntityId, String errorCode, String errorMessage, Map<String, Object> errorDetails) {
        log.debug("Failing trace: id={}, error={}", traceEntityId, errorMessage);

        WorkflowTraceEntity trace = traceRepository.findById(traceEntityId)
                .orElseThrow(() -> new IllegalArgumentException("Trace not found: " + traceEntityId));

        trace.setStatus(WorkflowTraceEntity.TraceStatus.FAILED);
        trace.setErrorCode(errorCode);
        trace.setErrorMessage(errorMessage);
        trace.setErrorDetails(errorDetails);
        trace.setCompletedAt(LocalDateTime.now());
        trace.setDurationMs(calculateDuration(trace.getStartedAt(), trace.getCompletedAt()));

        // 记录性能指标
        recordPerformanceMetrics(trace);

        traceRepository.save(trace);
    }

    /**
     * 记录追踪跳过
     */
    @Transactional
    public void skipTrace(
            String traceId,
            Long instanceId,
            Long stepId,
            String stepName,
            String reason) {

        log.debug("Skipping trace: stepName={}, reason={}", stepName, reason);

        WorkflowTraceEntity trace = WorkflowTraceEntity.builder()
                .traceId(traceId)
                .instanceId(instanceId)
                .stepId(stepId)
                .stepName(stepName)
                .status(WorkflowTraceEntity.TraceStatus.SKIPPED)
                .errorMessage(reason)
                .startedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .durationMs(0L)
                .build();

        traceRepository.save(trace);
    }

    /**
     * 查询追踪链路
     */
    public List<WorkflowTraceEntity> queryTracesByTraceId(String traceId) {
        return traceRepository.findByTraceIdOrderByStartedAtAsc(traceId);
    }

    /**
     * 查询实例的所有追踪
     */
    public List<WorkflowTraceEntity> queryTracesByInstanceId(Long instanceId) {
        return traceRepository.findByInstanceIdOrderByStartedAtAsc(instanceId);
    }

    /**
     * 获取追踪统计信息
     */
    public Map<String, Object> getTraceStats(String traceId) {
        List<WorkflowTraceEntity> traces = queryTracesByTraceId(traceId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSteps", traces.size());
        stats.put("successCount",
                traces.stream().filter(t -> t.getStatus() == WorkflowTraceEntity.TraceStatus.SUCCESS).count());
        stats.put("failedCount",
                traces.stream().filter(t -> t.getStatus() == WorkflowTraceEntity.TraceStatus.FAILED).count());
        stats.put("skippedCount",
                traces.stream().filter(t -> t.getStatus() == WorkflowTraceEntity.TraceStatus.SKIPPED).count());
        stats.put("totalDuration",
                traces.stream().mapToLong(t -> t.getDurationMs() != null ? t.getDurationMs() : 0).sum());
        stats.put("avgDuration", traces.stream()
                .filter(t -> t.getDurationMs() != null && t.getDurationMs() > 0)
                .mapToLong(WorkflowTraceEntity::getDurationMs)
                .average()
                .orElse(0.0));

        return stats;
    }

    /**
     * 查询最近调用链路摘要。
     */
    public List<Map<String, Object>> getRecentTraceSummaries(int size) {
        int limit = Math.max(1, Math.min(size, 100));
        List<WorkflowTraceEntity> recentTraces = traceRepository.findTop500ByOrderByStartedAtDesc();

        Map<String, List<WorkflowTraceEntity>> tracesById = recentTraces.stream()
                .filter(t -> t.getTraceId() != null && !t.getTraceId().isBlank())
                .collect(Collectors.groupingBy(
                        WorkflowTraceEntity::getTraceId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Map.Entry<String, List<WorkflowTraceEntity>> entry : tracesById.entrySet()) {
            if (summaries.size() >= limit) {
                break;
            }
            summaries.add(buildTraceSummary(entry.getKey(), entry.getValue()));
        }

        return summaries;
    }

    private Map<String, Object> buildTraceSummary(String traceId, List<WorkflowTraceEntity> traces) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("traceId", traceId);
        summary.put("instanceId", traces.stream()
                .map(WorkflowTraceEntity::getInstanceId)
                .filter(id -> id != null)
                .findFirst()
                .orElse(null));
        summary.put("status", resolveTraceStatus(traces));
        summary.put("totalSteps", traces.size());
        summary.put("failedCount", traces.stream()
                .filter(t -> t.getStatus() == WorkflowTraceEntity.TraceStatus.FAILED)
                .count());
        summary.put("totalDuration", traces.stream()
                .mapToLong(t -> t.getDurationMs() != null ? t.getDurationMs() : 0L)
                .sum());
        summary.put("firstStartedAt", traces.stream()
                .map(WorkflowTraceEntity::getStartedAt)
                .filter(t -> t != null)
                .min(LocalDateTime::compareTo)
                .orElse(null));
        summary.put("lastCompletedAt", traces.stream()
                .map(t -> t.getCompletedAt() != null ? t.getCompletedAt() : t.getStartedAt())
                .filter(t -> t != null)
                .max(LocalDateTime::compareTo)
                .orElse(null));
        return summary;
    }

    private String resolveTraceStatus(List<WorkflowTraceEntity> traces) {
        if (traces.stream().anyMatch(t -> t.getStatus() == WorkflowTraceEntity.TraceStatus.FAILED)) {
            return WorkflowTraceEntity.TraceStatus.FAILED.name();
        }
        if (traces.stream().anyMatch(t -> t.getStatus() == WorkflowTraceEntity.TraceStatus.RUNNING)) {
            return WorkflowTraceEntity.TraceStatus.RUNNING.name();
        }
        if (!traces.isEmpty()
                && traces.stream().allMatch(t -> t.getStatus() == WorkflowTraceEntity.TraceStatus.SUCCESS)) {
            return WorkflowTraceEntity.TraceStatus.SUCCESS.name();
        }
        if (!traces.isEmpty()
                && traces.stream().allMatch(t -> t.getStatus() == WorkflowTraceEntity.TraceStatus.SKIPPED)) {
            return WorkflowTraceEntity.TraceStatus.SKIPPED.name();
        }

        return traces.stream()
                .filter(t -> t.getStartedAt() != null)
                .max(Comparator.comparing(
                        WorkflowTraceEntity::getStartedAt))
                .map(WorkflowTraceEntity::getStatus)
                .map(Enum::name)
                .orElse(WorkflowTraceEntity.TraceStatus.PENDING.name());
    }

    /**
     * 计算执行时长
     */
    private Long calculateDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0L;
        }
        return java.time.Duration.between(start, end).toMillis();
    }

    /**
     * 记录性能指标
     */
    private void recordPerformanceMetrics(WorkflowTraceEntity trace) {
        try {
            // CPU 使用率
            double cpuLoad = osBean.getSystemLoadAverage();
            if (cpuLoad >= 0) {
                trace.setCpuUsage(BigDecimal.valueOf(cpuLoad * 100));
            }

            // 内存使用
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            trace.setMemoryUsage(usedMemory);

        } catch (Exception e) {
            log.warn("Failed to record performance metrics: {}", e.getMessage());
        }
    }
}
