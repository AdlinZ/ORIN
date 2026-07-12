package com.adlin.orin.modules.conversation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.adlin.orin.modules.conversation.dto.ChatMessageRequest;
import com.adlin.orin.modules.conversation.service.AgentChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

class AgentChatControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void streamChatPropagatesAuthenticatedUserToWorkerThread() throws Exception {
        AgentChatService service = mock(AgentChatService.class);
        CountDownLatch invoked = new CountDownLatch(1);
        AtomicReference<Authentication> workerAuthentication = new AtomicReference<>();
        doAnswer(invocation -> {
            workerAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            invoked.countDown();
            return null;
        }).when(service).sendMessageStream(eq("session-1"), any(ChatMessageRequest.class), any());

        AgentChatController controller = new AgentChatController(
                service,
                new ObjectMapper(),
                new DelegatingSecurityContextAsyncTaskExecutor(new SimpleAsyncTaskExecutor()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", null));

        ChatMessageRequest request = new ChatMessageRequest();
        request.setMessage("你好");
        controller.sendMessageStream("session-1", request);

        assertTrue(invoked.await(2, TimeUnit.SECONDS));
        assertNotNull(workerAuthentication.get());
        assertEquals("42", workerAuthentication.get().getPrincipal());
    }
}
