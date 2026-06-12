package com.adlin.orin.modules.collaboration.producer;

import com.adlin.orin.common.trace.TraceContext;
import com.adlin.orin.modules.collaboration.dto.CollabTaskMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
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
class CollaborationMQProducerTest {

    @Mock
    private RabbitTemplate collabRabbitTemplate;

    private CollaborationMQProducer producer;

    @BeforeEach
    void setUp() {
        producer = new CollaborationMQProducer(collabRabbitTemplate);
        ReflectionTestUtils.setField(producer, "exchangeName", "test-ex");
        ReflectionTestUtils.setField(producer, "routingKey", "test.key");
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // ---- sendTask ----

    @Test
    void sendTask_writesTraceparentHeader() {
        MDC.put(TraceContext.TRACE_ID_KEY, "cccccccccccccccccccccccccccccccc");
        CollabTaskMessage message = CollabTaskMessage.builder()
                .packageId("pkg-1").subTaskId("sub-1").attempt(0).build();

        producer.sendTask(message);

        MessagePostProcessor mpp = captureMpp();
        MessageProperties props = applyMpp(mpp);

        assertThat(props.getHeaders().get(TraceContext.TRACEPARENT_HEADER).toString())
                .matches("00-cccccccccccccccccccccccccccccccc-[0-9a-f]{16}-01");
        assertThat(props.getHeaders().get(TraceContext.TRACE_ID_HEADER))
                .isEqualTo("cccccccccccccccccccccccccccccccc");
    }

    @Test
    void sendTask_mdcEmpty_generatesFreshTraceparent() {
        CollabTaskMessage message = CollabTaskMessage.builder()
                .packageId("pkg-1").subTaskId("sub-1").attempt(0).build();

        producer.sendTask(message);

        MessagePostProcessor mpp = captureMpp();
        MessageProperties props = applyMpp(mpp);

        assertThat(props.getHeaders().get(TraceContext.TRACEPARENT_HEADER).toString())
                .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    }

    // ---- sendToRetry ----

    @Test
    void sendToRetry_writesTraceparentAndPreservesRetryHeaders() {
        MDC.put(TraceContext.TRACE_ID_KEY, "dddddddddddddddddddddddddddddddd");
        CollabTaskMessage message = CollabTaskMessage.builder()
                .packageId("pkg-2").subTaskId("sub-2").attempt(1).build();

        producer.sendToRetry(message);

        MessagePostProcessor mpp = captureMpp();
        MessageProperties props = applyMpp(mpp);

        // 原有重试 headers 保留
        assertThat(props.getHeaders().get("x-retry-count")).isEqualTo(2);
        assertThat(props.getHeaders().get("x-retry-delay")).isNotNull();
        assertThat(props.getPriority()).isEqualTo(9);
        // 新增 trace headers
        assertThat(props.getHeaders().get(TraceContext.TRACEPARENT_HEADER).toString())
                .matches("00-dddddddddddddddddddddddddddddddd-[0-9a-f]{16}-01");
        assertThat(props.getHeaders().get(TraceContext.TRACE_ID_HEADER))
                .isEqualTo("dddddddddddddddddddddddddddddddd");
    }

    // ---- sendTaskWithReplyTo ----

    @Test
    void sendTaskWithReplyTo_writesTraceparentAndPreservesReplyHeaders() {
        MDC.put(TraceContext.TRACE_ID_KEY, "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
        CollabTaskMessage message = CollabTaskMessage.builder()
                .packageId("pkg-3").subTaskId("sub-3").correlationId("corr-3").build();

        producer.sendTaskWithReplyTo(message, "reply-q");

        MessagePostProcessor mpp = captureMpp();
        MessageProperties props = applyMpp(mpp);

        // 原有 replyTo/correlationId 保留
        assertThat(props.getReplyTo()).isEqualTo("reply-q");
        assertThat(props.getCorrelationId()).isEqualTo("corr-3");
        // 新增 trace headers
        assertThat(props.getHeaders().get(TraceContext.TRACEPARENT_HEADER).toString())
                .matches("00-eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee-[0-9a-f]{16}-01");
        assertThat(props.getHeaders().get(TraceContext.TRACE_ID_HEADER))
                .isEqualTo("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
    }

    // ---- helpers ----

    private MessagePostProcessor captureMpp() {
        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(collabRabbitTemplate).convertAndSend(eq("test-ex"), eq("test.key"), any(CollabTaskMessage.class), captor.capture());
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
