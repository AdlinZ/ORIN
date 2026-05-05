package com.adlin.orin.modules.task.consumer;

import com.adlin.orin.modules.task.dto.TaskMessage;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.repository.TaskRepository;
import com.adlin.orin.modules.workflow.engine.WorkflowEngine;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskQueueConsumerTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private WorkflowEngine workflowEngine;
    @Mock
    private DeadLetterHandler deadLetterHandler;

    private TaskQueueConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TaskQueueConsumer(taskRepository, workflowEngine, deadLetterHandler);
        ReflectionTestUtils.setField(consumer, "maxRetries", 0);
        ReflectionTestUtils.setField(consumer, "initialInterval", 1000L);
        ReflectionTestUtils.setField(consumer, "multiplier", 2.0d);
        ReflectionTestUtils.setField(consumer, "maxInterval", 30000L);
    }

    @Test
    void consumeTask_ShouldCompleteTaskOnlyWhenInstanceSucceeds() {
        TaskEntity task = TaskEntity.builder()
                .taskId("task-1")
                .workflowId(10L)
                .workflowInstanceId(20L)
                .status(TaskEntity.TaskStatus.QUEUED)
                .build();
        TaskMessage message = TaskMessage.builder()
                .taskId("task-1")
                .workflowId(10L)
                .workflowInstanceId(20L)
                .build();
        WorkflowInstanceEntity instance = WorkflowInstanceEntity.builder()
                .id(20L)
                .status(WorkflowInstanceEntity.InstanceStatus.SUCCESS)
                .outputData(Map.of("result", "ok"))
                .build();

        when(taskRepository.findByTaskId("task-1")).thenReturn(Optional.of(task));
        when(workflowEngine.executeInstance(20L)).thenReturn(instance);

        consumer.consumeTask(message);

        assertThat(task.getStatus()).isEqualTo(TaskEntity.TaskStatus.COMPLETED);
        assertThat(task.getOutputData()).containsEntry("result", "ok");
        verify(deadLetterHandler, never()).moveToDeadLetter(any(), any(), anyString());
    }

    @Test
    void consumeTask_ShouldMoveToDeadLetterWhenInstanceFailsAndRetriesExhausted() {
        TaskEntity task = TaskEntity.builder()
                .taskId("task-2")
                .workflowId(10L)
                .workflowInstanceId(21L)
                .status(TaskEntity.TaskStatus.QUEUED)
                .build();
        TaskMessage message = TaskMessage.builder()
                .taskId("task-2")
                .workflowId(10L)
                .workflowInstanceId(21L)
                .retryCount(0)
                .maxRetries(0)
                .build();
        WorkflowInstanceEntity instance = WorkflowInstanceEntity.builder()
                .id(21L)
                .status(WorkflowInstanceEntity.InstanceStatus.FAILED)
                .errorMessage("node failed")
                .build();

        when(taskRepository.findByTaskId("task-2")).thenReturn(Optional.of(task));
        when(workflowEngine.executeInstance(21L)).thenReturn(instance);
        doAnswer(invocation -> {
            TaskEntity deadTask = invocation.getArgument(0);
            deadTask.setStatus(TaskEntity.TaskStatus.DEAD);
            return null;
        }).when(deadLetterHandler).moveToDeadLetter(eq(task), eq(message), anyString());

        consumer.consumeTask(message);

        verify(deadLetterHandler).moveToDeadLetter(eq(task), eq(message), contains("node failed"));
        assertThat(task.getStatus()).isEqualTo(TaskEntity.TaskStatus.DEAD);
        assertThat(task.getErrorMessage()).contains("node failed");
    }
}
