package com.adlin.orin.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 嵌入请求 (OpenAI兼容格式)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingRequest {

    /**
     * 模型名称
     */
    private String model;

    /**
     * 输入文本（单个或多个）
     */
    private Object input;

    /**
     * 用户标识
     */
    private String user;
}
