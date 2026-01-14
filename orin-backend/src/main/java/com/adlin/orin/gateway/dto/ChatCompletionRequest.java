package com.adlin.orin.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 聊天完成请求 (OpenAI兼容格式)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletionRequest {

    /**
     * 模型名称
     */
    private String model;

    /**
     * 消息列表
     */
    private List<Message> messages;

    /**
     * 采样温度 (0-2)
     */
    @Builder.Default
    private Double temperature = 1.0;

    /**
     * 核采样参数
     */
    @Builder.Default
    private Double topP = 1.0;

    /**
     * 生成数量
     */
    @Builder.Default
    private Integer n = 1;

    /**
     * 是否流式返回
     */
    @Builder.Default
    private Boolean stream = false;

    /**
     * 停止词
     */
    private List<String> stop;

    /**
     * 最大Token数
     */
    private Integer maxTokens;

    /**
     * 存在惩罚
     */
    @Builder.Default
    private Double presencePenalty = 0.0;

    /**
     * 频率惩罚
     */
    @Builder.Default
    private Double frequencyPenalty = 0.0;

    /**
     * 用户标识
     */
    private String user;

    /**
     * 扩展参数 (用于特定Provider的特殊参数)
     */
    private Map<String, Object> providerParams;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        /**
         * 角色: system, user, assistant
         */
        private String role;

        /**
         * 消息内容
         */
        private String content;

        /**
         * 消息名称（可选）
         */
        private String name;
    }
}
