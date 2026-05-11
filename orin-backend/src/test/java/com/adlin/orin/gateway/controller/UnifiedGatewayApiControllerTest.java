package com.adlin.orin.gateway.controller;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.gateway.service.ProviderRegistry;
import com.adlin.orin.gateway.service.RouterService;
import com.adlin.orin.modules.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedGatewayApiControllerTest {

    @Mock
    private ProviderRegistry providerRegistry;
    @Mock
    private RouterService routerService;
    @Mock
    private WorkflowService workflowService;
    @Mock
    private ProviderAdapter provider;

    private UnifiedGatewayApiController controller;

    @BeforeEach
    void setUp() {
        controller = new UnifiedGatewayApiController(providerRegistry, routerService, workflowService);
    }

    @Test
    void chatCompletions_ShouldReturnSseWhenStreamTrue() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-test")
                .stream(true)
                .messages(List.of())
                .build();
        ChatCompletionResponse chunk = ChatCompletionResponse.builder()
                .id("chunk-1")
                .model("gpt-test")
                .build();

        when(routerService.selectProviderByModel("gpt-test", request)).thenReturn(java.util.Optional.of(provider));
        when(provider.getProviderName()).thenReturn("test");
        when(provider.getProviderType()).thenReturn("openai");
        when(provider.chatCompletionStream(request)).thenReturn(Flux.just(chunk));

        ResponseEntity<Object> response = controller.chatCompletions(request, null, null, "trace-1").block();

        assertThat(response).isNotNull();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        assertThat(response.getBody()).isInstanceOf(Flux.class);
        @SuppressWarnings("unchecked")
        Flux<ServerSentEvent<ChatCompletionResponse>> body =
                (Flux<ServerSentEvent<ChatCompletionResponse>>) response.getBody();
        assertThat(body.blockFirst().data()).isEqualTo(chunk);
    }

    @Test
    void healthCheck_shouldReturnCachedProviderStateWithoutExternalProbe() {
        when(providerRegistry.getHealthSnapshot()).thenReturn(Map.of("local-ollama", true));
        when(providerRegistry.getStatistics()).thenReturn(Map.of(
                "totalProviders", 1,
                "healthyProviders", 1,
                "unhealthyProviders", 0));

        ResponseEntity<Map<String, Object>> response = controller.healthCheck().block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "ok");
        assertThat(response.getBody()).containsEntry("providers", Map.of("local-ollama", true));
        verify(providerRegistry, never()).checkAllHealth();
    }
}
