package com.adlin.orin.modules.multimodal.service;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.TranscriptionRequest;
import com.adlin.orin.gateway.dto.TranscriptionResponse;
import com.adlin.orin.gateway.service.RouterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AsrService.transcribe 单测（slice 3 改写）。
 * <p>原硬码 RestTemplate + @Value siliconflowApiKey 路径已删除；
 * 新路径走 {@code RouterService.selectProviderByType("siliconflow-asr", ...) + provider.transcribe}。
 */
@ExtendWith(MockitoExtension.class)
class AsrServiceTest {

    @Mock
    private RouterService routerService;
    @Mock
    private ProviderAdapter transcriptionProvider;

    private AsrService newService() {
        return new AsrService(routerService, new ObjectMapper());
    }

    @Test
    void transcribe_nullAudioPath_returnsError() {
        AsrService svc = newService();
        String result = svc.transcribe(null, "FunAudioLLM/SenseVoiceSmall");
        assertThat(result).startsWith("[ASR Error]").contains("audioPath");
    }

    @Test
    void transcribe_blankAudioPath_returnsError() {
        AsrService svc = newService();
        String result = svc.transcribe("   ", "FunAudioLLM/SenseVoiceSmall");
        assertThat(result).startsWith("[ASR Error]").contains("audioPath");
    }

    @Test
    void transcribe_nullModel_returnsError() {
        AsrService svc = newService();
        String result = svc.transcribe("/tmp/audio.mp3", null);
        assertThat(result).startsWith("[ASR Error]").contains("model");
    }

    @Test
    void transcribe_whisperModel_audioNotExists_returnsSpecificError() {
        AsrService svc = newService();
        String result = svc.transcribe("/nonexistent/audio.mp3", "whisper-large-v3");
        assertThat(result)
                .startsWith("[ASR Error]")
                .contains("Audio file not found");
    }

    @Test
    void transcribe_whisperModel_cliNotAvailable_returnsError() throws IOException {
        // 准备一个临时音频文件，让 whisper CLI 路径走到 process.exec（环境无 whisper CLI 时 fail）
        Path tmp = Files.createTempFile("asr-test-", ".mp3");
        try {
            AsrService svc = newService();
            String result = svc.transcribe(tmp.toString(), "whisper-large-v3");
            // 本机无 whisper CLI 时返回错误；如本机恰好装了，行为可能不同，但路径与契约一致
            assertThat(result).startsWith("[ASR Error]");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void transcribe_nonWhisperModel_noProvider_returnsError() throws IOException {
        Path tmp = Files.createTempFile("asr-test-", ".mp3");
        try {
            when(routerService.selectProviderByType(eq("siliconflow-asr"), any(ChatCompletionRequest.class)))
                    .thenReturn(Optional.empty());

            AsrService svc = newService();
            String result = svc.transcribe(tmp.toString(), "FunAudioLLM/SenseVoiceSmall");

            assertThat(result)
                    .startsWith("[ASR Error]")
                    .contains("No healthy provider")
                    .contains("FunAudioLLM/SenseVoiceSmall");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void transcribe_nonWhisperModel_providerReturnsText_succeeds() throws IOException {
        Path tmp = Files.createTempFile("asr-test-", ".mp3");
        try {
            when(transcriptionProvider.getProviderName()).thenReturn("siliconflow-asr");
            when(transcriptionProvider.transcribe(any(TranscriptionRequest.class)))
                    .thenReturn(Mono.just(TranscriptionResponse.builder()
                            .text("hello world")
                            .provider("siliconflow-asr")
                            .model("FunAudioLLM/SenseVoiceSmall")
                            .build()));
            when(routerService.selectProviderByType(eq("siliconflow-asr"), any(ChatCompletionRequest.class)))
                    .thenReturn(Optional.of(transcriptionProvider));

            AsrService svc = newService();
            String result = svc.transcribe(tmp.toString(), "FunAudioLLM/SenseVoiceSmall");

            assertThat(result).isEqualTo("hello world");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void transcribe_nonWhisperModel_providerReturnsEmptyText_returnsEmptyString() throws IOException {
        Path tmp = Files.createTempFile("asr-test-", ".mp3");
        try {
            when(transcriptionProvider.transcribe(any(TranscriptionRequest.class)))
                    .thenReturn(Mono.just(TranscriptionResponse.builder()
                            .text("")
                            .provider("siliconflow-asr")
                            .model("FunAudioLLM/SenseVoiceSmall")
                            .build()));
            when(routerService.selectProviderByType(eq("siliconflow-asr"), any(ChatCompletionRequest.class)))
                    .thenReturn(Optional.of(transcriptionProvider));

            AsrService svc = newService();
            String result = svc.transcribe(tmp.toString(), "FunAudioLLM/SenseVoiceSmall");

            assertThat(result).isEmpty();
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void transcribe_nonWhisperModel_providerThrows_returnsError() throws IOException {
        Path tmp = Files.createTempFile("asr-test-", ".mp3");
        try {
            when(transcriptionProvider.getProviderName()).thenReturn("siliconflow-asr");
            when(transcriptionProvider.transcribe(any(TranscriptionRequest.class)))
                    .thenReturn(Mono.error(new RuntimeException("upstream boom")));
            when(routerService.selectProviderByType(eq("siliconflow-asr"), any(ChatCompletionRequest.class)))
                    .thenReturn(Optional.of(transcriptionProvider));

            AsrService svc = newService();
            String result = svc.transcribe(tmp.toString(), "FunAudioLLM/SenseVoiceSmall");

            assertThat(result)
                    .startsWith("[ASR Error]")
                    .contains("upstream boom");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void transcribe_nonWhisperModel_providerReturnsNull_returnsError() throws IOException {
        Path tmp = Files.createTempFile("asr-test-", ".mp3");
        try {
            when(transcriptionProvider.getProviderName()).thenReturn("siliconflow-asr");
            when(transcriptionProvider.transcribe(any(TranscriptionRequest.class)))
                    .thenReturn(Mono.empty());
            when(routerService.selectProviderByType(eq("siliconflow-asr"), any(ChatCompletionRequest.class)))
                    .thenReturn(Optional.of(transcriptionProvider));

            AsrService svc = newService();
            String result = svc.transcribe(tmp.toString(), "FunAudioLLM/SenseVoiceSmall");

            assertThat(result)
                    .startsWith("[ASR Error]")
                    .contains("Empty response");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
