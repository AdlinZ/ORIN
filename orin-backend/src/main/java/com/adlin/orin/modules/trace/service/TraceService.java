package com.adlin.orin.modules.trace.service;

import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.repository.TaskRepository;
import com.adlin.orin.modules.trace.entity.WorkflowTraceEntity;
import com.adlin.orin.modules.trace.repository.WorkflowTraceRepository;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import com.adlin.orin.modules.workflow.repository.WorkflowInstanceRepository;
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
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final TaskRepository taskRepository;
    private final CollaborationPackageRepository collaborationPackageRepository;
    private final AuditLogRepository auditLogRepository;
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

    /**
     * 获取 traceId 的脱敏关联对象摘要。
     */
    public Map<String, Object> getTraceSummary(String traceId) {
        List<WorkflowTraceEntity> traceSteps = queryTracesByTraceId(traceId);
        WorkflowInstanceEntity workflowInstance = workflowInstanceRepository.findByTraceId(traceId).orElse(null);
        List<TaskEntity> workflowTasks = workflowInstance == null
                ? List.of()
                : taskRepository.findByWorkflowInstanceId(workflowInstance.getId());
        List<CollaborationPackageEntity> collaborationPackages = collaborationPackageRepository.findByTraceId(traceId);
        List<AuditLog> auditLogs = auditLogRepository.findByTraceIdOrderByCreatedAtAsc(traceId);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("traceId", traceId);
        summary.put("found", workflowInstance != null
                || !workflowTasks.isEmpty()
                || !collaborationPackages.isEmpty()
                || !auditLogs.isEmpty()
                || !traceSteps.isEmpty());
        summary.put("workflowInstance", sanitizeWorkflowInstance(workflowInstance));
        summary.put("workflowTasks", workflowTasks.stream()
                .map(this::sanitizeTask)
                .toList());
        summary.put("collaborationPackages", collaborationPackages.stream()
                .map(this::sanitizeCollaborationPackage)
                .toList());
        summary.put("auditLogs", auditLogs.stream()
                .map(this::sanitizeAuditLog)
                .toList());
        summary.put("traceSteps", traceSteps.stream()
                .map(this::sanitizeTraceStep)
                .toList());
        summary.put("counts", Map.of(
                "workflowTasks", workflowTasks.size(),
                "collaborationPackages", collaborationPackages.size(),
                "auditLogs", auditLogs.size(),
                "traceSteps", traceSteps.size()
        ));
        return summary;
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

    private Map<String, Object> sanitizeWorkflowInstance(WorkflowInstanceEntity instance) {
        if (instance == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", instance.getId());
        result.put("workflowId", instance.getWorkflowId());
        result.put("traceId", instance.getTraceId());
        result.put("status", enumName(instance.getStatus()));
        result.put("startedAt", instance.getStartedAt());
        result.put("completedAt", instance.getCompletedAt());
        result.put("durationMs", instance.getDurationMs());
        result.put("errorMessage", summarize(instance.getErrorMessage()));
        result.put("triggeredBy", instance.getTriggeredBy());
        result.put("triggerSource", instance.getTriggerSource());
        return result;
    }

    private Map<String, Object> sanitizeTask(TaskEntity task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getTaskId());
        result.put("workflowId", task.getWorkflowId());
        result.put("workflowInstanceId", task.getWorkflowInstanceId());
        result.put("taskCategory", enumName(task.getTaskCategory()));
        result.put("priority", enumName(task.getPriority()));
        result.put("status", enumName(task.getStatus()));
        result.put("retryCount", task.getRetryCount());
        result.put("maxRetries", task.getMaxRetries());
        result.put("nextRetryAt", task.getNextRetryAt());
        result.put("errorMessage", summarize(task.getErrorMessage()));
        result.put("deadLetterReason", summarize(task.getDeadLetterReason()));
        result.put("queuedAt", task.getQueuedAt());
        result.put("startedAt", task.getStartedAt());
        result.put("completedAt", task.getCompletedAt());
        result.put("durationMs", task.getDurationMs());
        result.put("createdAt", task.getCreatedAt());
        result.put("updatedAt", task.getUpdatedAt());
        return result;
    }

    private Map<String, Object> sanitizeCollaborationPackage(CollaborationPackageEntity collaborationPackage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("packageId", collaborationPackage.getPackageId());
        result.put("rootTaskId", collaborationPackage.getRootTaskId());
        result.put("status", collaborationPackage.getStatus());
        result.put("intentCategory", collaborationPackage.getIntentCategory());
        result.put("intentPriority", collaborationPackage.getIntentPriority());
        result.put("intentComplexity", collaborationPackage.getIntentComplexity());
        result.put("needReview", collaborationPackage.getNeedReview());
        result.put("needConsensus", collaborationPackage.getNeedConsensus());
        result.put("collaborationMode", collaborationPackage.getCollaborationMode());
        result.put("traceId", collaborationPackage.getTraceId());
        result.put("createdBy", collaborationPackage.getCreatedBy());
        result.put("createdAt", collaborationPackage.getCreatedAt());
        result.put("updatedAt", collaborationPackage.getUpdatedAt());
        result.put("completedAt", collaborationPackage.getCompletedAt());
        result.put("timeoutAt", collaborationPackage.getTimeoutAt());
        result.put("errorMessage", summarize(collaborationPackage.getErrorMessage()));
        return result;
    }

    private Map<String, Object> sanitizeAuditLog(AuditLog auditLog) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", auditLog.getId());
        result.put("userId", auditLog.getUserId());
        result.put("providerId", auditLog.getProviderId());
        result.put("providerType", auditLog.getProviderType());
        result.put("conversationId", auditLog.getConversationId());
        result.put("workflowId", auditLog.getWorkflowId());
        result.put("traceId", auditLog.getTraceId());
        result.put("endpoint", auditLog.getEndpoint());
        result.put("method", auditLog.getMethod());
        result.put("model", auditLog.getModel());
        result.put("statusCode", auditLog.getStatusCode());
        result.put("responseTime", auditLog.getResponseTime());
        result.put("promptTokens", auditLog.getPromptTokens());
        result.put("completionTokens", auditLog.getCompletionTokens());
        result.put("totalTokens", auditLog.getTotalTokens());
        result.put("estimatedCost", auditLog.getEstimatedCost());
        result.put("success", auditLog.getSuccess());
        result.put("errorMessage", summarize(auditLog.getErrorMessage()));
        result.put("createdAt", auditLog.getCreatedAt());
        return result;
    }

    private Map<String, Object> sanitizeTraceStep(WorkflowTraceEntity trace) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", trace.getId());
        result.put("traceId", trace.getTraceId());
        result.put("instanceId", trace.getInstanceId());
        result.put("stepId", trace.getStepId());
        result.put("stepName", trace.getStepName());
        result.put("skillId", trace.getSkillId());
        result.put("skillName", trace.getSkillName());
        result.put("status", enumName(trace.getStatus()));
        result.put("startedAt", trace.getStartedAt());
        result.put("completedAt", trace.getCompletedAt());
        result.put("durationMs", trace.getDurationMs());
        result.put("errorCode", trace.getErrorCode());
        result.put("errorMessage", summarize(trace.getErrorMessage()));
        result.put("cpuUsage", trace.getCpuUsage());
        result.put("memoryUsage", trace.getMemoryUsage());
        return result;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String summarize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int maxLength = 500;
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
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
