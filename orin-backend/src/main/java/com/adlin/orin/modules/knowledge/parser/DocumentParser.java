package com.adlin.orin.modules.knowledge.parser;

import java.util.Map;
import java.util.Set;

/**
 * 文档解析器接口
 * 支持多种媒体类型的解析
 */
public interface DocumentParser {

    /**
     * 获取支持的媒体类型
     */
    Set<String> supportedMediaTypes();

    /**
     * 解析文档
     * @param inputPath 输入文件路径
     * @param outputPath 输出文本路径
     * @param config 解析配置（如 OCR 模型、ASR 模型等）
     * @return 解析结果
     */
    ParsingResult parse(String inputPath, String outputPath, Map<String, String> config);

    /**
     * 是否支持该媒体类型
     */
    boolean supports(String mediaType);

    /**
     * 获取解析器名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
