package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.agent.service.AgentManageService;
import com.adlin.orin.modules.collaboration.config.CollaborationOrchestrationMode;
import com.adlin.orin.modules.collaboration.consumer.CollaborationResultListener;
import com.adlin.orin.modules.collaboration.dto.CollabTaskResult;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.event.CollaborationEventBus;
import com.adlin.orin.modules.collaboration.metrics.CollaborationMetricsService;
import com.adlin.orin.modules.collaboration.producer.CollaborationMQProducer;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.collaboration.service.selection.BiddingSelector;
import com.adlin.orin.modules.collaboration.service.selection.StaticSelector;
import com.adlin.orin.modules.observability.service.LangfuseObservabilityService;
import com.adlin.orin.modules.workflow.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationMqIsolationTest {

    @Mock
    private AgentManageService agentManageService;
    @Mock
    private CollaborationEventBus eventBus;
    @Mock
    private CollaborationMetricsService metricsService;
    @Mock
    private LangfuseObservabilityService langfuseService;
    @Mock
    private WorkflowService workflowService;
    @Mock
    private CollaborationMQProducer mqProducer;
    @Mock
    private CollaborationResultListener resultListener;
    @Mock
    private CollaborationPackageRepository packageRepository;
    @Mock
    private CollaborationMemoryService memoryService;
    @Mock
    private CollaborationRedisService redisService;
    @Mock
    private CollaborationOrchestrator orchestrator;
    @Mock
    private StaticSelector staticSelector;
    @Mock
    private BiddingSelector biddingSelector;

    private ObjectMapper objectMapper;
    private CollaborationOrchestrationMode orchestrationMode;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        orchestrationMode = new CollaborationOrchestrationMode();
        orchestrationMode.setMode("LANGGRAPH_MQ");
        orchestrationMode.setMqForParallel(true);
        orchestrationMode.setMqForSequential(false);
        orchestrationMode.setMqForConsensus(false);
        lenient().when(redisService.acquireLockWithToken(anyString(), anyString(), anyString(), any()))
                .thenReturn(true);
    }

    @Test
    @DisplayName("MQ 执行会按 subTaskId 写入 pending_task，避免并行分支覆盖")
    void executeSubtask_storesPendingTaskPerSubtask() {
        CollaborationExecutor executor = new CollaborationExecutor(
                agentManageService,
                eventBus,
                metricsService,
                objectMapper,
                langfuseService,
                workflowService,
                orchestrationMode,
                mqProducer,
                resultListener,
                packageRepository,
                memoryService,
                redisService,
                staticSelector,
                biddingSelector
        );

        CollabSubtaskEntity subtask = CollabSubtaskEntity.builder()
                .packageId("pkg-iso-001")
                .subTaskId("sub-1")
                .description("parallel branch A")
                .expectedRole("SPECIALIST")
                .inputData("{\"branch\":\"A\"}")
                .retryCount(0)
                .build();

        CollaborationPackageEntity pkg = CollaborationPackageEntity.builder()
                .packageId("pkg-iso-001")
                .collaborationMode("PARALLEL")
                .status("EXECUTING")
                .build();

        when(packageRepository.findByPackageId("pkg-iso-001")).thenReturn(Optional.of(pkg));
        when(memoryService.readAllBlackboard("pkg-iso-001")).thenReturn(Collections.emptyMap());
        when(memoryService.getCursor("pkg-iso-001")).thenReturn(null);

        executor.executeSubtask(subtask, "pkg-iso-001", "trace-iso-001");

        verify(redisService).updateContextField(
                eq("pkg-iso-001"),
                eq("pending_task:sub-1"),
                any(Map.class)
        );
    }

    @Test
    @DisplayName("MQ 重试会按 subTaskId 读取 pending_task，避免读取到其他分支上下文")
    void handleResult_retryReadsPendingTaskPerSubtask() {
        CollaborationResultListener listener = new CollaborationResultListener(
                orchestrator,
                eventBus,
                metricsService,
                mqProducer,
                redisService
        );

        CompletableFuture<String> future = new CompletableFuture<>();
        listener.registerCallback("pkg-iso-002:sub-2", future);

        Map<String, Object> pendingTask = Map.of(
                "collaborationMode", "PARALLEL",
                "expectedRole", "SPECIALIST",
                "description", "parallel branch B",
                "inputData", "{\"branch\":\"B\"}",
                "executionStrategy", "AGENT",
                "contextSnapshot", Map.of("blackboard", Map.of("seed", "value"))
        );
        when(redisService.getContextField("pkg-iso-002", "pending_task:sub-2"))
                .thenReturn(Optional.of(pendingTask));

        CollabTaskResult result = CollabTaskResult.builder()
                .packageId("pkg-iso-002")
                .subTaskId("sub-2")
                .traceId("trace-iso-002")
                .status("FAILED")
                .attempt(0)
                .errorMessage("temporary failure")
                .metadata(Map.of("maxRetries", 3))
                .build();

        listener.handleResult(result);

        verify(redisService).getContextField("pkg-iso-002", "pending_task:sub-2");

        ArgumentCaptor<com.adlin.orin.modules.collaboration.dto.CollabTaskMessage> messageCaptor =
                ArgumentCaptor.forClass(com.adlin.orin.modules.collaboration.dto.CollabTaskMessage.class);
        verify(mqProducer).sendToRetry(messageCaptor.capture());

        assertEquals("sub-2", messageCaptor.getValue().getSubTaskId());
        assertEquals("parallel branch B", messageCaptor.getValue().getDescription());
        assertEquals("{\"branch\":\"B\"}", messageCaptor.getValue().getInputData());
    }
}
