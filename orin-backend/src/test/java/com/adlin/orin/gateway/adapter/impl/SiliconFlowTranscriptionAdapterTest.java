package com.adlin.orin.gateway.adapter.impl;

import com.adlin.orin.gateway.dto.TranscriptionRequest;
import com.adlin.orin.gateway.dto.TranscriptionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SiliconFlowTranscriptionAdapter 单测。
 * <p>验证：
 * <ul>
 *   <li>{@code transcribe} POST 到 {@code {baseUrl}/audio/transcriptions}，body 含 model + audio_url</li>
 *   <li>Authorization 头带 Bearer apiKey</li>
 *   <li>成功响应（HTTP 200 + 包含 text 字段）→ 返回带 text 的 TranscriptionResponse</li>
 *   <li>响应缺 text 字段 → 返回空字符串</li>
 *   <li>非 2xx 响应 → 抛 RuntimeException</li>
 *   <li>空 apiKey / 空 audioUrl / 空 model → 抛 IllegalStateException / IllegalArgumentException</li>
 *   <li>chat/embedding 仍按设计抛 UnsupportedOperationException</li>
 * </ul>
 * <p>仅依赖 Reactor Core + AssertJ + Mockito，不引入 reactor-test。
 */
@ExtendWith(MockitoExtension.class)
class SiliconFlowTranscriptionAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    private SiliconFlowTranscriptionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SiliconFlowTranscriptionAdapter(
                "siliconflow-asr", "sk-test-key", "https://api.siliconflow.cn/v1", restTemplate);
    }

    @Test
    void providerType_isSiliconFlowAsr() {
        assertThat(adapter.getProviderType()).isEqualTo("siliconflow-asr");
        assertThat(adapter.getProviderName()).isEqualTo("SiliconFlow ASR - siliconflow-asr");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void transcribe_sendsCorrectEndpointAndBody() {
        when(restTemplate.postForEntity(eq("https://api.siliconflow.cn/v1/audio/transcriptions"),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"text\":\"hello world\"}"));

        TranscriptionResponse resp = adapter.transcribe(TranscriptionRequest.builder()
                .model("FunAudioLLM/SenseVoiceSmall")
                .audioUrl("data:audio/mpeg;base64,AAAA")
                .build()).block();

        assertThat(resp).isNotNull();
        assertThat(resp.getText()).isEqualTo("hello world");
        assertThat(resp.getProvider()).isEqualTo("siliconflow-asr");
        assertThat(resp.getModel()).isEqualTo("FunAudioLLM/SenseVoiceSmall");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor =
                ArgumentCaptor.forClass((Class) HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("https://api.siliconflow.cn/v1/audio/transcriptions"),
                captor.capture(),
                eq(String.class));
        HttpEntity<Map<String, Object>> sent = captor.getValue();
        assertThat(sent.getHeaders().getFirst("Authorization")).isEqualTo("Bearer sk-test-key");
        assertThat(sent.getHeaders().getContentType().toString()).contains("application/json");
        assertThat(sent.getBody()).containsEntry("model", "FunAudioLLM/SenseVoiceSmall");
        assertThat(sent.getBody()).containsEntry("audio_url", "data:audio/mpeg;base64,AAAA");
    }

    @Test
    void transcribe_passesLanguageWhenProvided() {
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"text\":\"hi\"}"));

        TranscriptionResponse resp = adapter.transcribe(TranscriptionRequest.builder()
                .model("FunAudioLLM/SenseVoiceSmall")
                .audioUrl("data:audio/mpeg;base64,AAAA")
                .language("zh")
                .build()).block();

        assertThat(resp).isNotNull();
        assertThat(resp.getLanguage()).isEqualTo("zh");
    }

    @Test
    void transcribe_responseMissingText_returnsEmptyText() {
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        TranscriptionResponse resp = adapter.transcribe(TranscriptionRequest.builder()
                .model("FunAudioLLM/SenseVoiceSmall")
                .audioUrl("data:audio/mpeg;base64,AAAA")
                .build()).block();

        assertThat(resp).isNotNull();
        assertThat(resp.getText()).isEmpty();
        assertThat(resp.getProvider()).isEqualTo("siliconflow-asr");
    }

    @Test
    void transcribe_nonOkResponse_throws() {
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));

        Throwable thrown = catchThrowable(() -> adapter.transcribe(TranscriptionRequest.builder()
                .model("FunAudioLLM/SenseVoiceSmall")
                .audioUrl("data:audio/mpeg;base64,AAAA")
                .build()).block());

        assertThat(thrown)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unexpected response");
    }

    @Test
    void transcribe_emptyApiKey_throws() {
        SiliconFlowTranscriptionAdapter noKey = new SiliconFlowTranscriptionAdapter(
                "siliconflow-asr", "", "https://api.siliconflow.cn/v1", restTemplate);

        Throwable thrown = catchThrowable(() -> noKey.transcribe(TranscriptionRequest.builder()
                .model("m").audioUrl("data:audio/mpeg;base64,AAAA").build()).block());

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API key not configured");
    }

    @Test
    void transcribe_blankAudioUrl_throws() {
        Throwable thrown = catchThrowable(() -> adapter.transcribe(TranscriptionRequest.builder()
                .model("m").audioUrl("").build()).block());

        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audioUrl");
    }

    @Test
    void transcribe_nullRequest_throws() {
        Throwable thrown = catchThrowable(() -> adapter.transcribe(null).block());

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transcribe_blankModel_throws() {
        Throwable thrown = catchThrowable(() -> adapter.transcribe(TranscriptionRequest.builder()
                .model("").audioUrl("data:audio/mpeg;base64,AAAA").build()).block());

        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
    }

    @Test
    void chatCompletion_unsupported() {
        Throwable thrown = catchThrowable(() -> adapter.chatCompletion(null).block());
        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void chatCompletionStream_unsupported() {
        Throwable thrown = catchThrowable(() -> adapter.chatCompletionStream(null).next().block());
        // Flux.error → block 会抛
        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void embedding_unsupported() {
        Throwable thrown = catchThrowable(() -> adapter.embedding(null).block());
        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void estimateCost_isZero() {
        assertThat(adapter.estimateCost(null)).isEqualTo(0.0);
    }

    @Test
    void getModels_returnsStub() {
        Map<String, Object> map = adapter.getModels().block();
        assertThat(map).isNotNull();
        assertThat(map).containsKey("data");
    }

    @Test
    void healthCheck_okWhenEndpointReachable() {
        when(restTemplate.exchange(eq("https://api.siliconflow.cn/v1/models"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", java.util.List.of())));

        Boolean ok = adapter.healthCheck().block();
        assertThat(ok).isTrue();
    }

    @Test
    void healthCheck_falseOnException() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("connection refused"));

        Boolean ok = adapter.healthCheck().block();
        assertThat(ok).isFalse();
    }
}
