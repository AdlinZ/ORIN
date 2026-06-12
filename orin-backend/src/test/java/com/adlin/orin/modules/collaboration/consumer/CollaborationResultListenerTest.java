package com.adlin.orin.modules.collaboration.consumer;

import com.adlin.orin.common.trace.TraceContext;
import com.adlin.orin.modules.collaboration.dto.CollabTaskMessage;
import com.adlin.orin.modules.collaboration.dto.CollabTaskResult;
import com.adlin.orin.modules.collaboration.event.CollaborationEventBus;
import com.adlin.orin.modules.collaboration.metrics.CollaborationMetricsService;
import com.adlin.orin.modules.collaboration.producer.CollaborationMQProducer;
import com.adlin.orin.modules.collaboration.service.CollaborationOrchestrator;
import com.adlin.orin.modules.collaboration.service.CollaborationRedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationResultListenerTest {

    @Mock
    private CollaborationOrchestrator orchestrator;
    @Mock
    private CollaborationEventBus eventBus;
    @Mock
    private CollaborationMetricsService metricsService;
    @Mock
    private CollaborationMQProducer mqProducer;
    @Mock
    private CollaborationRedisService redisService;

    @Test
    @DisplayName("CollabTaskResult 反序列化应忽略 AI Engine 附加字段")
    void collabTaskResult_ignoresUnknownFields() throws Exception {
        String json = """
                {
                  "packageId": "pkg-json",
                  "subTaskId": "sub-json",
                  "status": "COMPLETED",
                  "result": "ok",
                  "correlationId": "pkg-json:sub-json"
                }
                """;

        CollabTaskResult result = new ObjectMapper().readValue(json, CollabTaskResult.class);

        assertEquals("pkg-json", result.getPackageId());
        assertEquals("sub-json", result.getSubTaskId());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals("ok", result.getResult());
    }

    @Test
    @DisplayName("SUCCESS 回执写入完整 branch_result 且状态更新不覆盖 payload")
    void handleResult_completedKeepsFullBranchPayload() throws Exception {
        CollaborationResultListener listener = new CollaborationResultListener(
                orchestrator,
                eventBus,
                metricsService,
                mqProducer,
                redisService
        );
        CompletableFuture<String> future = spy(new CompletableFuture<>());
        when(redisService.writeBranchResultAndIncrement(eq("pkg-success"), eq("workflow_1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1L);
        listener.registerCallback("pkg-success:workflow_1", future);

        CollabTaskResult result = CollabTaskResult.builder()
                .packageId("pkg-success")
                .subTaskId("workflow_1")
                .traceId("trace-success")
                .attempt(0)
                .status("COMPLETED")
                .result("Workflow enqueued: taskId=task-123, workflowInstanceId=37")
                .latencyMs(120L)
                .metadata(Map.of(
                        "expectedRole", "WORKFLOW",
                        "toolTrace", Map.of("workflowInstanceId", 37)
                ))
                .build();

        Message message = org.mockito.Mockito.mock(Message.class);
        MessageProperties messageProperties = new MessageProperties();
        org.mockito.Mockito.when(message.getMessageProperties()).thenReturn(messageProperties);

        listener.handleResult(result, message);

        assertEquals("Workflow enqueued: taskId=task-123, workflowInstanceId=37", future.get());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(redisService).writeBranchResultAndIncrement(
                eq("pkg-success"),
                eq("workflow_1"),
                payloadCaptor.capture()
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertEquals("Workflow enqueued: taskId=task-123, workflowInstanceId=37", payload.get("result"));
        assertEquals("COMPLETED", payload.get("status"));
        assertEquals(0, payload.get("attempt"));
        assertEquals(Map.of("workflowInstanceId", 37), payload.get("toolTrace"));

        verify(orchestrator).updateSubtaskStatusOnly(
                "pkg-success",
                "workflow_1",
                "COMPLETED",
                "Workflow enqueued: taskId=task-123, workflowInstanceId=37",
                null
        );
        verify(orchestrator, never()).updateSubtaskStatus(
                eq("pkg-success"),
                eq("workflow_1"),
                eq("COMPLETED"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );

        InOrder inOrder = inOrder(redisService, orchestrator, future);
        inOrder.verify(redisService).writeBranchResultAndIncrement(
                eq("pkg-success"),
                eq("workflow_1"),
                org.mockito.ArgumentMatchers.any()
        );
        inOrder.verify(orchestrator).updateSubtaskStatusOnly(
                "pkg-success",
                "workflow_1",
                "COMPLETED",
                "Workflow enqueued: taskId=task-123, workflowInstanceId=37",
                null
        );
        inOrder.verify(future).complete("Workflow enqueued: taskId=task-123, workflowInstanceId=37");
    }

    // ---- trace 传播 ----

    @Nested
    @DisplayName("traceparent 灌 MDC 与 finally 清理")
    class TraceContextTests {

        @org.junit.jupiter.api.BeforeEach
        void clearMdc() {
            MDC.clear();
        }

        @AfterEach
        void tearDown() {
            MDC.clear();
        }

        @Test
        void handleResult_propagatesInboundTraceparentIntoMdc() {
            String inboundTraceId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa00";
            String inboundSpanId = "bbbbbbbbbbbbbbbb";
            Message message = Mockito.mock(Message.class);
            MessageProperties props = new MessageProperties();
            props.setHeader(TraceContext.TRACEPARENT_HEADER,
                    TraceContext.build(inboundTraceId, inboundSpanId));
            Mockito.when(message.getMessageProperties()).thenReturn(props);

            // 在 redisService 调用点 snapshot MDC
            String[] mdcAtCall = new String[2];
            doAnswer(inv -> {
                mdcAtCall[0] = MDC.get(TraceContext.TRACE_ID_KEY);
                mdcAtCall[1] = MDC.get(TraceContext.SPAN_ID_KEY);
                return 1L;
            }).when(redisService).writeBranchResultAndIncrement(anyString(), anyString(), any());

            CollaborationResultListener listener = new CollaborationResultListener(
                    orchestrator, eventBus, metricsService, mqProducer, redisService);
            CollabTaskResult result = CollabTaskResult.builder()
                    .packageId("pkg-trace")
                    .subTaskId("sub-trace")
                    .status("COMPLETED")
                    .result("ok")
                    .build();

            listener.handleResult(result, message);

            assertEquals(inboundTraceId, mdcAtCall[0]);
            assertEquals(inboundSpanId, mdcAtCall[1]);
            // 调用结束 finally 清空
            assertNull(MDC.get(TraceContext.TRACE_ID_KEY));
            assertNull(MDC.get(TraceContext.SPAN_ID_KEY));
        }

        @Test
        void handleResult_mdcCleanedInFinally_onException() {
            Message message = Mockito.mock(Message.class);
            MessageProperties props = new MessageProperties();
            props.setHeader(TraceContext.TRACEPARENT_HEADER,
                    TraceContext.build("99999999999999999999999999999999", "8888888888888888"));
            Mockito.when(message.getMessageProperties()).thenReturn(props);

            // 让 orchestrator 抛异常
            when(orchestrator.updateSubtaskStatusOnly(anyString(), anyString(), anyString(),
                    anyString(), any())).thenThrow(new RuntimeException("boom"));

            CollaborationResultListener listener = new CollaborationResultListener(
                    orchestrator, eventBus, metricsService, mqProducer, redisService);
            CollabTaskResult result = CollabTaskResult.builder()
                    .packageId("pkg-err")
                    .subTaskId("sub-err")
                    .status("COMPLETED")
                    .result("ok")
                    .build();

            try {
                listener.handleResult(result, message);
            } catch (RuntimeException ignored) {
                // 预期会抛出
            }

            assertNull(MDC.get(TraceContext.TRACE_ID_KEY));
            assertNull(MDC.get(TraceContext.SPAN_ID_KEY));
        }

        @Test
        void handleResult_missingHeader_generatesTraceId() {
            Message message = Mockito.mock(Message.class);
            MessageProperties props = new MessageProperties(); // 缺 header
            Mockito.when(message.getMessageProperties()).thenReturn(props);

            String[] mdcAtCall = new String[1];
            doAnswer(inv -> {
                mdcAtCall[0] = MDC.get(TraceContext.TRACE_ID_KEY);
                return 1L;
            }).when(redisService).writeBranchResultAndIncrement(anyString(), anyString(), any());

            CollaborationResultListener listener = new CollaborationResultListener(
                    orchestrator, eventBus, metricsService, mqProducer, redisService);
            CollabTaskResult result = CollabTaskResult.builder()
                    .packageId("pkg-miss")
                    .subTaskId("sub-miss")
                    .status("COMPLETED")
                    .result("ok")
                    .build();

            listener.handleResult(result, message);

            assertNotNull(mdcAtCall[0]);
            assertEquals(32, mdcAtCall[0].length());
            // 缺 header 时 traceId 兜底生成
            assertNull(MDC.get(TraceContext.TRACE_ID_KEY));
        }

        @Test
        void handleDeadLetter_propagatesTraceparent() {
            String inboundTraceId = "77777777777777777777777777777777";
            String inboundSpanId = "6666666666666666";
            Message message = Mockito.mock(Message.class);
            MessageProperties props = new MessageProperties();
            props.setHeader(TraceContext.TRACEPARENT_HEADER,
                    TraceContext.build(inboundTraceId, inboundSpanId));
            Mockito.when(message.getMessageProperties()).thenReturn(props);

            String[] mdcAtCall = new String[2];
            doAnswer(inv -> {
                mdcAtCall[0] = MDC.get(TraceContext.TRACE_ID_KEY);
                mdcAtCall[1] = MDC.get(TraceContext.SPAN_ID_KEY);
                return 1L;
            }).when(redisService).writeBranchResultAndIncrement(anyString(), anyString(), any());

            CollaborationResultListener listener = new CollaborationResultListener(
                    orchestrator, eventBus, metricsService, mqProducer, redisService);
            CollabTaskMessage msg = CollabTaskMessage.builder()
                    .packageId("pkg-dlq")
                    .subTaskId("sub-dlq")
                    .attempt(3)
                    .build();

            listener.handleDeadLetter(msg, message);

            assertEquals(inboundTraceId, mdcAtCall[0]);
            assertEquals(inboundSpanId, mdcAtCall[1]);
            assertNull(MDC.get(TraceContext.TRACE_ID_KEY));
            assertNull(MDC.get(TraceContext.SPAN_ID_KEY));
        }
    }
}
