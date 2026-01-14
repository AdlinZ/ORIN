package com.adlin.orin.gateway.adapter;

import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.gateway.dto.EmbeddingRequest;
import com.adlin.orin.gateway.dto.EmbeddingResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 通用AI Provider适配器接口
 * 所有AI服务提供商（Dify、OpenAI、本地模型等）都需要实现此接口
 */
public interface ProviderAdapter {

    /**
     * 获取Provider类型
     * 
     * @return Provider类型标识 (dify, openai, local, aliyun等)
     */
    String getProviderType();

    /**
     * 获取Provider名称
     * 
     * @return Provider显示名称
     */
    String getProviderName();

    /**
     * 健康检查
     * 
     * @return 是否健康
     */
    Mono<Boolean> healthCheck();

    /**
     * 聊天完成 (非流式)
     * 
     * @param request 聊天请求
     * @return 聊天响应
     */
    Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request);

    /**
     * 聊天完成 (流式)
     * 
     * @param request 聊天请求
     * @return 流式聊天响应
     */
    Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest request);

    /**
     * 文本嵌入
     * 
     * @param request 嵌入请求
     * @return 嵌入响应
     */
    Mono<EmbeddingResponse> embedding(EmbeddingRequest request);

    /**
     * 获取支持的模型列表
     * 
     * @return 模型列表
     */
    Mono<Map<String, Object>> getModels();

    /**
     * 获取Provider配置信息
     * 
     * @return 配置信息
     */
    Map<String, Object> getProviderConfig();

    /**
     * 估算请求成本
     * 
     * @param request 请求
     * @return 估算成本（美元）
     */
    double estimateCost(ChatCompletionRequest request);
}
