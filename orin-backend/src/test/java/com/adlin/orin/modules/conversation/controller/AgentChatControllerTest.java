package com.adlin.orin.modules.conversation.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentChatControllerTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void runWithTraceIdPropagatesRequestTraceAndRestoresWorkerContext() {
        MDC.put("traceId", "worker-previous");
        AtomicReference<String> observedTraceId = new AtomicReference<>();

        AgentChatController.runWithTraceId(
                "request-trace",
                () -> observedTraceId.set(MDC.get("traceId")));

        assertEquals("request-trace", observedTraceId.get());
        assertEquals("worker-previous", MDC.get("traceId"));
    }
}
