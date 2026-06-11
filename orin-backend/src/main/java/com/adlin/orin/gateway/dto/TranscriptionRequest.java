package com.adlin.orin.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 转写请求（ASR）。
 * <p>对应 OpenAI /audio/transcriptions 风格的输入；ProviderAdapter.transcribe 的标准入参。
 * <p>audioUrl 既可填 http(s) URL，也可填 base64 data URI（data:audio/&lt;mime&gt;;base64,...），
 * 由具体 Provider 适配器自行决定走哪种端点。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionRequest {

    /**
     * 模型名称（如 openai/whisper-large-v3-turbo）。由调用方传入，Provider 通过 RouterService 选定
     */
    private String model;

    /**
     * 音频地址（http(s) URL 或 base64 data URI）
     */
    private String audioUrl;

    /**
     * 音频 MIME（可选，留给需要显式声明 mime 的 provider；不填时由 audioUrl 自动推断）
     */
    private String mimeType;

    /**
     * 期望语言（可选；如不填，由 provider 决定是否自动检测）
     */
    private String language;

    /**
     * 透传给 provider 的额外参数（如 response_format、temperature 等）
     */
    private Map<String, Object> providerParams;
}
