package com.adlin.orin.modules.task.service;

import com.adlin.orin.modules.task.dto.TaskMessage;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.producer.TaskQueueProducer;
import com.adlin.orin.modules.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskQueueProducer taskQueueProducer;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, taskQueueProducer);
        ReflectionTestUtils.setField(taskService, "defaultMaxRetries", 3);
    }

    @Test
    void replayTask_ShouldCreateQueuedTaskWithoutReusingWorkflowInstance() {
        TaskEntity failedTask = TaskEntity.builder()
                .taskId("task-old")
                .workflowId(42L)
                .workflowInstanceId(99L)
                .priority(TaskEntity.TaskPriority.NORMAL)
                .status(TaskEntity.TaskStatus.FAILED)
                .inputData(Map.of("query", "retry"))
                .triggeredBy("tester")
                .triggerSource("API")
                .maxRetries(3)
                .build();

        when(taskRepository.findByTaskId("task-old")).thenReturn(Optional.of(failedTask));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskEntity replayed = taskService.replayTask("task-old");

        assertThat(replayed.getWorkflowInstanceId()).isNull();
        ArgumentCaptor<TaskMessage> messageCaptor = ArgumentCaptor.forClass(TaskMessage.class);
        verify(taskQueueProducer).sendTask(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getWorkflowInstanceId()).isNull();
    }

    @Test
    void createAndEnqueueTask_WhenQueueUnavailable_ShouldReturnFailedTask() {
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("Connection refused")).when(taskQueueProducer).sendTask(any(TaskMessage.class));

        TaskEntity task = taskService.createAndEnqueueTask(
                42L,
                99L,
                Map.of("query", "hello"),
                TaskEntity.TaskPriority.NORMAL,
                "tester",
                "API");

        assertThat(task.getStatus()).isEqualTo(TaskEntity.TaskStatus.FAILED);
        assertThat(task.getWorkflowInstanceId()).isEqualTo(99L);
        assertThat(task.getErrorMessage()).contains("任务队列不可用");
    }
}
