package com.adlin.orin.gateway.adapter;

import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.gateway.dto.EmbeddingRequest;
import com.adlin.orin.gateway.dto.EmbeddingResponse;
import com.adlin.orin.gateway.dto.TranscriptionRequest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * ProviderAdapter.transcribe 默认实现回归测试。
 * <p>验证未覆盖 transcribe 的 provider 走 default 分支，返回 UnsupportedOperationException，
 * 而不是发无效 HTTP 请求或静默返回空字符串。
 * <p>仅依赖 Reactor Core + AssertJ，不引入 reactor-test。
 */
class ProviderAdapterTranscribeDefaultTest {

    /**
     * 最小化的 ProviderAdapter 桩，用于验证 default transcribe 行为
     */
    static class StubAdapter implements ProviderAdapter {
        @Override
        public String getProviderType() {
            return "stub";
        }

        @Override
        public String getProviderName() {
            return "Stub";
        }

        @Override
        public Mono<Boolean> healthCheck() {
            return Mono.just(true);
        }

        @Override
        public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request) {
            return Mono.empty();
        }

        @Override
        public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest request) {
            return Flux.empty();
        }

        @Override
        public Mono<EmbeddingResponse> embedding(EmbeddingRequest request) {
            return Mono.empty();
        }

        @Override
        public Mono<Map<String, Object>> getModels() {
            return Mono.empty();
        }

        @Override
        public Map<String, Object> getProviderConfig() {
            return Map.of();
        }

        @Override
        public double estimateCost(ChatCompletionRequest request) {
            return 0.0;
        }
    }

    @Test
    void transcribeDefault_shouldReturnUnsupportedOperationException() {
        ProviderAdapter stub = new StubAdapter();
        TranscriptionRequest req = TranscriptionRequest.builder()
                .model("any-model")
                .audioUrl("data:audio/mpeg;base64,AAAA")
                .build();

        Throwable thrown = catchThrowable(() -> stub.transcribe(req).block());

        assertThat(thrown)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("stub")
                .hasMessageContaining("does not support transcription");
    }
}
