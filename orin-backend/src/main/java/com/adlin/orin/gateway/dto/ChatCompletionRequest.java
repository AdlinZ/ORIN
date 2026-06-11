package com.adlin.orin.gateway.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonAlias("top_p")
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
    @JsonAlias("max_tokens")
    private Integer maxTokens;

    /**
     * 存在惩罚
     */
    @Builder.Default
    @JsonAlias("presence_penalty")
    private Double presencePenalty = 0.0;

    /**
     * 频率惩罚
     */
    @Builder.Default
    @JsonAlias("frequency_penalty")
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
         * 思考内容（可选，针对推理模型如 DeepSeek-R1 / Ollama）
         */
        private String thinking;

        /**
         * 消息名称（可选）
         */
        private String name;

        /**
         * 多模态内容（OpenAI 兼容格式）。
         * <p>当非空时，调用方应改用多模态 parts；content 字段将被忽略。
         * <p>典型场景：VLM OCR / 视觉理解
         */
        private List<ContentPart> parts;

        /**
         * 向后兼容的位置构造器（role + content）。
         * <p>部分历史调用方（{@code KnowledgeWorkflowEngine} 等）用 2 参位置构造，
         * 加上 parts 字段后 Lombok @AllArgsConstructor 改为 5 参，故保留此 overload
         */
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    /**
     * 多模态内容片段（OpenAI 兼容格式）。
     * <p>支持 text / image_url 两类；audio_url 暂不内置，留给后续 transcribe 通道
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentPart {
        /**
         * 片段类型: text | image_url
         */
        private String type;

        /**
         * 文本内容（type=text 时使用）
         */
        private String text;

        /**
         * 图片 URL 引用（type=image_url 时使用）
         */
        @JsonProperty("image_url")
        private ImageUrl imageUrl;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ImageUrl {
            /**
             * 图片地址（http(s) URL 或 base64 data URI）
             */
            private String url;

            /**
             * 详情级别: low | high | auto（OpenAI 视觉模型参数）
             */
            private String detail;
        }
    }
}
