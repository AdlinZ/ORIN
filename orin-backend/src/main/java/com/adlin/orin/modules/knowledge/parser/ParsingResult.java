package com.adlin.orin.modules.knowledge.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 解析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsingResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 提取的文本内容
     */
    private String text;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 处理耗时（毫秒）
     */
    private long processingTimeMs;

    /**
     * 元数据
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 成功结果构建器
     */
    public static ParsingResult success(String text) {
        return ParsingResult.builder()
                .success(true)
                .text(text)
                .build();
    }

    /**
     * 成功结果构建器（带元数据）
     */
    public static ParsingResult success(String text, Map<String, Object> metadata) {
        return ParsingResult.builder()
                .success(true)
                .text(text)
                .metadata(metadata)
                .build();
    }

    /**
     * 失败结果构建器
     */
    public static ParsingResult failure(String errorMessage) {
        return ParsingResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 失败结果构建器（带处理时长）
     */
    public static ParsingResult failure(String errorMessage, long processingTimeMs) {
        return ParsingResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .processingTimeMs(processingTimeMs)
                .build();
    }
}
