package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.model.entity.ModelConfig;
import com.adlin.orin.modules.model.service.ModelConfigService;
import com.adlin.orin.modules.model.service.SiliconFlowIntegrationService;
import com.adlin.orin.modules.multimodal.service.VisualAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * E2.2 验证 OCR、ASR 在"已配置"和"未配置"两种环境下的降级行为
 *
 * 测试目标：
 * - OCR 图片解析：已配置(API key + VLM model) → 成功；未配置 → 返回错误信息不崩溃
 * - ASR 音频转写：已配置(API key) → 成功；未配置 → 返回错误信息不崩溃
 *
 * 运行方式：mvn test -Dtest=OcrAsrDegradationTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OcrAsrDegradationTest {

    @Mock
    private VisualAnalysisService visualAnalysisService;

    @Mock
    private SiliconFlowIntegrationService siliconFlowService;

    @Mock
    private ModelConfigService modelConfigService;

    @TempDir
    Path tempDir;

    private MultimodalContentParserService parserService;

    // 测试用配置（已配置状态）
    private ModelConfig configuredModelConfig;
    // 测试用配置（未配置状态 - API key 为空）
    private ModelConfig unconfiguredModelConfig;

    @BeforeEach
    void setUp() throws Exception {
        // 构建 MultimodalContentParserService
        parserService = new MultimodalContentParserService(
                visualAnalysisService,
                siliconFlowService,
                modelConfigService
        );

        // 初始化已配置 ModelConfig
        configuredModelConfig = new ModelConfig();
        configuredModelConfig.setSiliconFlowApiKey("sk-test-key-12345");
        configuredModelConfig.setSiliconFlowEndpoint("https://api.siliconflow.cn/v1");
        configuredModelConfig.setVlmModel("Qwen/Qwen2-VL-7B-Instruct");

        // 初始化未配置 ModelConfig（API key 为空）
        unconfiguredModelConfig = new ModelConfig();
        unconfiguredModelConfig.setSiliconFlowApiKey("");
        unconfiguredModelConfig.setSiliconFlowEndpoint("https://api.siliconflow.cn/v1");
        unconfiguredModelConfig.setVlmModel("");
    }

    // ==================== OCR / 图片解析降级测试 ====================

    @Test
    @DisplayName("E2.2 - OCR 已配置: VLM 模型和 API Key 都配置时，正常调用视觉分析服务")
    void testOcr_Configured_BothModelAndApiKey_DelegatesToVisualAnalysis() throws Exception {
        // Given: 已配置 VLM 模型和 API Key
        when(modelConfigService.getConfig()).thenReturn(configuredModelConfig);
        when(visualAnalysisService.analyzeImage(anyString(), eq("Qwen/Qwen2-VL-7B-Instruct"), any()))
                .thenReturn("图片中显示了一台电脑和键盘");

        // 创建一个临时图片文件
        Path imagePath = tempDir.resolve("test.png");
        java.nio.file.Files.write(imagePath, "fake-png-data".getBytes());

        // When: 解析图片
        String result = parserService.parseToText(imagePath.toString(), "png", null);

        // Then: 成功返回 OCR 结果
        assertEquals("图片中显示了一台电脑和键盘", result);
        verify(visualAnalysisService, times(1)).analyzeImage(anyString(), eq("Qwen/Qwen2-VL-7B-Instruct"), any());
    }

    @Test
    @DisplayName("E2.2 - OCR 未配置: VLM 模型未配置时返回明确的错误信息")
    void testOcr_Unconfigured_VlmModelNotSet_ReturnsErrorMessage() throws Exception {
        // Given: API Key 有值但 VLM 模型为空
        ModelConfig partialConfig = new ModelConfig();
        partialConfig.setSiliconFlowApiKey("sk-test-key-12345");
        partialConfig.setVlmModel("");  // VLM 模型未配置
        when(modelConfigService.getConfig()).thenReturn(partialConfig);

        // 创建一个临时图片文件
        Path imagePath = tempDir.resolve("test.png");
        java.nio.file.Files.write(imagePath, "fake-png-data".getBytes());

        // When: 解析图片
        // parseImage 会在内部检查 vlmModel，如果为空会抛出异常
        // parseToText 会捕获异常并转为错误字符串返回
        String result = parserService.parseToText(imagePath.toString(), "png", null);

        // Then: 返回包含 VLM 模型未配置提示的错误信息
        assertTrue(result.contains("VLM 模型未配置") || result.contains("未配置"),
                "期望返回包含'VLM 模型未配置'或'未配置'的错误信息，实际返回: " + result);
        assertTrue(result.startsWith("["), "错误信息应以'['开头，实际: " + result);
    }

    @Test
    @DisplayName("E2.2 - OCR 未配置: 图片文件不存在时返回错误信息字符串，不抛出异常")
    void testOcr_FileNotFound_ReturnsErrorMessage_NotException() throws Exception {
        // Given: 已配置 VLM 模型和 API Key
        when(modelConfigService.getConfig()).thenReturn(configuredModelConfig);

        // 不存在的文件路径
        Path nonExistentPath = tempDir.resolve("non_existent_file.png");

        // When: 解析不存在的文件
        // parseImage 会抛出 Exception，parseToText 会捕获并转为字符串
        String result = parserService.parseToText(nonExistentPath.toString(), "png", null);

        // Then: 返回错误信息字符串（异常被捕获转换）
        assertTrue(result.contains("不存在") || result.contains("图片解析失败"),
                "期望返回包含'不存在'或'图片解析失败'的错误信息，实际返回: " + result);
        assertTrue(result.startsWith("["), "错误信息应以'['开头，实际: " + result);
    }

    @Test
    @DisplayName("E2.2 - OCR 未配置: 完整未配置时（API key + VLM model 都空）返回错误信息")
    void testOcr_CompletelyUnconfigured_ReturnsErrorMessage() throws Exception {
        // Given: 完全未配置的 ModelConfig（API key 和 VLM model 都为空）
        ModelConfig emptyConfig = new ModelConfig();
        emptyConfig.setSiliconFlowApiKey("");
        emptyConfig.setVlmModel("");
        when(modelConfigService.getConfig()).thenReturn(emptyConfig);

        Path imagePath = tempDir.resolve("test.jpg");
        java.nio.file.Files.write(imagePath, "fake-jpg".getBytes());

        // When: 解析图片
        String result = parserService.parseToText(imagePath.toString(), "jpg", null);

        // Then: 返回包含 VLM 未配置错误的字符串
        assertTrue(result.startsWith("["), "错误信息应以'['开头，实际: " + result);
        assertTrue(result.contains("未配置") || result.contains("VLM"),
                "期望返回包含'未配置'或'VLM'的错误信息，实际: " + result);
    }

    // ==================== ASR / 音频转写降级测试 ====================

    @Test
    @DisplayName("E2.2 - ASR 已配置: API Key 配置时，调用 SiliconFlow ASR 服务")
    void testAsr_Configured_ApiKeySet_CallsSiliconFlowAsr() throws Exception {
        // Given: 已配置 SiliconFlow API Key
        when(modelConfigService.getConfig()).thenReturn(configuredModelConfig);
        when(siliconFlowService.transcribeAudio(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Optional.of("这是音频转写的文字内容"));

        // 创建一个临时音频文件
        Path audioPath = tempDir.resolve("test.mp3");
        byte[] fakeAudio = "fake-audio-data".getBytes();
        java.nio.file.Files.write(audioPath, fakeAudio);

        // When: 解析音频
        String result = parserService.parseToText(audioPath.toString(), "mp3", null);

        // Then: 成功返回转写结果
        assertEquals("这是音频转写的文字内容", result);
        verify(siliconFlowService, times(1)).transcribeAudio(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("E2.2 - ASR 未配置: API Key 为空时返回明确的错误信息，不崩溃")
    void testAsr_Unconfigured_ApiKeyEmpty_ReturnsErrorMessage() throws Exception {
        // Given: API Key 为空
        when(modelConfigService.getConfig()).thenReturn(unconfiguredModelConfig);

        // 创建一个临时音频文件
        Path audioPath = tempDir.resolve("test.mp3");
        byte[] fakeAudio = "fake-audio-data".getBytes();
        java.nio.file.Files.write(audioPath, fakeAudio);

        // When: 解析音频
        String result = parserService.parseToText(audioPath.toString(), "mp3", null);

        // Then: 返回明确的错误信息，不是崩溃
        assertTrue(result.contains("音频转写失败") || result.contains("API Key") || result.contains("未配置"),
                "期望返回包含'音频转写失败'或'API Key'或'未配置'的错误信息，实际返回: " + result);
        assertFalse(result.isEmpty(), "错误信息不应为空");
    }

    @Test
    @DisplayName("E2.2 - ASR 未配置: 音频文件不存在时返回错误信息")
    void testAsr_FileNotFound_ReturnsErrorMessage() throws Exception {
        // Given: 已配置 API Key 但文件不存在
        when(modelConfigService.getConfig()).thenReturn(configuredModelConfig);

        // 不存在的文件路径
        Path nonExistentPath = tempDir.resolve("non_existent_audio.mp3");

        // When: 解析不存在的音频
        String result = parserService.parseToText(nonExistentPath.toString(), "mp3", null);

        // Then: 返回文件不存在的错误信息
        assertTrue(result.contains("不存在") || result.contains("not found"),
                "期望返回文件不存在的错误信息，实际返回: " + result);
    }

    // ==================== 视频解析降级测试 ====================

    @Test
    @DisplayName("E2.2 - 视频解析: 无 FFmpeg 时返回提示信息")
    void testVideo_NoFFmpeg_ReturnsHintMessage() throws Exception {
        // Given: 已配置 API Key（视频解析不直接依赖这个配置，但为了一致性）
        when(modelConfigService.getConfig()).thenReturn(configuredModelConfig);

        // 创建一个临时视频文件
        Path videoPath = tempDir.resolve("test.mp4");
        java.nio.file.Files.write(videoPath, "fake-video-data".getBytes());

        // When: 解析视频（无 FFmpeg）
        String result = parserService.parseToText(videoPath.toString(), "mp4", null);

        // Then: 返回 FFmpeg 不可用的提示信息
        assertTrue(result.contains("FFmpeg") || result.contains("音频"),
                "期望返回 FFmpeg 相关提示，实际返回: " + result);
    }

    // ==================== 降级行为核心验证 ====================

    @Test
    @DisplayName("E2.2 - 核心验证: 图片解析在未配置时不会崩溃，返回错误标记字符串")
    void testCoreBehavior_ImageUnconfigured_DoesNotCrash_ReturnsErrorMarker() throws Exception {
        // Given: 完全未配置的 ModelConfig（API key 和 VLM model 都为空）
        ModelConfig emptyConfig = new ModelConfig();
        emptyConfig.setSiliconFlowApiKey("");
        emptyConfig.setVlmModel("");
        when(modelConfigService.getConfig()).thenReturn(emptyConfig);

        Path imagePath = tempDir.resolve("test.jpg");
        java.nio.file.Files.write(imagePath, "fake-jpg".getBytes());

        // When: 解析图片（不应该崩溃）
        String result = parserService.parseToText(imagePath.toString(), "jpg", null);

        // Then: 返回错误标记字符串（[图片解析失败: VLM 模型未配置...]）
        assertNotNull(result);
        assertTrue(result.startsWith("["),
                "期望返回以'['开头的错误标记，实际: " + result);
        System.out.println("=== E2.2 图片未配置降级验证通过 ===");
        System.out.println("返回信息: " + result);
    }

    @Test
    @DisplayName("E2.2 - 核心验证: 音频解析在未配置时返回错误信息字符串，不是异常")
    void testCoreBehavior_AudioUnconfigured_ReturnsErrorMessage_NotException() throws Exception {
        // Given: 未配置 API Key
        when(modelConfigService.getConfig()).thenReturn(unconfiguredModelConfig);

        Path audioPath = tempDir.resolve("test.wav");
        java.nio.file.Files.write(audioPath, "fake-wav".getBytes());

        // When: 解析音频（不应该崩溃）
        String result = parserService.parseToText(audioPath.toString(), "wav", null);

        // Then: 返回错误信息字符串（不是抛异常）
        assertNotNull(result);
        assertTrue(result.startsWith("[") || result.contains("失败") || result.contains("未配置"),
                "期望返回以'['开头或包含'失败'/'未配置'的错误信息，实际: " + result);
        System.out.println("=== E2.2 音频未配置降级验证通过 ===");
        System.out.println("返回信息: " + result);
    }

    // ==================== 配置感知验证 ====================

    @Test
    @DisplayName("E2.2 - 配置感知: 知识库级别配置优先于全局配置")
    void testConfigPriority_KbLevelConfigOverridesGlobal() throws Exception {
        // Given: 全局配置无 VLM 模型，但知识库配置有
        ModelConfig globalConfig = new ModelConfig();
        globalConfig.setSiliconFlowApiKey("sk-global");
        globalConfig.setVlmModel("");  // 全局无 VLM

        when(modelConfigService.getConfig()).thenReturn(globalConfig);
        when(visualAnalysisService.analyzeImage(anyString(), eq("Qwen/Qwen2-VL-72B"), any()))
                .thenReturn("OCR result with KB-level model");

        // KB 级别配置指定了 ocr_model
        java.util.Map<String, String> kbConfig = new java.util.HashMap<>();
        kbConfig.put("ocr_model", "Qwen/Qwen2-VL-72B");

        Path imagePath = tempDir.resolve("test.png");
        java.nio.file.Files.write(imagePath, "fake".getBytes());

        // When: 使用 KB 级别配置解析图片
        String result = parserService.parseToText(imagePath.toString(), "png", kbConfig);

        // Then: 使用了 KB 级别指定的模型
        assertEquals("OCR result with KB-level model", result);
        verify(visualAnalysisService, times(1)).analyzeImage(anyString(), eq("Qwen/Qwen2-VL-72B"), any());
    }

    @Test
    @DisplayName("E2.2 - 配置感知: 知识库级别 ASR 模型优先于默认值")
    void testConfigPriority_KbLevelAsrModelOverridesDefault() throws Exception {
        // Given: 全局配置有 API Key
        when(modelConfigService.getConfig()).thenReturn(configuredModelConfig);
        when(siliconFlowService.transcribeAudio(anyString(), anyString(), eq("paraformer-zh"), any(), anyString()))
                .thenReturn(Optional.of("transcribed with custom model"));

        // KB 级别配置指定了 asr_model
        java.util.Map<String, String> kbConfig = new java.util.HashMap<>();
        kbConfig.put("asr_model", "paraformer-zh");

        Path audioPath = tempDir.resolve("test.m4a");
        java.nio.file.Files.write(audioPath, "fake".getBytes());

        // When: 使用 KB 级别配置解析音频
        String result = parserService.parseToText(audioPath.toString(), "m4a", kbConfig);

        // Then: 使用了 KB 级别指定的 ASR 模型
        assertEquals("transcribed with custom model", result);
    }

    // ==================== 降级路径总结 ====================

    @Test
    @DisplayName("E2.2 - 降级行为总结: 验证所有降级路径返回错误字符串而非崩溃")
    void testDegradationSummary_AllPathsReturnErrorString() throws Exception {
        // Given: 未配置环境
        when(modelConfigService.getConfig()).thenReturn(unconfiguredModelConfig);

        // 创建测试文件
        Path imagePath = tempDir.resolve("test.jpg");
        Path audioPath = tempDir.resolve("test.mp3");
        Path videoPath = tempDir.resolve("test.mp4");
        Path nonExistentPath = tempDir.resolve("non_existent.png");
        java.nio.file.Files.write(imagePath, "fake".getBytes());
        java.nio.file.Files.write(audioPath, "fake".getBytes());
        java.nio.file.Files.write(videoPath, "fake".getBytes());

        // When & Then: 各种未配置场景都应返回错误字符串
        String imageResult = parserService.parseToText(imagePath.toString(), "jpg", null);
        assertTrue(imageResult.startsWith("["), "图片未配置应返回错误字符串，实际: " + imageResult);

        String audioResult = parserService.parseToText(audioPath.toString(), "mp3", null);
        assertTrue(audioResult.startsWith("[") || audioResult.contains("失败"),
                "音频未配置应返回错误字符串，实际: " + audioResult);

        String videoResult = parserService.parseToText(videoPath.toString(), "mp4", null);
        assertTrue(videoResult.startsWith("[") || videoResult.contains("FFmpeg"),
                "视频无FFmpeg应返回错误字符串，实际: " + videoResult);

        String missingResult = parserService.parseToText(nonExistentPath.toString(), "png", null);
        assertTrue(missingResult.startsWith("["), "文件不存在应返回错误字符串，实际: " + missingResult);

        System.out.println("=== E2.2 降级行为总结验证通过 ===");
        System.out.println("所有路径均返回错误字符串而非崩溃");
    }
}
