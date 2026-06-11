package com.adlin.orin.gateway.adapter.impl;

import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAIProviderAdapter 多模态 content parts 序列化回归测试。
 * <p>覆盖：纯文本（向后兼容）、多模态 parts、parts 优先于 content、空 parts 回退。
 * <p>仅验证 {@code buildOpenAIRequest} 序列化形状，不发真实 HTTP。
 */
class OpenAIProviderAdapterContentPartsTest {

    private final OpenAIProviderAdapter adapter = new OpenAIProviderAdapter(
            "test-openai", "test-key", "http://localhost:9999/v1", new RestTemplate());
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void textOnlyContent_shouldSerializeAsString() throws Exception {
        ChatCompletionRequest req = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatCompletionRequest.Message.builder()
                        .role("user")
                        .content("hello world")
                        .build()))
                .build();

        Map<String, Object> body = adapter.buildOpenAIRequest(req);
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0))
                .containsEntry("role", "user")
                .containsEntry("content", "hello world");
    }

    @Test
    void multimodalParts_shouldSerializeAsContentArray() throws Exception {
        ChatCompletionRequest.ContentPart imagePart = ChatCompletionRequest.ContentPart.builder()
                .type("image_url")
                .imageUrl(ChatCompletionRequest.ContentPart.ImageUrl.builder()
                        .url("data:image/png;base64,AAAA")
                        .detail("auto")
                        .build())
                .build();
        ChatCompletionRequest.ContentPart textPart = ChatCompletionRequest.ContentPart.builder()
                .type("text")
                .text("请识别图中的文字")
                .build();

        ChatCompletionRequest req = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatCompletionRequest.Message.builder()
                        .role("user")
                        .parts(List.of(imagePart, textPart))
                        .build()))
                .build();

        Map<String, Object> body = adapter.buildOpenAIRequest(req);
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");

        assertThat(messages).hasSize(1);
        // 用 Jackson 序列化整个 message，验证 image_url 是 snake_case（OpenAI 协议要求）
        String json = objectMapper.writeValueAsString(messages.get(0));
        assertThat(json)
                .contains("\"role\":\"user\"")
                .contains("\"type\":\"image_url\"")
                .contains("\"image_url\":{")
                .contains("\"url\":\"data:image/png;base64,AAAA\"")
                .contains("\"type\":\"text\"")
                .contains("\"text\":\"请识别图中的文字\"");
        // content 必须是 array 形态，不是字符串
        assertThat(messages.get(0).get("content")).isInstanceOf(List.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void partsTakesPrecedenceOverContent() {
        ChatCompletionRequest req = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatCompletionRequest.Message.builder()
                        .role("user")
                        .content("被忽略的旧字段")
                        .parts(List.of(ChatCompletionRequest.ContentPart.builder()
                                .type("text")
                                .text("实际生效的 parts")
                                .build()))
                        .build()))
                .build();

        Map<String, Object> body = adapter.buildOpenAIRequest(req);
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("content")).isInstanceOf(List.class);
        assertThat(messages.get(0))
                .doesNotContainEntry("content", "被忽略的旧字段");
    }

    @Test
    void emptyParts_shouldFallBackToContent() {
        ChatCompletionRequest req = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatCompletionRequest.Message.builder()
                        .role("user")
                        .content("纯文本")
                        .parts(List.of()) // 空列表
                        .build()))
                .build();

        Map<String, Object> body = adapter.buildOpenAIRequest(req);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).containsEntry("content", "纯文本");
    }
}
