package com.adlin.orin.modules.multimodal.service;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.gateway.service.RouterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * OCR 服务（VLM 多模态路由）。
 * <p>通过 {@link RouterService} 按模型名选择 provider，由 provider 的 {@code chatCompletion}
 * 承载多模态 content（OpenAI 兼容 parts）。
 * <p>历史三家云厂商（aliCloud / tencent / baidu）stub 已删除；后续若需新增，
 * 应以 {@code ProviderAdapter} 子接口或新 provider 形式注入，不要在本类罗列厂商。
 */
@Slf4j
@Service
public class OcrService {

    private final RouterService routerService;

    public OcrService(RouterService routerService) {
        this.routerService = routerService;
    }

    /**
     * 用指定 VLM 模型识别图片中的文字。
     * <p>调用方只传 model，由 RouterService 决定具体 provider（默认命中 siliconflow 走 Qwen-VL 等）
     *
     * @param imageUrl 图片 http(s) URL 或 base64 data URI
     * @param model    VLM 模型名（如 {@code Qwen/Qwen2-VL-7B-Instruct}）
     * @return 识别文本；失败时返回形如 {@code [OCR Error] ...} 的字符串，与历史契约一致
     */
    public String recognize(String imageUrl, String model) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return "[OCR Error] imageUrl is required";
        }
        if (model == null || model.isBlank()) {
            return "[OCR Error] model is required";
        }

        ChatCompletionRequest request = buildOcrRequest(imageUrl, model);
        Optional<ProviderAdapter> providerOpt = routerService.selectProviderByModel(model, request);
        if (providerOpt.isEmpty()) {
            log.warn("No healthy provider available for OCR model={}", model);
            return "[OCR Error] No healthy provider available for model " + model;
        }

        ProviderAdapter provider = providerOpt.get();
        try {
            ChatCompletionResponse response = provider.chatCompletion(request).block();
            if (response == null
                    || response.getChoices() == null
                    || response.getChoices().isEmpty()) {
                log.warn("OCR provider {} returned empty response for model={}", provider.getProviderName(), model);
                return "[OCR Error] Empty response from provider " + provider.getProviderName();
            }
            String content = response.getChoices().get(0).getMessage().getContent();
            if (content == null) {
                return "[OCR Error] Null content from provider " + provider.getProviderName();
            }
            String trimmed = content.trim();
            if (trimmed.contains("[NO_TEXT_DETECTED]")) {
                return "";
            }
            log.info("OCR via {} succeeded, model={}, chars={}",
                    provider.getProviderName(), model, trimmed.length());
            return trimmed;
        } catch (Exception e) {
            log.error("OCR via {} failed for model={}: {}", provider.getProviderName(), model, e.getMessage(), e);
            return "[OCR Error] " + e.getMessage();
        }
    }

    /**
     * 构造 OCR 多模态请求（系统 prompt + 文本指令 + 图片 URL 部件）
     */
    private ChatCompletionRequest buildOcrRequest(String imageUrl, String model) {
        List<ChatCompletionRequest.ContentPart> parts = new ArrayList<>();
        parts.add(ChatCompletionRequest.ContentPart.builder()
                .type("text")
                .text("Please extract all text from this image:")
                .build());
        parts.add(ChatCompletionRequest.ContentPart.builder()
                .type("image_url")
                .imageUrl(ChatCompletionRequest.ContentPart.ImageUrl.builder()
                        .url(imageUrl)
                        .detail("auto")
                        .build())
                .build());

        return ChatCompletionRequest.builder()
                .model(model)
                .messages(List.of(
                        ChatCompletionRequest.Message.builder()
                                .role("system")
                                .content("You are an OCR service. Extract ALL text from the image. "
                                        + "Return ONLY the extracted text, nothing else. "
                                        + "If no text is found, return '[NO_TEXT_DETECTED]'.")
                                .build(),
                        ChatCompletionRequest.Message.builder()
                                .role("user")
                                .parts(parts)
                                .build()))
                .maxTokens(4096)
                .build();
    }
}
