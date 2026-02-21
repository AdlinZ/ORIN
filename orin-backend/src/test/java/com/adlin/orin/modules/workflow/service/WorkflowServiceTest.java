package com.adlin.orin.modules.workflow.service;

import com.adlin.orin.modules.workflow.dto.WorkflowRequest;
import com.adlin.orin.modules.workflow.dto.WorkflowStepRequest;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowStepEntity;
import com.adlin.orin.modules.workflow.repository.WorkflowRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WorkflowServiceTest {

        @Mock
        private WorkflowRepository workflowRepository;

        @Mock
        private WorkflowStepRepository stepRepository;

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
                                .workflowType(WorkflowEntity.WorkflowType.SEQUENTIAL)
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
                                .workflowType(WorkflowEntity.WorkflowType.SEQUENTIAL)
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
}
