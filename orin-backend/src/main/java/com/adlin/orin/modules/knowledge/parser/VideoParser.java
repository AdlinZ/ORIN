package com.adlin.orin.modules.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 视频解析器
 * 使用 FFmpeg 提取音频，然后使用 ASR 进行语音识别
 */
@Slf4j
@Component
public class VideoParser implements DocumentParser {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "mp4", "avi", "mov", "mkv", "wmv", "flv", "webm", "m4v", "mpeg", "mpg"
    );

    private final AudioParser audioParser;

    public VideoParser(AudioParser audioParser) {
        this.audioParser = audioParser;
    }

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

            // 临时音频文件路径
            Path tempAudioPath = Path.of(inputPath + ".temp.mp3");

            try {
                // Step 1: 使用 FFmpeg 提取音频
                boolean extracted = extractAudio(input, tempAudioPath);
                if (!extracted) {
                    return ParsingResult.failure("Failed to extract audio from video",
                            System.currentTimeMillis() - startTime);
                }

                // Step 2: 使用 ASR 处理音频
                ParsingResult asrResult = audioParser.parse(
                        tempAudioPath.toString(),
                        outputPath,
                        config
                );

                long processingTime = System.currentTimeMillis() - startTime;

                // 添加视频元数据
                Map<String, Object> metadata = asrResult.getMetadata();
                if (metadata == null) {
                    metadata = new java.util.HashMap<>();
                }
                metadata.put("video_processed", true);

                if (asrResult.isSuccess()) {
                    log.info("Video parsed successfully: {} -> {}, chars: {}",
                            inputPath, outputPath, asrResult.getText().length());

                    return ParsingResult.builder()
                            .success(true)
                            .text(asrResult.getText())
                            .processingTimeMs(processingTime)
                            .metadata(metadata)
                            .build();
                } else {
                    return ParsingResult.builder()
                            .success(false)
                            .errorMessage(asrResult.getErrorMessage())
                            .processingTimeMs(processingTime)
                            .metadata(metadata)
                            .build();
                }

            } finally {
                // 清理临时音频文件
                Files.deleteIfExists(tempAudioPath);
            }

        } catch (Exception e) {
            log.error("Failed to parse video: {}", inputPath, e);
            return ParsingResult.failure("Failed to parse video: " + e.getMessage(),
                    System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 使用 FFmpeg 提取音频
     */
    private boolean extractAudio(Path videoPath, Path audioPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i", videoPath.toString(),
                    "-vn",                    // 禁用视频
                    "-acodec", "libmp3lame",  // 使用 MP3 编码
                    "-q:a", "2",              // 高质量
                    "-y",                     // 覆盖输出文件
                    audioPath.toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(300, TimeUnit.SECONDS); // 5分钟超时

            if (!finished) {
                process.destroyForcibly();
                log.warn("FFmpeg timed out for: {}", videoPath);
                return false;
            }

            return process.exitValue() == 0 && Files.exists(audioPath);

        } catch (Exception e) {
            log.error("Failed to extract audio from video: {}", videoPath, e);
            return false;
        }
    }

    @Override
    public boolean supports(String mediaType) {
        if (mediaType == null) return false;
        String type = mediaType.toLowerCase();
        return SUPPORTED_TYPES.contains(type) ||
               type.startsWith("video/");
    }
}
