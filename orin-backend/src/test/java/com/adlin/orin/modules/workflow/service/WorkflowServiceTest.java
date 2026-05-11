package com.adlin.orin.modules.workflow.service;

import com.adlin.orin.modules.workflow.dto.WorkflowRequest;
import com.adlin.orin.modules.workflow.dto.WorkflowStepRequest;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.service.TaskService;
import com.adlin.orin.modules.workflow.converter.DifyDslConverter;
import com.adlin.orin.modules.workflow.dto.WorkflowExecutionSubmissionResponse;
import com.adlin.orin.modules.workflow.dsl.OrinWorkflowDslNormalizer;
import com.adlin.orin.modules.workflow.dsl.OrinWorkflowDslValidator;
import com.adlin.orin.modules.workflow.engine.WorkflowEngine;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowStepEntity;
import com.adlin.orin.modules.workflow.repository.WorkflowInstanceRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WorkflowServiceTest {

        @Mock
        private WorkflowRepository workflowRepository;

        @Mock
        private WorkflowStepRepository stepRepository;
        @Mock
        private WorkflowInstanceRepository instanceRepository;
        @Mock
        private WorkflowEngine workflowEngine;
        @Mock
        private DifyDslConverter difyDslConverter;
        @Spy
        private OrinWorkflowDslNormalizer workflowDslNormalizer = new OrinWorkflowDslNormalizer();
        @Mock
        private OrinWorkflowDslValidator workflowDslValidator;
        @Mock
        private TaskService taskService;

        @InjectMocks
        private WorkflowService workflowService;

        @BeforeEach
        void setUp() {
                MockitoAnnotations.openMocks(this);
        }

        @Test
        void createWorkflow_WithAgentSteps_ShouldSaveWorkflowAndSteps() {
                // Arrange: Create a multi-agent workflow (Researcher → Writer)
                Map<String, Object> step1Input = new HashMap<>();
                step1Input.put("message", "${context.topic}");

                Map<String, Object> step2Input = new HashMap<>();
                step2Input.put("message", "${step.1.response}");

                WorkflowStepRequest step1 = WorkflowStepRequest.builder()
                                .stepName("Research Agent")
                                .stepOrder(1)
                                .stepType("AGENT")
                                .agentId(101L)
                                .inputMapping(step1Input)
                                .build();

                WorkflowStepRequest step2 = WorkflowStepRequest.builder()
                                .stepName("Writer Agent")
                                .stepOrder(2)
                                .stepType("AGENT")
                                .agentId(102L)
                                .inputMapping(step2Input)
                                .build();

                WorkflowRequest request = WorkflowRequest.builder()
                                .workflowName("Research & Write Workflow")
                                .workflowType("SEQUENTIAL")
                                .steps(List.of(step1, step2))
                                .build();

                WorkflowEntity savedWorkflow = WorkflowEntity.builder()
                                .id(1L)
                                .workflowName("Research & Write Workflow")
                                .build();

                when(workflowRepository.existsByWorkflowName(anyString())).thenReturn(false);
                when(workflowRepository.save(any(WorkflowEntity.class))).thenReturn(savedWorkflow);
                when(workflowRepository.existsById(1L)).thenReturn(true);

                // Act
                workflowService.createWorkflow(request);

                // Assert
                verify(workflowRepository).save(any(WorkflowEntity.class));
                verify(stepRepository, times(2))
                                .save(argThat(step -> step.getStepType() == WorkflowStepEntity.StepType.AGENT &&
                                                step.getWorkflowId().equals(1L)));
        }

        @Test
        void createWorkflow_WithMixedSteps_ShouldSupportAgentAndSkill() {
                // Arrange: Workflow with both AGENT and SKILL steps
                WorkflowStepRequest agentStep = WorkflowStepRequest.builder()
                                .stepName("Agent Step")
                                .stepOrder(1)
                                .stepType("AGENT")
                                .agentId(100L)
                                .build();

                WorkflowStepRequest skillStep = WorkflowStepRequest.builder()
                                .stepName("Skill Step")
                                .stepOrder(2)
                                .stepType("SKILL")
                                .skillId(200L)
                                .build();

                WorkflowRequest request = WorkflowRequest.builder()
                                .workflowName("Mixed Workflow")
                                .workflowType("SEQUENTIAL")
                                .steps(List.of(agentStep, skillStep))
                                .build();

                WorkflowEntity savedWorkflow = WorkflowEntity.builder()
                                .id(2L)
                                .workflowName("Mixed Workflow")
                                .build();

                when(workflowRepository.existsByWorkflowName(anyString())).thenReturn(false);
                when(workflowRepository.save(any(WorkflowEntity.class))).thenReturn(savedWorkflow);
                when(workflowRepository.existsById(2L)).thenReturn(true);

                // Act
                workflowService.createWorkflow(request);

                // Assert
                verify(stepRepository).save(argThat(step -> step.getStepType() == WorkflowStepEntity.StepType.AGENT &&
                                step.getAgentId().equals(100L)));
                verify(stepRepository).save(argThat(step -> step.getStepType() == WorkflowStepEntity.StepType.SKILL &&
                                step.getSkillId().equals(200L)));
        }

        @Test
        void triggerWorkflowWithPriority_ShouldReturnRealTaskAndInstanceIds() {
                Long workflowId = 7L;
                Map<String, Object> inputs = Map.of("query", "hello");
                WorkflowEntity workflow = WorkflowEntity.builder()
                                .id(workflowId)
                                .workflowName("Published Workflow")
                                .status(WorkflowEntity.WorkflowStatus.ACTIVE)
                                .build();
                WorkflowInstanceEntity instance = WorkflowInstanceEntity.builder()
                                .id(88L)
                                .workflowId(workflowId)
                                .traceId("trace-88")
                                .build();
                TaskEntity task = TaskEntity.builder()
                                .taskId("task-88")
                                .workflowId(workflowId)
                                .workflowInstanceId(instance.getId())
                                .status(TaskEntity.TaskStatus.QUEUED)
                                .build();

                when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
                when(workflowEngine.validateWorkflow(workflowId)).thenReturn(true);
                when(workflowEngine.createInstance(eq(workflowId), any(), eq("tester"))).thenReturn(instance);
                when(taskService.createAndEnqueueTask(
                                eq(workflowId),
                                eq(instance.getId()),
                                any(),
                                eq(TaskEntity.TaskPriority.HIGH),
                                eq("tester"),
                                eq("API")))
                                .thenReturn(task);

                WorkflowExecutionSubmissionResponse response = workflowService.triggerWorkflowWithPriority(
                                workflowId,
                                inputs,
                                TaskEntity.TaskPriority.HIGH,
                                "tester");

                assertThat(response.getTaskId()).isEqualTo("task-88");
                assertThat(response.getWorkflowId()).isEqualTo(workflowId);
                assertThat(response.getWorkflowInstanceId()).isEqualTo(88L);
                assertThat(response.getTraceId()).isEqualTo("trace-88");
                assertThat(response.getStatusUrl()).isEqualTo("/api/v1/workflow-tasks/task-88");
                assertThat(response.getInstanceUrl()).isEqualTo("/api/workflows/instances/88");
        }

        @Test
        void triggerWorkflowWithPriority_ShouldRejectDraftWorkflow() {
                Long workflowId = 9L;
                WorkflowEntity workflow = WorkflowEntity.builder()
                                .id(workflowId)
                                .workflowName("Draft Workflow")
                                .status(WorkflowEntity.WorkflowStatus.DRAFT)
                                .build();

                when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

                assertThatThrownBy(() -> workflowService.triggerWorkflowWithPriority(
                                workflowId,
                                Map.of("query", "hello"),
                                TaskEntity.TaskPriority.NORMAL,
                                "tester"))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("must be published");

                verifyNoInteractions(taskService);
        }

        @Test
        void publishWorkflow_ShouldValidateAndActivateWorkflow() {
                Long workflowId = 10L;
                WorkflowEntity workflow = WorkflowEntity.builder()
                                .id(workflowId)
                                .workflowName("Ready Workflow")
                                .status(WorkflowEntity.WorkflowStatus.DRAFT)
                                .build();

                when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
                when(workflowEngine.validateWorkflow(workflowId)).thenReturn(true);
                when(workflowRepository.save(any(WorkflowEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                WorkflowEntity.WorkflowStatus status = workflowService.publishWorkflow(workflowId).getStatus();

                assertThat(status).isEqualTo(WorkflowEntity.WorkflowStatus.ACTIVE);
                verify(workflowEngine).validateWorkflow(workflowId);
        }
}
