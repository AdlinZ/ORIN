package com.adlin.orin.modules.collaboration.controller;

import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.event.CollaborationEventBus;
import com.adlin.orin.modules.collaboration.metrics.CollaborationMetricsService;
import com.adlin.orin.modules.collaboration.service.CollaborationExecutor;
import com.adlin.orin.modules.collaboration.service.CollaborationMemoryService;
import com.adlin.orin.modules.collaboration.service.CollaborationOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationOrchestratorControllerTest {

    @Mock
    private CollaborationOrchestrator orchestrator;
    @Mock
    private CollaborationEventBus eventBus;
    @Mock
    private CollaborationExecutor executor;
    @Mock
    private CollaborationMemoryService memoryService;
    @Mock
    private CollaborationMetricsService metricsService;

    @Test
    @DisplayName("executeSubtask 在 MQ 已完成回写后不应重复推进 COMPLETED")
    void executeSubtask_skipsDuplicateCompletedTransition() {
        CollaborationOrchestratorController controller = new CollaborationOrchestratorController(
                orchestrator,
                eventBus,
                executor,
                memoryService,
                metricsService
        );

        CollabSubtaskEntity pending = CollabSubtaskEntity.builder()
                .packageId("pkg-ctrl-001")
                .subTaskId("sub-1")
                .description("test task")
                .status("PENDING")
                .build();
        CollabSubtaskEntity completed = CollabSubtaskEntity.builder()
                .packageId("pkg-ctrl-001")
                .subTaskId("sub-1")
                .description("test task")
                .status("COMPLETED")
                .build();

        when(orchestrator.getSubtasks("pkg-ctrl-001"))
                .thenReturn(List.of(pending), List.of(completed), List.of(completed));
        when(executor.executeSubtask(any(CollabSubtaskEntity.class), eq("pkg-ctrl-001"), eq("trace-ctrl-001")))
                .thenReturn(CompletableFuture.completedFuture("done"));
        when(orchestrator.autoScheduleIfPossible("pkg-ctrl-001"))
                .thenReturn(List.of());
        when(orchestrator.isAllSubtasksCompleted("pkg-ctrl-001"))
                .thenReturn(false);

        ResponseEntity<Map<String, Object>> response =
                controller.executeSubtask("pkg-ctrl-001", "sub-1", "trace-ctrl-001", null);

        assertEquals(202, response.getStatusCode().value());
        verify(orchestrator).updateSubtaskStatus("pkg-ctrl-001", "sub-1", "RUNNING", null, null);
        verify(orchestrator, never()).updateSubtaskStatus("pkg-ctrl-001", "sub-1", "COMPLETED", "done", null);
    }

    @Test
    @DisplayName("自动调度回调在子任务已由 MQ 回写后不应重复推进 FAILED")
    void scheduleNextSubtasks_skipsDuplicateFailedTransition() throws Exception {
        CollaborationOrchestratorController controller = new CollaborationOrchestratorController(
                orchestrator,
                eventBus,
                executor,
                memoryService,
                metricsService
        );

        CollabSubtaskEntity running = CollabSubtaskEntity.builder()
                .packageId("pkg-ctrl-002")
                .subTaskId("sub-2")
                .description("auto task")
                .status("RUNNING")
                .build();
        CollabSubtaskEntity failed = CollabSubtaskEntity.builder()
                .packageId("pkg-ctrl-002")
                .subTaskId("sub-2")
                .description("auto task")
                .status("FAILED")
                .build();

        when(orchestrator.autoScheduleIfPossible("pkg-ctrl-002"))
                .thenReturn(List.of(running), List.of());
        when(orchestrator.getSubtasks("pkg-ctrl-002"))
                .thenReturn(List.of(failed));
        when(executor.executeSubtask(running, "pkg-ctrl-002", "trace-ctrl-002"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("worker failed")));
        when(orchestrator.isAllSubtasksCompleted("pkg-ctrl-002"))
                .thenReturn(false);

        Method scheduleNextSubtasks = CollaborationOrchestratorController.class
                .getDeclaredMethod("scheduleNextSubtasks", String.class, String.class);
        scheduleNextSubtasks.setAccessible(true);
        scheduleNextSubtasks.invoke(controller, "pkg-ctrl-002", "trace-ctrl-002");

        verify(orchestrator, never()).updateSubtaskStatus("pkg-ctrl-002", "sub-2", "FAILED", null, "worker failed");
    }
}
