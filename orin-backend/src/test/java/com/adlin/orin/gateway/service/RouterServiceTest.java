package com.adlin.orin.gateway.service;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.config.GatewayModelProperties;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouterServiceTest {

    @Mock
    private ProviderRegistry providerRegistry;
    @Mock
    private GatewayModelProperties modelProperties;
    @Mock
    private ProviderAdapter openAiProvider;
    @Mock
    private ProviderAdapter ollamaProvider;

    private RouterService routerService;

    @BeforeEach
    void setUp() {
        routerService = new RouterService(providerRegistry, modelProperties);
    }

    @Test
    void selectProviderByModel_shouldPreferOpenAiForGptModels() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .build();
        when(providerRegistry.getHealthyProvidersByType("openai")).thenReturn(List.of(openAiProvider));
        when(openAiProvider.estimateCost(request)).thenReturn(0.01d);

        Optional<ProviderAdapter> selected = routerService.selectProviderByModel("gpt-4o-mini", request);

        assertThat(selected).contains(openAiProvider);
    }

    @Test
    void selectProviderByModel_shouldPreferOllamaForLocalModels() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("qwen2.5:7b")
                .build();
        when(providerRegistry.getHealthyProvidersByType("ollama")).thenReturn(List.of(ollamaProvider));
        when(ollamaProvider.estimateCost(request)).thenReturn(0.0d);

        Optional<ProviderAdapter> selected = routerService.selectProviderByModel("qwen2.5:7b", request);

        assertThat(selected).contains(ollamaProvider);
    }

    @Test
    void selectProvider_roundRobinShouldClampIndexWhenProviderListShrinks() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .build();
        when(providerRegistry.getHealthyProviders())
                .thenReturn(List.of(openAiProvider, ollamaProvider))
                .thenReturn(List.of(openAiProvider));

        routerService.selectProvider(request, RouterService.RoutingStrategy.ROUND_ROBIN);
        Optional<ProviderAdapter> selected = routerService.selectProvider(request, RouterService.RoutingStrategy.ROUND_ROBIN);

        assertThat(selected).contains(openAiProvider);
    }

    @Test
    void selectProviderByType_shouldOnlyUseHealthyProviders() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .build();
        when(providerRegistry.getHealthyProvidersByType("openai")).thenReturn(List.of());

        Optional<ProviderAdapter> selected = routerService.selectProviderByType("openai", request);

        assertThat(selected).isEmpty();
    }
}
