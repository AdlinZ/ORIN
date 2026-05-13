package com.adlin.orin.modules.collaboration.consumer;

import com.adlin.orin.modules.collaboration.dto.CollabTaskResult;
import com.adlin.orin.modules.collaboration.event.CollaborationEventBus;
import com.adlin.orin.modules.collaboration.metrics.CollaborationMetricsService;
import com.adlin.orin.modules.collaboration.producer.CollaborationMQProducer;
import com.adlin.orin.modules.collaboration.service.CollaborationOrchestrator;
import com.adlin.orin.modules.collaboration.service.CollaborationRedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationResultListenerTest {

    @Mock
    private CollaborationOrchestrator orchestrator;
    @Mock
    private CollaborationEventBus eventBus;
    @Mock
    private CollaborationMetricsService metricsService;
    @Mock
    private CollaborationMQProducer mqProducer;
    @Mock
    private CollaborationRedisService redisService;

    @Test
    @DisplayName("CollabTaskResult 反序列化应忽略 AI Engine 附加字段")
    void collabTaskResult_ignoresUnknownFields() throws Exception {
        String json = """
                {
                  "packageId": "pkg-json",
                  "subTaskId": "sub-json",
                  "status": "COMPLETED",
                  "result": "ok",
                  "correlationId": "pkg-json:sub-json"
                }
                """;

        CollabTaskResult result = new ObjectMapper().readValue(json, CollabTaskResult.class);

        assertEquals("pkg-json", result.getPackageId());
        assertEquals("sub-json", result.getSubTaskId());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals("ok", result.getResult());
    }

    @Test
    @DisplayName("SUCCESS 回执写入完整 branch_result 且状态更新不覆盖 payload")
    void handleResult_completedKeepsFullBranchPayload() throws Exception {
        CollaborationResultListener listener = new CollaborationResultListener(
                orchestrator,
                eventBus,
                metricsService,
                mqProducer,
                redisService
        );
        CompletableFuture<String> future = spy(new CompletableFuture<>());
        when(redisService.writeBranchResultAndIncrement(eq("pkg-success"), eq("workflow_1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1L);
        listener.registerCallback("pkg-success:workflow_1", future);

        CollabTaskResult result = CollabTaskResult.builder()
                .packageId("pkg-success")
                .subTaskId("workflow_1")
                .traceId("trace-success")
                .attempt(0)
                .status("COMPLETED")
                .result("Workflow enqueued: taskId=task-123, workflowInstanceId=37")
                .latencyMs(120L)
                .metadata(Map.of(
                        "expectedRole", "WORKFLOW",
                        "toolTrace", Map.of("workflowInstanceId", 37)
                ))
                .build();

        listener.handleResult(result);

        assertEquals("Workflow enqueued: taskId=task-123, workflowInstanceId=37", future.get());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(redisService).writeBranchResultAndIncrement(
                eq("pkg-success"),
                eq("workflow_1"),
                payloadCaptor.capture()
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertEquals("Workflow enqueued: taskId=task-123, workflowInstanceId=37", payload.get("result"));
        assertEquals("COMPLETED", payload.get("status"));
        assertEquals(0, payload.get("attempt"));
        assertEquals(Map.of("workflowInstanceId", 37), payload.get("toolTrace"));

        verify(orchestrator).updateSubtaskStatusOnly(
                "pkg-success",
                "workflow_1",
                "COMPLETED",
                "Workflow enqueued: taskId=task-123, workflowInstanceId=37",
                null
        );
        verify(orchestrator, never()).updateSubtaskStatus(
                eq("pkg-success"),
                eq("workflow_1"),
                eq("COMPLETED"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );

        InOrder inOrder = inOrder(redisService, orchestrator, future);
        inOrder.verify(redisService).writeBranchResultAndIncrement(
                eq("pkg-success"),
                eq("workflow_1"),
                org.mockito.ArgumentMatchers.any()
        );
        inOrder.verify(orchestrator).updateSubtaskStatusOnly(
                "pkg-success",
                "workflow_1",
                "COMPLETED",
                "Workflow enqueued: taskId=task-123, workflowInstanceId=37",
                null
        );
        inOrder.verify(future).complete("Workflow enqueued: taskId=task-123, workflowInstanceId=37");
    }
}
