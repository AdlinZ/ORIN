package com.adlin.orin.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 嵌入响应 (OpenAI兼容格式)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResponse {

    /**
     * 对象类型
     */
    @Builder.Default
    private String object = "list";

    /**
     * 嵌入数据列表
     */
    private List<EmbeddingData> data;

    /**
     * 模型名称
     */
    private String model;

    /**
     * Token使用统计
     */
    private Usage usage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingData {
        /**
         * 对象类型
         */
        @Builder.Default
        private String object = "embedding";

        /**
         * 索引
         */
        private Integer index;

        /**
         * 嵌入向量
         */
        private List<Double> embedding;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        /**
         * 提示词Token数
         */
        private Integer promptTokens;

        /**
         * 总Token数
         */
        private Integer totalTokens;
    }
}
