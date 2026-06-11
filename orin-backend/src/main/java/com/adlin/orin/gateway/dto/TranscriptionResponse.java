package com.adlin.orin.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 转写响应（ASR）。
 * <p>ProviderAdapter.transcribe 的标准出参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionResponse {

    /**
     * 识别得到的文本
     */
    private String text;

    /**
     * Provider 标识（如 siliconflow / openai），便于审计和路由追溯
     */
    private String provider;

    /**
     * 实际使用的模型名（可能与请求传入不一致）
     */
    private String model;

    /**
     * 检测或显式指定到的语言
     */
    private String language;
}
