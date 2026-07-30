package com.adlin.orin.modules.knowledge.parser;

import com.adlin.orin.modules.multimodal.service.AsrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 音频解析器 (ASR)
 * 支持本地 Whisper 和云服务 ASR
 */
@Slf4j
@Component
public class AudioParser implements DocumentParser {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "mp3", "wav", "m4a", "aac", "ogg", "flac", "wma", "aiff"
    );

    @Autowired
    private AsrService asrService;

    @Override
    public Set<String> supportedMediaTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public ParsingResult parse(String inputPath, String outputPath, Map<String, String> config) {
        long startTime = System.currentTimeMillis();

        try {
            Path input = Path.of(inputPath);
            Path output = Path.of(outputPath);

            if (!Files.exists(input)) {
                return ParsingResult.failure("Input file not found: " + inputPath);
            }

            // 获取 ASR 配置
            String provider = config.getOrDefault("asr_provider", "local");
            String model = config.getOrDefault("asr_model", "base");

            String text;
            Map<String, Object> metadata;

            switch (provider) {
                case "cloud" -> {
                    var result = parseWithCloud(input, config);
                    text = result.text();
                    metadata = result.metadata();
                }
                case "local" -> {
                    var result = parseWithWhisper(input, config);
                    text = result.text();
                    metadata = result.metadata();
                }
                default -> {
                    var result = parseWithWhisper(input, config);
                    text = result.text();
                    metadata = result.metadata();
                }
            }

            if (text == null || text.trim().isEmpty()) {
                return ParsingResult.failure("No text extracted from audio",
                        System.currentTimeMillis() - startTime);
            }

            // 写入解析结果
            Files.createDirectories(output.getParent());
            Files.writeString(output, text);

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("Audio parsed with ASR: {} -> {}, chars: {}, provider: {}",
                    inputPath, outputPath, text.length(), provider);

            return ParsingResult.builder()
                    .success(true)
                    .text(text)
                    .processingTimeMs(processingTime)
                    .metadata(metadata)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse audio: {}", inputPath, e);
            return ParsingResult.failure("Failed to parse audio: " + e.getMessage(),
                    System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 使用本地 Whisper 进行语音识别
     */
    private AsrResult parseWithWhisper(Path input, Map<String, String> config) {
        String model = config.getOrDefault("asr_model", "base");
        String whisperPath = config.getOrDefault("whisper_path", "/usr/local/bin/whisper");

        try {
            // 尝试使用 Whisper CLI
            ProcessBuilder pb = new ProcessBuilder(
                    whisperPath,
                    input.toString(),
                    "--model", model,
                    "--language", "auto",
                    "--output_format", "txt"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(300, TimeUnit.SECONDS); // 5分钟超时

            if (!finished) {
                process.destroyForcibly();
                return new AsrResult(
                        "[ASR Timeout] Audio processing took too long",
                        Map.of("timeout", true)
                );
            }

            // 读取输出文件
            Path outputFile = Path.of(input.toString().replaceAll("\\.[^.]+$", ".txt"));
            if (Files.exists(outputFile)) {
                String text = Files.readString(outputFile);
                return new AsrResult(text.trim(), Map.of("model", model));
            } else if (process.exitValue() == 0) {
                return new AsrResult("", Map.of("empty", true));
            } else {
                // Whisper 不可用
                log.warn("Whisper ASR not available: exit code {}", process.exitValue());
                return new AsrResult(
                        "[ASR Required] Audio file uploaded. ASR processing not available. " +
                        "Please configure Whisper or use cloud ASR provider.",
                        Map.of("asr_required", true)
                );
            }
        } catch (Exception e) {
            log.warn("Whisper ASR failed: {}", e.getMessage());
            return new AsrResult(
                    "[ASR Unavailable] Could not process audio. " +
                    "Error: " + e.getMessage(),
                    Map.of("asr_error", e.getMessage())
            );
        }
    }

    /**
     * 使用云服务 ASR（SiliconFlow ASR）
     */
    private AsrResult parseWithCloud(Path input, Map<String, String> config) {
        try {
            String model = config.getOrDefault("asr_model", "openai/whisper-large-v3-turbo");
            String extractedText = asrService.transcribeWithSiliconFlowAsr(input.toString(), model);

            if (extractedText.startsWith("[ASR Error]")) {
                log.warn("SiliconFlow ASR failed: {}", extractedText);
                return new AsrResult(
                        "[Cloud ASR Failed] " + extractedText,
                        Map.of("provider", "siliconflow_asr", "error", extractedText)
                );
            }

            if (extractedText.isEmpty()) {
                return new AsrResult(
                        "[No Speech Detected] The audio appears to contain no extractable speech.",
                        Map.of("provider", "siliconflow_asr", "no_speech", true)
                );
            }

            return new AsrResult(extractedText, Map.of("provider", "siliconflow_asr", "model", model));

        } catch (Exception e) {
            log.error("Cloud ASR failed: {}", e.getMessage(), e);
            return new AsrResult(
                    "[ASR Error] " + e.getMessage(),
                    Map.of("error", e.getMessage())
            );
        }
    }

    @Override
    public boolean supports(String mediaType) {
        if (mediaType == null) return false;
        String type = mediaType.toLowerCase();
        return SUPPORTED_TYPES.contains(type) ||
               type.startsWith("audio/");
    }

    // 内部类用于返回结果
    private record AsrResult(String text, Map<String, Object> metadata) {}
}
