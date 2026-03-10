package com.adlin.orin.modules.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * 文本解析器
 * 支持 TXT, MD 等纯文本格式
 */
@Slf4j
@Component
public class TextParser implements DocumentParser {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "txt", "md", "text", "log", "csv", "json", "xml", "html", "htm"
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

            // 读取文本内容
            String content = Files.readString(input);

            // 写入解析结果
            Files.createDirectories(output.getParent());
            Files.writeString(output, content);

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Text parsed successfully: {} -> {}, size: {} chars",
                    inputPath, outputPath, content.length());

            return ParsingResult.builder()
                    .success(true)
                    .text(content)
                    .processingTimeMs(processingTime)
                    .metadata(Map.of(
                            "charCount", content.length(),
                            "lineCount", content.split("\n").length
                    ))
                    .build();

        } catch (IOException e) {
            log.error("Failed to parse text file: {}", inputPath, e);
            return ParsingResult.failure("Failed to parse text: " + e.getMessage(),
                    System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public boolean supports(String mediaType) {
        if (mediaType == null) return false;
        String type = mediaType.toLowerCase();
        // 支持 MIME 类型或文件扩展名
        return SUPPORTED_TYPES.contains(type) ||
               type.startsWith("text/") ||
               type.equals("application/json") ||
               type.equals("application/xml");
    }
}
