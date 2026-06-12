package com.adlin.orin.modules.task.consumer;

import com.adlin.orin.common.trace.TraceContext;
import com.adlin.orin.modules.task.dto.TaskMessage;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.repository.TaskRepository;
import com.adlin.orin.modules.workflow.engine.WorkflowEngine;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private static Message headerlessMessage() {
        Message amqpMessage = Mockito.mock(Message.class);
        MessageProperties messageProperties = new MessageProperties();
        Mockito.when(amqpMessage.getMessageProperties()).thenReturn(messageProperties);
        return amqpMessage;
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

        consumer.consumeTask(message, headerlessMessage());

        assertThat(task.getStatus()).isEqualTo(TaskEntity.TaskStatus.COMPLETED);
        assertThat(task.getOutputData()).containsEntry("result", "ok");
        verify(deadLetterHandler, never()).moveToDeadLetter(any(), any(), anyString());
    }

    @Test
    void consumeTask_ShouldMarkFailedWithoutRetryWhenMaxRetriesIsZero() {
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
        consumer.consumeTask(message, headerlessMessage());

        verify(deadLetterHandler, never()).moveToDeadLetter(any(), any(), anyString());
        assertThat(task.getStatus()).isEqualTo(TaskEntity.TaskStatus.FAILED);
        assertThat(task.getErrorMessage()).contains("node failed");
        assertThat(task.getCompletedAt()).isNotNull();
    }

    // ---- trace 传播 ----

    @Test
    void consumeTask_propagatesInboundTraceparentIntoMdc() {
        String inboundTraceId = "44444444444444444444444444444444";
        String inboundSpanId = "5555555555555555";
        TaskEntity task = TaskEntity.builder()
                .taskId("task-trace")
                .workflowId(10L)
                .workflowInstanceId(20L)
                .status(TaskEntity.TaskStatus.QUEUED)
                .build();
        TaskMessage message = TaskMessage.builder()
                .taskId("task-trace")
                .workflowId(10L)
                .workflowInstanceId(20L)
                .build();
        WorkflowInstanceEntity instance = WorkflowInstanceEntity.builder()
                .id(20L)
                .status(WorkflowInstanceEntity.InstanceStatus.SUCCESS)
                .outputData(Map.of("result", "ok"))
                .build();

        // 在 taskRepository 调用点 snapshot MDC，并 return task
        String[] mdcAtCall = new String[2];
        when(taskRepository.findByTaskId("task-trace")).thenAnswer(inv -> {
            mdcAtCall[0] = MDC.get(TraceContext.TRACE_ID_KEY);
            mdcAtCall[1] = MDC.get(TraceContext.SPAN_ID_KEY);
            return Optional.of(task);
        });
        when(workflowEngine.executeInstance(20L)).thenReturn(instance);

        Message amqpMessage = Mockito.mock(Message.class);
        MessageProperties props = new MessageProperties();
        props.setHeader(TraceContext.TRACEPARENT_HEADER,
                TraceContext.build(inboundTraceId, inboundSpanId));
        Mockito.when(amqpMessage.getMessageProperties()).thenReturn(props);

        consumer.consumeTask(message, amqpMessage);

        assertThat(mdcAtCall[0]).isEqualTo(inboundTraceId);
        assertThat(mdcAtCall[1]).isEqualTo(inboundSpanId);
        // finally 清空
        assertThat(MDC.get(TraceContext.TRACE_ID_KEY)).isNull();
        assertThat(MDC.get(TraceContext.SPAN_ID_KEY)).isNull();
    }

    @Test
    void consumeTask_mdcCleanedInFinallyOnException() {
        String inboundTraceId = "66666666666666666666666666666666";
        TaskEntity task = TaskEntity.builder()
                .taskId("task-err")
                .workflowId(10L)
                .workflowInstanceId(20L)
                .status(TaskEntity.TaskStatus.QUEUED)
                .build();
        TaskMessage message = TaskMessage.builder()
                .taskId("task-err")
                .workflowId(10L)
                .workflowInstanceId(20L)
                .build();

        when(taskRepository.findByTaskId("task-err")).thenReturn(Optional.of(task));
        when(workflowEngine.executeInstance(20L)).thenThrow(new RuntimeException("boom"));

        Message amqpMessage = Mockito.mock(Message.class);
        MessageProperties props = new MessageProperties();
        props.setHeader(TraceContext.TRACEPARENT_HEADER,
                TraceContext.build(inboundTraceId, "7777777777777777"));
        Mockito.when(amqpMessage.getMessageProperties()).thenReturn(props);

        // maxRetries=0 走 FAILED 分支，最终落到 save。MDC 已在 handleTaskFailure 里清完。
        consumer.consumeTask(message, amqpMessage);

        assertThat(MDC.get(TraceContext.TRACE_ID_KEY)).isNull();
        assertThat(MDC.get(TraceContext.SPAN_ID_KEY)).isNull();
    }

    @Test
    void consumeTask_missingHeader_generates() {
        TaskEntity task = TaskEntity.builder()
                .taskId("task-miss")
                .workflowId(10L)
                .workflowInstanceId(20L)
                .status(TaskEntity.TaskStatus.QUEUED)
                .build();
        TaskMessage message = TaskMessage.builder()
                .taskId("task-miss")
                .workflowId(10L)
                .workflowInstanceId(20L)
                .build();
        WorkflowInstanceEntity instance = WorkflowInstanceEntity.builder()
                .id(20L)
                .status(WorkflowInstanceEntity.InstanceStatus.SUCCESS)
                .outputData(Map.of())
                .build();

        // 在 taskRepository 调用点 snapshot MDC
        String[] mdcAtCall = new String[1];
        when(taskRepository.findByTaskId("task-miss")).thenAnswer(inv -> {
            mdcAtCall[0] = MDC.get(TraceContext.TRACE_ID_KEY);
            return Optional.of(task);
        });
        when(workflowEngine.executeInstance(20L)).thenReturn(instance);

        // 缺 header
        consumer.consumeTask(message, headerlessMessage());

        assertThat(mdcAtCall[0]).isNotNull();
        assertThat(mdcAtCall[0].length()).isEqualTo(32);
        assertThat(MDC.get(TraceContext.TRACE_ID_KEY)).isNull();
    }
}
