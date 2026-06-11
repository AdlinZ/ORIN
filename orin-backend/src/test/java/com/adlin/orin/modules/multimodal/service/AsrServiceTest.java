package com.adlin.orin.modules.multimodal.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AsrService.transcribe 单测。
 * <p>覆盖：参数校验、SiliconFlow 无 key 路径、whisper 路径（whisper CLI 不可用时返回错误）。
 * <p>SiliconFlow 真实 HTTP 路径与 Whisper CLI 真实成功路径不在本单测范围，由集成测试覆盖。
 */
class AsrServiceTest {

    private AsrService newService() {
        return new AsrService();
    }

    @Test
    void transcribe_nullAudioPath_returnsError() {
        AsrService svc = newService();
        String result = svc.transcribe(null, "openai/whisper-large-v3-turbo");
        assertThat(result).startsWith("[ASR Error]").contains("audioPath");
    }

    @Test
    void transcribe_blankAudioPath_returnsError() {
        AsrService svc = newService();
        String result = svc.transcribe("   ", "openai/whisper-large-v3-turbo");
        assertThat(result).startsWith("[ASR Error]").contains("audioPath");
    }

    @Test
    void transcribe_nullModel_returnsError() {
        AsrService svc = newService();
        String result = svc.transcribe("/tmp/audio.mp3", null);
        assertThat(result).startsWith("[ASR Error]").contains("model");
    }

    @Test
    void transcribe_siliconFlowPath_noApiKey_returnsError() {
        AsrService svc = newService();
        // 显式置空 apiKey，确保走 SiliconFlow 路径时立即失败
        ReflectionTestUtils.setField(svc, "siliconFlowApiKey", "");
        // model 不含 whisper → 走 SiliconFlow 路径
        String result = svc.transcribe("/tmp/audio.mp3", "FunAudioLLM/SenseVoiceSmall");
        assertThat(result)
                .startsWith("[ASR Error]")
                .contains("SiliconFlow API key not configured");
    }

    @Test
    void transcribe_whisperModel_cliNotAvailable_returnsError() {
        AsrService svc = newService();
        // 即使 SiliconFlow 有 key，whisper 模型也强制走本地 CLI
        ReflectionTestUtils.setField(svc, "siliconFlowApiKey", "sk-fake-but-set");
        String result = svc.transcribe("/tmp/audio.mp3", "whisper-large-v3");
        // 本机无 whisper CLI 时返回错误
        assertThat(result).startsWith("[ASR Error]");
    }

    @Test
    void transcribe_whisperModel_audioNotExists_returnsSpecificError() {
        AsrService svc = newService();
        String result = svc.transcribe("/nonexistent/audio.mp3", "whisper-large-v3");
        assertThat(result)
                .startsWith("[ASR Error]")
                .contains("Audio file not found");
    }
}
