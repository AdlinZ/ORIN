package com.adlin.orin.modules.task.producer;

import com.adlin.orin.common.trace.TraceContext;
import com.adlin.orin.modules.task.dto.TaskMessage;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskPriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskQueueProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private TaskQueueProducer producer;

    @BeforeEach
    void setUp() {
        producer = new TaskQueueProducer(rabbitTemplate);
        ReflectionTestUtils.setField(producer, "exchangeName", "task-ex");
        ReflectionTestUtils.setField(producer, "routingKey", "task.rk");
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void sendTask_writesTraceparentAndPreservesPriority() {
        MDC.put(TraceContext.TRACE_ID_KEY, "ffffffffffffffffffffffffffffffff");
        TaskMessage msg = TaskMessage.builder()
                .taskId("task-1")
                .workflowId(10L)
                .priority(TaskPriority.HIGH)
                .build();

        producer.sendTask(msg);

        MessagePostProcessor mpp = captureMpp();
        MessageProperties props = applyMpp(mpp);

        // 原有 priority + deliveryMode 保留
        assertThat(props.getPriority()).isEqualTo(TaskPriority.HIGH.getValue());
        assertThat(props.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        // 新增 trace headers
        assertThat(props.getHeaders().get(TraceContext.TRACEPARENT_HEADER).toString())
                .matches("00-ffffffffffffffffffffffffffffffff-[0-9a-f]{16}-01");
        assertThat(props.getHeaders().get(TraceContext.TRACE_ID_HEADER))
                .isEqualTo("ffffffffffffffffffffffffffffffff");
    }

    @Test
    void sendTask_propagatesExistingMdcTraceId() {
        MDC.put(TraceContext.TRACE_ID_KEY, "11111111111111111111111111111111");
        TaskMessage msg = TaskMessage.builder()
                .taskId("task-2")
                .workflowId(11L)
                .priority(TaskPriority.NORMAL)
                .build();

        producer.sendTask(msg);

        MessagePostProcessor mpp = captureMpp();
        MessageProperties props = applyMpp(mpp);

        assertThat(props.getHeaders().get(TraceContext.TRACE_ID_HEADER))
                .isEqualTo("11111111111111111111111111111111");
    }

    @Test
    void sendTask_generatesWhenMdcEmpty() {
        TaskMessage msg = TaskMessage.builder()
                .taskId("task-3")
                .workflowId(12L)
                .priority(TaskPriority.NORMAL)
                .build();

        producer.sendTask(msg);

        MessagePostProcessor mpp = captureMpp();
        MessageProperties props = applyMpp(mpp);

        assertThat(props.getHeaders().get(TraceContext.TRACEPARENT_HEADER).toString())
                .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    }

    @Test
    void sendToRetry_delegatesToSendTask_carriesTrace() {
        MDC.put(TraceContext.TRACE_ID_KEY, "22222222222222222222222222222222");
        TaskMessage msg = TaskMessage.builder()
                .taskId("task-4")
                .workflowId(13L)
                .priority(TaskPriority.LOW)
                .build();

        producer.sendToRetry(msg);

        MessagePostProcessor mpp = captureMpp();
        MessageProperties props = applyMpp(mpp);

        // sendToRetry 把 priority 改成 HIGH 然后 delegate 到 sendTask
        assertThat(props.getPriority()).isEqualTo(TaskPriority.HIGH.getValue());
        assertThat(props.getHeaders().get(TraceContext.TRACEPARENT_HEADER).toString())
                .matches("00-22222222222222222222222222222222-[0-9a-f]{16}-01");
    }

    @Test
    void replayTask_delegatesToSendTask_carriesTrace() {
        MDC.put(TraceContext.TRACE_ID_KEY, "33333333333333333333333333333333");
        TaskMessage msg = TaskMessage.builder()
                .taskId("task-5")
                .workflowId(14L)
                .priority(TaskPriority.NORMAL)
                .build();

        producer.replayTask(msg);

        MessagePostProcessor mpp = captureMpp();
        MessageProperties props = applyMpp(mpp);

        assertThat(props.getHeaders().get(TraceContext.TRACEPARENT_HEADER).toString())
                .matches("00-33333333333333333333333333333333-[0-9a-f]{16}-01");
    }

    private MessagePostProcessor captureMpp() {
        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq("task-ex"), eq("task.rk"), any(TaskMessage.class), captor.capture());
        return captor.getValue();
    }

    private MessageProperties applyMpp(MessagePostProcessor mpp) {
        Message msg = mock(Message.class);
        MessageProperties props = new MessageProperties();
        when(msg.getMessageProperties()).thenReturn(props);
        mpp.postProcessMessage(msg);
        return props;
    }
}
