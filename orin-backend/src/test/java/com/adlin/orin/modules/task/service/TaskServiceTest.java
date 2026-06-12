package com.adlin.orin.modules.task.service;

import com.adlin.orin.common.exception.BusinessException;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
        assertThat(failedTask.getStatus()).isEqualTo(TaskEntity.TaskStatus.FAILED);
        ArgumentCaptor<TaskMessage> messageCaptor = ArgumentCaptor.forClass(TaskMessage.class);
        verify(taskQueueProducer).sendTask(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getWorkflowInstanceId()).isNull();
    }

    @Test
    void replayTask_WhenDead_ShouldCreateNewQueuedTaskAndKeepOriginalTerminal() {
        TaskEntity deadTask = TaskEntity.builder()
                .taskId("task-dead")
                .workflowId(42L)
                .priority(TaskEntity.TaskPriority.HIGH)
                .status(TaskEntity.TaskStatus.DEAD)
                .inputData(Map.of("query", "retry"))
                .triggeredBy("tester")
                .triggerSource("API")
                .maxRetries(3)
                .build();

        when(taskRepository.findByTaskId("task-dead")).thenReturn(Optional.of(deadTask));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskEntity replayed = taskService.replayTask("task-dead");

        assertThat(replayed.getStatus()).isEqualTo(TaskEntity.TaskStatus.QUEUED);
        assertThat(deadTask.getStatus()).isEqualTo(TaskEntity.TaskStatus.DEAD);
        verify(taskQueueProducer).sendTask(any(TaskMessage.class));
    }

    @Test
    void replayTask_WhenNotFailedOrDead_ShouldRejectWithoutMutatingTask() {
        TaskEntity completedTask = TaskEntity.builder()
                .taskId("task-completed")
                .workflowId(42L)
                .priority(TaskEntity.TaskPriority.NORMAL)
                .status(TaskEntity.TaskStatus.COMPLETED)
                .build();

        when(taskRepository.findByTaskId("task-completed")).thenReturn(Optional.of(completedTask));

        assertThatThrownBy(() -> taskService.replayTask("task-completed"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only FAILED or DEAD tasks can be replayed");

        assertThat(completedTask.getStatus()).isEqualTo(TaskEntity.TaskStatus.COMPLETED);
        verify(taskQueueProducer, never()).sendTask(any(TaskMessage.class));
    }

    @Test
    void cancelTask_WhenQueued_ShouldMarkCancelledAndSetCompletionFields() {
        TaskEntity queuedTask = TaskEntity.builder()
                .taskId("task-queued")
                .workflowId(42L)
                .priority(TaskEntity.TaskPriority.NORMAL)
                .status(TaskEntity.TaskStatus.QUEUED)
                .queuedAt(java.time.LocalDateTime.now().minusSeconds(2))
                .build();

        when(taskRepository.findByTaskId("task-queued")).thenReturn(Optional.of(queuedTask));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskEntity cancelled = taskService.cancelTask("task-queued");

        assertThat(cancelled.getStatus()).isEqualTo(TaskEntity.TaskStatus.CANCELLED);
        assertThat(cancelled.getCompletedAt()).isNotNull();
        assertThat(cancelled.getDurationMs()).isNotNull();
        assertThat(cancelled.getErrorMessage()).isEqualTo("Task cancelled by user");
        verify(taskQueueProducer, never()).sendTask(any(TaskMessage.class));
    }

    @Test
    void cancelTask_WhenNotQueued_ShouldRejectWithCurrentStatus() {
        TaskEntity runningTask = TaskEntity.builder()
                .taskId("task-running")
                .workflowId(42L)
                .priority(TaskEntity.TaskPriority.NORMAL)
                .status(TaskEntity.TaskStatus.RUNNING)
                .build();

        when(taskRepository.findByTaskId("task-running")).thenReturn(Optional.of(runningTask));

        assertThatThrownBy(() -> taskService.cancelTask("task-running"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Current status: RUNNING");

        assertThat(runningTask.getStatus()).isEqualTo(TaskEntity.TaskStatus.RUNNING);
        verify(taskRepository, never()).save(runningTask);
    }

    @Test
    void taskStatus_ShouldIncludeCancelledTerminalState() {
        assertThat(TaskEntity.TaskStatus.valueOf("CANCELLED")).isEqualTo(TaskEntity.TaskStatus.CANCELLED);
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

    @Test
    void createAndEnqueueTask_WhenTransactionActive_ShouldSendAfterCommit() {
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            taskService.createAndEnqueueTask(
                    42L,
                    99L,
                    Map.of("query", "hello"),
                    TaskEntity.TaskPriority.NORMAL,
                    "tester",
                    "API");

            verify(taskQueueProducer, never()).sendTask(any(TaskMessage.class));

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(taskQueueProducer).sendTask(any(TaskMessage.class));
    }
}
