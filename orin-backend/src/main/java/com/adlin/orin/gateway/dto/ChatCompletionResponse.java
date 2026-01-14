package com.adlin.orin.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 聊天完成响应 (OpenAI兼容格式)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletionResponse {

    /**
     * 响应ID
     */
    private String id;

    /**
     * 对象类型
     */
    @Builder.Default
    private String object = "chat.completion";

    /**
     * 创建时间戳
     */
    private Long created;

    /**
     * 使用的模型
     */
    private String model;

    /**
     * 选项列表
     */
    private List<Choice> choices;

    /**
     * Token使用统计
     */
    private Usage usage;

    /**
     * Provider来源
     */
    private String provider;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        /**
         * 索引
         */
        private Integer index;

        /**
         * 消息
         */
        private ChatCompletionRequest.Message message;

        /**
         * 完成原因: stop, length, content_filter
         */
        private String finishReason;

        /**
         * 流式响应增量内容
         */
        private Delta delta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Delta {
        /**
         * 角色
         */
        private String role;

        /**
         * 内容
         */
        private String content;
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
         * 完成Token数
         */
        private Integer completionTokens;

        /**
         * 总Token数
         */
        private Integer totalTokens;
    }
}
