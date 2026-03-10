package com.adlin.orin.modules.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 解析器工厂
 * 根据媒体类型和配置选择合适的解析器
 */
@Slf4j
@Component
public class ParserFactory {

    private final Map<String, DocumentParser> parsers;
    private final List<DocumentParser> parserList;

    public ParserFactory(List<DocumentParser> parserList) {
        this.parserList = parserList;
        this.parsers = parserList.stream()
                .collect(Collectors.toMap(
                        p -> p.getName().toLowerCase(),
                        Function.identity()
                ));
        log.info("Registered parsers: {}", parsers.keySet());
    }

    /**
     * 获取解析器
     * @param mediaType 媒体类型（文件扩展名或 MIME 类型）
     * @param config 知识库配置
     * @return 合适的解析器
     */
    public DocumentParser getParser(String mediaType, Map<String, String> config) {
        if (mediaType == null || mediaType.isEmpty()) {
            log.warn("Media type is null or empty");
            return null;
        }

        String type = mediaType.toLowerCase();

        // 根据文件扩展名选择解析器
        for (DocumentParser parser : parserList) {
            if (parser.supports(type)) {
                log.debug("Selected parser {} for media type: {}", parser.getName(), type);
                return parser;
            }
        }

        log.warn("No parser found for media type: {}", type);
        return null;
    }

    /**
     * 根据任务类型获取解析器
     */
    public DocumentParser getParserByTaskType(String taskType) {
        if (taskType == null) return null;

        return switch (taskType.toUpperCase()) {
            case "OCR" -> parsers.get("imageparser");
            case "ASR" -> parsers.get("audioparser");
            case "PDF_EXTRACT" -> parsers.get("pdfparser");
            case "TEXT_EXTRACT" -> parsers.get("textparser");
            default -> null;
        };
    }

    /**
     * 获取所有支持的媒体类型
     */
    public Map<String, String> getSupportedMediaTypes() {
        Map<String, String> supported = new HashMap<>();
        for (DocumentParser parser : parserList) {
            for (String type : parser.supportedMediaTypes()) {
                supported.put(type, parser.getName());
            }
        }
        return supported;
    }

    /**
     * 检查是否支持该媒体类型
     */
    public boolean isSupported(String mediaType) {
        if (mediaType == null) return false;
        return parserList.stream().anyMatch(p -> p.supports(mediaType));
    }

    /**
     * 获取媒体类型分类
     */
    public static String getMediaCategory(String mediaType) {
        if (mediaType == null) return "unknown";

        String type = mediaType.toLowerCase();

        // 文本类
        if (Set.of("txt", "md", "text", "log", "csv", "json", "xml", "html", "htm").contains(type)) {
            return "text";
        }

        // PDF
        if (Set.of("pdf").contains(type)) {
            return "pdf";
        }

        // 图片
        if (Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif").contains(type)) {
            return "image";
        }

        // 音频
        if (Set.of("mp3", "wav", "m4a", "aac", "ogg", "flac", "wma", "aiff").contains(type)) {
            return "audio";
        }

        // 视频
        if (Set.of("mp4", "avi", "mov", "mkv", "wmv", "flv", "webm", "m4v", "mpeg", "mpg").contains(type)) {
            return "video";
        }

        // 尝试从 MIME 类型判断
        if (type.startsWith("text/")) return "text";
        if (type.equals("application/pdf")) return "pdf";
        if (type.startsWith("image/")) return "image";
        if (type.startsWith("audio/")) return "audio";
        if (type.startsWith("video/")) return "video";

        return "unknown";
    }
}
