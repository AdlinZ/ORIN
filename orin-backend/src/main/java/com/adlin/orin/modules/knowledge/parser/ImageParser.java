package com.adlin.orin.modules.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * 图片解析器 (OCR)
 * 支持本地 Tesseract OCR 和云服务 OCR
 */
@Slf4j
@Component
public class ImageParser implements DocumentParser {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif"
    );

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

            // 获取 OCR 配置
            String provider = config.getOrDefault("ocr_provider", "local");
            String model = config.getOrDefault("ocr_model", "default");

            String text;
            Map<String, Object> metadata;

            switch (provider) {
                case "cloud" -> {
                    // 使用云服务 OCR
                    var result = parseWithCloud(input, config);
                    text = result.text;
                    metadata = result.metadata;
                }
                case "local" -> {
                    // 使用本地 Tesseract OCR
                    var result = parseWithTesseract(input, config);
                    text = result.text;
                    metadata = result.metadata;
                }
                default -> {
                    // 默认使用本地
                    var result = parseWithTesseract(input, config);
                    text = result.text;
                    metadata = result.metadata;
                }
            }

            if (text == null || text.trim().isEmpty()) {
                return ParsingResult.failure("No text extracted from image",
                        System.currentTimeMillis() - startTime);
            }

            // 写入解析结果
            Files.createDirectories(output.getParent());
            Files.writeString(output, text);

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("Image parsed with OCR: {} -> {}, chars: {}, provider: {}",
                    inputPath, outputPath, text.length(), provider);

            return ParsingResult.builder()
                    .success(true)
                    .text(text)
                    .processingTimeMs(processingTime)
                    .metadata(metadata)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse image: {}", inputPath, e);
            return ParsingResult.failure("Failed to parse image: " + e.getMessage(),
                    System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 使用 Tesseract 本地 OCR
     */
    private OcrResult parseWithTesseract(Path input, Map<String, String> config) {
        String language = config.getOrDefault("ocr_language", "chi_sim+eng");
        String tessdataPath = config.getOrDefault("tessdata_path", "/usr/local/share/tessdata");

        try {
            // 尝试使用 Tesseract
            ProcessBuilder pb = new ProcessBuilder(
                    "tesseract",
                    input.toString(),
                    "stdout",
                    "-l", language,
                    "--oem", "3",
                    "--psm", "6"
            );
            pb.directory(new java.io.File(tessdataPath).getParentFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String text = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode == 0 && !text.isEmpty()) {
                return new OcrResult(text.trim(), null);
            } else {
                // Tesseract 不可用，返回提示信息
                log.warn("Tesseract OCR not available, image will need manual processing");
                return new OcrResult(
                        "[OCR Required] Image file uploaded. OCR processing not available. " +
                        "Please configure Tesseract or use cloud OCR provider.",
                        Map.of("ocr_required", true)
                );
            }
        } catch (Exception e) {
            log.warn("Tesseract OCR failed: {}", e.getMessage());
            return new OcrResult(
                    "[OCR Unavailable] Could not process image. " +
                    "Error: " + e.getMessage(),
                    Map.of("ocr_error", e.getMessage())
            );
        }
    }

    /**
     * 使用云服务 OCR
     */
    private OcrResult parseWithCloud(Path input, Map<String, String> config) {
        String apiKey = config.get("ocr_api_key");
        String endpoint = config.getOrDefault("ocr_endpoint", "");

        if (apiKey == null || apiKey.isEmpty()) {
            return new OcrResult(
                    "[OCR Configuration Error] Cloud OCR API key not configured",
                    Map.of("error", "missing_api_key")
            );
        }

        // TODO: 实现云服务 OCR 调用
        // 可以接入阿里云、腾讯云、百度云等 OCR 服务
        log.info("Cloud OCR not implemented yet, using placeholder");

        return new OcrResult(
                "[Cloud OCR] Not implemented. Please configure local Tesseract or implement cloud OCR.",
                Map.of("provider", "cloud", "not_implemented", true)
        );
    }

    @Override
    public boolean supports(String mediaType) {
        if (mediaType == null) return false;
        String type = mediaType.toLowerCase();
        return SUPPORTED_TYPES.contains(type) ||
               type.startsWith("image/");
    }

    // 内部类用于返回结果
    private record OcrResult(String text, Map<String, Object> metadata) {}
}
