package com.adlin.orin.modules.workflow.engine;

import com.adlin.orin.modules.agent.service.AgentExecutor;
import com.adlin.orin.modules.skill.service.SkillService;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import com.adlin.orin.modules.workflow.repository.WorkflowInstanceRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowEngineTest {

    private WorkflowEngine workflowEngine;

    @Mock
    private WorkflowStepRepository stepRepository;
    @Mock
    private WorkflowInstanceRepository instanceRepository;
    @Mock
    private SkillService skillService;
    @Mock
    private AgentExecutor agentExecutor;
    @Mock
    private GraphExecutor graphExecutor;
    @Mock
    private WorkflowRepository workflowRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        workflowEngine = new WorkflowEngine(
                stepRepository,
                instanceRepository,
                skillService,
                agentExecutor,
                graphExecutor,
                workflowRepository);
    }

    @Test
    void testExecuteGraphBasedWorkflow() {
        Long workflowId = 1L;
        Long instanceId = 100L;
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("key", "value");

        WorkflowInstanceEntity instance = WorkflowInstanceEntity.builder()
                .id(instanceId)
                .workflowId(workflowId)
                .inputData(inputs)
                .status(WorkflowInstanceEntity.InstanceStatus.RUNNING)
                .startedAt(java.time.LocalDateTime.now())
                .build();

        WorkflowEntity workflow = new WorkflowEntity();
        Map<String, Object> definition = new HashMap<>();
        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", Collections.singletonList(new HashMap<>()));
        definition.put("graph", graph);
        workflow.setWorkflowDefinition(definition);

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

        Map<String, Object> expectedOutputs = new HashMap<>();
        expectedOutputs.put("result", "ok");
        when(graphExecutor.executeGraph(any(), any())).thenReturn(expectedOutputs);

        workflowEngine.executeInstance(instanceId);

        verify(graphExecutor).setInstanceId(instanceId);
        verify(graphExecutor).executeGraph(eq(graph), any());
        verify(instanceRepository, times(1)).save(any(WorkflowInstanceEntity.class));

        assertEquals(WorkflowInstanceEntity.InstanceStatus.SUCCESS, instance.getStatus());
        assertEquals(expectedOutputs, instance.getOutputData());
    }

    @Test
    void testExecuteInstanceNotFound() {
        Long instanceId = 1L;
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.empty());

        try {
            workflowEngine.executeInstance(instanceId);
        } catch (IllegalArgumentException e) {
            assertEquals("Instance not found: 1", e.getMessage());
        }
    }
}
