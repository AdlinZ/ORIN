package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.agent.service.AgentManageService;
import com.adlin.orin.modules.collaboration.config.CollaborationOrchestrationMode;
import com.adlin.orin.modules.collaboration.consumer.CollaborationResultListener;
import com.adlin.orin.modules.collaboration.dto.CollabTaskMessage;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationExecutorTest {

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
    private StaticSelector staticSelector;
    @Mock
    private BiddingSelector biddingSelector;

    @Test
    @DisplayName("inputData.workflowId 应映射为 MQ WORKFLOW 执行策略")
    void executeViaMq_usesWorkflowStrategyWhenInputDataContainsWorkflowId() {
        CollaborationOrchestrationMode orchestrationMode = new CollaborationOrchestrationMode();
        orchestrationMode.setMode("LANGGRAPH_MQ");
        CollaborationExecutor executor = new CollaborationExecutor(
                agentManageService,
                eventBus,
                metricsService,
                new ObjectMapper(),
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
                .packageId("pkg-workflow")
                .subTaskId("workflow-1")
                .description("run workflow")
                .expectedRole("SPECIALIST")
                .inputData("{\"workflowId\":42,\"inputs\":{\"topic\":\"collab\"}}")
                .status("PENDING")
                .retryCount(0)
                .build();
        CollaborationPackageEntity pkg = CollaborationPackageEntity.builder()
                .packageId("pkg-workflow")
                .collaborationMode("SEQUENTIAL")
                .status("EXECUTING")
                .build();
        when(redisService.getContextField("pkg-workflow", "branch_result:workflow-1")).thenReturn(Optional.empty());
        when(redisService.acquireLockWithToken(eq("pkg-workflow"), eq("workflow-1"), any(), any(Duration.class)))
                .thenReturn(true);
        when(packageRepository.findByPackageId("pkg-workflow")).thenReturn(Optional.of(pkg));
        when(redisService.loadContext("pkg-workflow")).thenReturn(Optional.of(Map.of("sessionId", "session-1")));
        when(memoryService.readAllBlackboard("pkg-workflow")).thenReturn(Map.of());
        when(memoryService.getCursor("pkg-workflow")).thenReturn(Map.of());

        executor.executeSubtask(subtask, "pkg-workflow", "trace-workflow");

        ArgumentCaptor<CollabTaskMessage> messageCaptor = ArgumentCaptor.forClass(CollabTaskMessage.class);
        verify(mqProducer).sendTask(messageCaptor.capture());
        CollabTaskMessage message = messageCaptor.getValue();
        assertThat(message.getExecutionStrategy()).isEqualTo("WORKFLOW");
        assertThat(message.getExpectedRole()).isEqualTo("SPECIALIST");
        assertThat(message.getInputData()).contains("\"workflowId\":42");
        verify(resultListener).registerCallback(eq("pkg-workflow:workflow-1"), any());
        verify(redisService).updateContextField(eq("pkg-workflow"), eq("pending_task:workflow-1"), any());
    }
}
