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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceServiceTest {

    @Mock
    private WorkflowTraceRepository traceRepository;
    @Mock
    private WorkflowInstanceRepository workflowInstanceRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private CollaborationPackageRepository collaborationPackageRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    private TraceService traceService;

    @BeforeEach
    void setUp() {
        traceService = new TraceService(
                traceRepository,
                workflowInstanceRepository,
                taskRepository,
                collaborationPackageRepository,
                auditLogRepository);
    }

    @Test
    void getRecentTraceSummariesShouldGroupSortAndAggregateRecentTraces() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 3, 12, 0);
        when(traceRepository.findTop500ByOrderByStartedAtDesc()).thenReturn(List.of(
                trace("trace-new", 9L, WorkflowTraceEntity.TraceStatus.FAILED, now, now.plusSeconds(2), 2000L),
                trace("trace-new", 9L, WorkflowTraceEntity.TraceStatus.SUCCESS, now.minusSeconds(5), now.minusSeconds(1), 4000L),
                trace("trace-old", 8L, WorkflowTraceEntity.TraceStatus.SUCCESS, now.minusMinutes(1), now.minusMinutes(1).plusSeconds(1), 1000L)
        ));

        List<Map<String, Object>> result = traceService.getRecentTraceSummaries(20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0))
                .containsEntry("traceId", "trace-new")
                .containsEntry("instanceId", 9L)
                .containsEntry("status", "FAILED")
                .containsEntry("totalSteps", 2)
                .containsEntry("failedCount", 1L)
                .containsEntry("totalDuration", 6000L)
                .containsEntry("firstStartedAt", now.minusSeconds(5))
                .containsEntry("lastCompletedAt", now.plusSeconds(2));
        assertThat(result.get(1)).containsEntry("traceId", "trace-old");
    }

    @Test
    void getRecentTraceSummariesShouldClampSizeToOneWhenInvalid() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 3, 12, 0);
        when(traceRepository.findTop500ByOrderByStartedAtDesc()).thenReturn(List.of(
                trace("trace-a", 1L, WorkflowTraceEntity.TraceStatus.SUCCESS, now, now, 0L),
                trace("trace-b", 2L, WorkflowTraceEntity.TraceStatus.SUCCESS, now.minusSeconds(1), now, 0L)
        ));

        List<Map<String, Object>> result = traceService.getRecentTraceSummaries(0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("traceId", "trace-a");
    }

    @Test
    void getTraceSummaryShouldAggregateRelatedObjectsWithoutSensitivePayloads() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 18, 10, 0);
        WorkflowInstanceEntity instance = WorkflowInstanceEntity.builder()
                .id(7L)
                .workflowId(11L)
                .traceId("trace-1")
                .status(WorkflowInstanceEntity.InstanceStatus.FAILED)
                .inputData(Map.of("password", "secret"))
                .outputData(Map.of("token", "secret"))
                .errorStack("stack")
                .errorMessage("workflow failed")
                .startedAt(now)
                .durationMs(42L)
                .build();
        TaskEntity task = TaskEntity.builder()
                .taskId("task-1")
                .workflowId(11L)
                .workflowInstanceId(7L)
                .priority(TaskEntity.TaskPriority.NORMAL)
                .status(TaskEntity.TaskStatus.FAILED)
                .inputData(Map.of("inputData", "hidden"))
                .outputData(Map.of("outputData", "hidden"))
                .errorStack("stack")
                .errorMessage("task failed")
                .retryCount(1)
                .maxRetries(3)
                .queuedAt(now)
                .build();
        CollaborationPackageEntity collaborationPackage = CollaborationPackageEntity.builder()
                .packageId("pkg-1")
                .traceId("trace-1")
                .intent("full user request")
                .result("full result")
                .sharedContext("{\"token\":\"secret\"}")
                .strategy("{\"key\":\"secret\"}")
                .status("FAILED")
                .errorMessage("package failed")
                .createdAt(now)
                .updatedAt(now)
                .build();
        AuditLog auditLog = AuditLog.builder()
                .id("audit-1")
                .userId("u-1")
                .apiKeyId("sk-secret-id")
                .traceId("trace-1")
                .endpoint("/v1/chat/completions")
                .method("POST")
                .requestParams("{\"message\":\"hidden\"}")
                .responseContent("{\"token\":\"hidden\"}")
                .success(false)
                .errorMessage("audit failed")
                .createdAt(now)
                .build();

        when(traceRepository.findByTraceIdOrderByStartedAtAsc("trace-1")).thenReturn(List.of(
                trace("trace-1", 7L, WorkflowTraceEntity.TraceStatus.FAILED, now, now.plusSeconds(1), 1000L)
        ));
        when(workflowInstanceRepository.findByTraceId("trace-1")).thenReturn(Optional.of(instance));
        when(taskRepository.findByWorkflowInstanceId(7L)).thenReturn(List.of(task));
        when(collaborationPackageRepository.findByTraceId("trace-1")).thenReturn(List.of(collaborationPackage));
        when(auditLogRepository.findByTraceIdOrderByCreatedAtAsc("trace-1")).thenReturn(List.of(auditLog));

        Map<String, Object> result = traceService.getTraceSummary("trace-1");

        assertThat(result)
                .containsEntry("traceId", "trace-1")
                .containsEntry("found", true);
        assertThat(result.toString())
                .doesNotContain("inputData")
                .doesNotContain("outputData")
                .doesNotContain("requestParams")
                .doesNotContain("responseContent")
                .doesNotContain("apiKeyId")
                .doesNotContain("sk-secret-id")
                .doesNotContain("full user request")
                .doesNotContain("full result")
                .doesNotContain("sharedContext")
                .doesNotContain("strategy");
    }

    @Test
    void getTraceSummaryShouldReturnEmptySummaryForUnknownTraceId() {
        when(traceRepository.findByTraceIdOrderByStartedAtAsc("missing")).thenReturn(List.of());
        when(workflowInstanceRepository.findByTraceId("missing")).thenReturn(Optional.empty());
        when(collaborationPackageRepository.findByTraceId("missing")).thenReturn(List.of());
        when(auditLogRepository.findByTraceIdOrderByCreatedAtAsc("missing")).thenReturn(List.of());

        Map<String, Object> result = traceService.getTraceSummary("missing");

        assertThat(result)
                .containsEntry("traceId", "missing")
                .containsEntry("found", false)
                .containsEntry("workflowInstance", null);
        assertThat((List<?>) result.get("workflowTasks")).isEmpty();
        assertThat((List<?>) result.get("collaborationPackages")).isEmpty();
        assertThat((List<?>) result.get("auditLogs")).isEmpty();
        assertThat((List<?>) result.get("traceSteps")).isEmpty();
    }

    private WorkflowTraceEntity trace(
            String traceId,
            Long instanceId,
            WorkflowTraceEntity.TraceStatus status,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            Long durationMs) {
        return WorkflowTraceEntity.builder()
                .traceId(traceId)
                .instanceId(instanceId)
                .status(status)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .durationMs(durationMs)
                .build();
    }
}
