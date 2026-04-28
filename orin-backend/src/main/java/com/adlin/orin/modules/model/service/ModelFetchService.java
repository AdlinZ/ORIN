package com.adlin.orin.modules.model.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.apikey.entity.ExternalProviderKey;
import com.adlin.orin.modules.apikey.repository.ExternalProviderKeyRepository;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.model.entity.ModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelFetchService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final GatewaySecretService gatewaySecretService;
    private final ExternalProviderKeyRepository providerKeyRepository;
    private final ModelConfigService modelConfigService;

    @Value("${siliconflow.api.key:}")
    private String configuredSiliconFlowApiKey;

    public List<Map<String, Object>> fetchModelsFromApi(String baseUrl, String apiKey) {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        if (normalizedBaseUrl.toLowerCase().contains("siliconflow")) {
            return fetchSiliconFlowModels(normalizedBaseUrl, apiKey);
        }

        if (normalizedBaseUrl.contains("11434") || normalizedBaseUrl.toLowerCase().contains("ollama")) {
            return fetchOllamaModels(normalizedBaseUrl);
        }

        try {
            String url = normalizedBaseUrl;
            if (!url.endsWith("/models") && !url.contains("/models?")) {
                if (url.endsWith("/")) {
                    url += "models";
                } else {
                    url += "/models";
                }
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object data = response.getBody().get("data");
                if (data instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> models = (List<Map<String, Object>>) data;
                    // Infer type for each model
                    models.forEach(model -> {
                        String id = (String) model.get("id");
                        if (id != null) {
                            String lowerId = id.toLowerCase();

                            if (lowerId.contains("embed")) {
                                model.put("type", "EMBEDDING");
                            } else if (lowerId.contains("rerank")) {
                                model.put("type", "RERANKER");
                            } else if (lowerId.contains("flux") || lowerId.contains("stable-diffusion")
                                    || lowerId.contains("sdxl") || lowerId.contains("kolors")) {
                                model.put("type", "TEXT_TO_IMAGE");
                            } else if (lowerId.contains("video") || lowerId.contains("cogvideo")
                                    || lowerId.contains("hunyuan") || lowerId.contains("ltx")
                                    || lowerId.contains("wan") || lowerId.contains("t2v")
                                    || lowerId.contains("i2v")) {
                                model.put("type", "TEXT_TO_VIDEO");
                            } else if (lowerId.contains("whisper") || lowerId.contains("funasr")
                                    || lowerId.contains("sensevoice")) {
                                model.put("type", "SPEECH_TO_TEXT");
                            } else if (lowerId.contains("tts") || lowerId.contains("fish-speech")
                                    || lowerId.contains("chattts") || lowerId.contains("speech")) {
                                model.put("type", "TEXT_TO_SPEECH");
                            } else {
                                model.put("type", "CHAT");
                            }
                        } else {
                            model.put("type", "CHAT");
                        }
                    });
                    return models;
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch models from {}: {}", baseUrl, e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * SiliconFlow 专属抓取逻辑：利用 Query Parameters 进行精确类型分类
     */
    private List<Map<String, Object>> fetchSiliconFlowModels(String baseUrl, String apiKey) {
        log.info("Starting precise SiliconFlow model fetch using API parameters");
        String modelsUrl = normalizeModelsUrl(baseUrl);
        List<String> apiKeyCandidates = resolveSiliconFlowApiKeyCandidates(apiKey);
        if (apiKeyCandidates.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "未找到可用的 SiliconFlow API Key，请填写 API Key 或先保存一个 SiliconFlow 密钥");
        }

        // 定义抓取任务： sub_type -> internal type
        // 参考官方参数：chat, embedding, reranker, text-to-image, image-to-image,
        // speech-to-text, text-to-video, image-to-video, text-to-speech, image-to-text
        Map<String, String> subTypeQueries = new java.util.LinkedHashMap<>();
        subTypeQueries.put("chat", "CHAT");
        subTypeQueries.put("embedding", "EMBEDDING");
        subTypeQueries.put("reranker", "RERANKER");
        subTypeQueries.put("text-to-image", "TEXT_TO_IMAGE");
        subTypeQueries.put("image-to-image", "TEXT_TO_IMAGE");
        subTypeQueries.put("speech-to-text", "SPEECH_TO_TEXT");
        subTypeQueries.put("text-to-video", "TEXT_TO_VIDEO");
        subTypeQueries.put("image-to-video", "TEXT_TO_VIDEO"); // 补上 I2V
        subTypeQueries.put("text-to-speech", "TEXT_TO_SPEECH"); // 补上 TTS
        subTypeQueries.put("image-to-text", "CHAT"); // 多模态理解，归类为 CHAT

        Exception lastError = null;
        for (String candidateKey : apiKeyCandidates) {
            java.util.Map<String, Map<String, Object>> masterList = new java.util.LinkedHashMap<>();
            try {
                for (Map.Entry<String, String> entry : subTypeQueries.entrySet()) {
                    String url = appendQuery(modelsUrl, "sub_type=" + entry.getKey(), "page=1", "page_size=100");
                    fetchAndMerge(url, candidateKey, entry.getValue(), masterList);
                }

                fetchAndMerge(appendQuery(modelsUrl, "type=audio", "page=1", "page_size=100"), candidateKey,
                        "TEXT_TO_SPEECH", masterList);
                fetchAndMerge(appendQuery(modelsUrl, "type=image", "page=1", "page_size=100"), candidateKey,
                        "TEXT_TO_IMAGE", masterList);
                fetchAndMerge(appendQuery(modelsUrl, "type=video", "page=1", "page_size=100"), candidateKey,
                        "TEXT_TO_VIDEO", masterList);
                fetchAndMerge(appendQuery(modelsUrl, "page=1", "page_size=100"), candidateKey, "CHAT", masterList);

                if (!masterList.isEmpty()) {
                    log.info("Fetched {} SiliconFlow models", masterList.size());
                    return new java.util.ArrayList<>(masterList.values());
                }
            } catch (HttpClientErrorException.Unauthorized e) {
                lastError = e;
                log.warn("SiliconFlow API key rejected, trying next configured key if available");
            } catch (HttpClientErrorException.Forbidden e) {
                lastError = e;
                log.warn("SiliconFlow API key forbidden, trying next configured key if available");
            } catch (Exception e) {
                lastError = e;
                log.warn("SiliconFlow model fetch failed with one credential: {}", e.getMessage());
            }
        }

        if (lastError instanceof HttpClientErrorException.Unauthorized
                || lastError instanceof HttpClientErrorException.Forbidden) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "SiliconFlow API Key 无效或无权限，请换一个保存的密钥，或清空当前密钥后使用系统密钥");
        }

        throw new BusinessException(ErrorCode.MODEL_API_ERROR,
                "未能从 SiliconFlow 获取模型列表，请检查网络、Endpoint 和 API Key");
    }

    private List<String> resolveSiliconFlowApiKeyCandidates(String apiKey) {
        Set<String> candidates = new LinkedHashSet<>();
        if (isConfigured(apiKey)) {
            candidates.add(apiKey.trim());
        }

        var unifiedCredential = gatewaySecretService.resolveProviderCredential("siliconflow");
        if (unifiedCredential.isPresent() && isConfigured(unifiedCredential.get().getApiKey())) {
            candidates.add(unifiedCredential.get().getApiKey().trim());
        }

        for (String provider : List.of("SiliconFlow", "siliconflow")) {
            List<ExternalProviderKey> keys = providerKeyRepository.findByProvider(provider);
            ExternalProviderKey key = keys.stream()
                    .filter(k -> Boolean.TRUE.equals(k.getEnabled()))
                    .findFirst()
                    .orElse(keys.isEmpty() ? null : keys.get(0));
            if (key != null && isConfigured(key.getApiKey())) {
                candidates.add(key.getApiKey().trim());
            }
        }

        try {
            ModelConfig config = modelConfigService.getConfig();
            if (config != null && isConfigured(config.getSiliconFlowApiKey())) {
                candidates.add(config.getSiliconFlowApiKey().trim());
            }
        } catch (Exception e) {
            log.warn("Could not load SiliconFlow API key from model config: {}", e.getMessage());
        }

        if (isConfigured(configuredSiliconFlowApiKey)) {
            candidates.add(configuredSiliconFlowApiKey.trim());
        }
        return new java.util.ArrayList<>(candidates);
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank() && !"sk-placeholder".equals(value) && !"sk-dummy".equals(value);
    }

    private String normalizeModelsUrl(String baseUrl) {
        String url = baseUrl == null || baseUrl.isBlank() ? "https://api.siliconflow.cn/v1" : baseUrl.trim();
        if (!url.endsWith("/models") && !url.contains("/models?")) {
            url = url.endsWith("/") ? url + "models" : url + "/models";
        }
        return url;
    }

    private String appendQuery(String url, String... params) {
        StringBuilder builder = new StringBuilder(url);
        String separator = url.contains("?") ? "&" : "?";
        for (String param : params) {
            builder.append(separator).append(param);
            separator = "&";
        }
        return builder.toString();
    }

    private void fetchAndMerge(String url, String apiKey, String type, Map<String, Map<String, Object>> masterList) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (apiKey != null && !apiKey.isEmpty() && !"sk-placeholder".equals(apiKey)) {
                headers.setBearerAuth(apiKey);
            }
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object data = response.getBody().get("data");
                if (data instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> models = (List<Map<String, Object>>) data;
                    for (Map<String, Object> model : models) {
                        String id = (String) model.get("id");
                        if (id != null && !masterList.containsKey(id)) {
                            model.put("type", type);
                            masterList.put(id, model);
                        }
                    }
                }
            }
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw e;
        } catch (Exception e) {
            log.warn("SiliconFlow fetch failed for url {}: {}", url, e.getMessage());
        }
    }

    /**
     * Ollama 专属抓取逻辑
     */
    private List<Map<String, Object>> fetchOllamaModels(String baseUrl) {
        String url = baseUrl;
        // Try to handle both /v1 and raw base URL
        if (url.endsWith("/v1") || url.endsWith("/v1/")) {
            url = url.replaceAll("/v1/?$", "") + "/api/tags";
        } else if (!url.contains("/api/tags")) {
            if (url.endsWith("/")) {
                url += "api/tags";
            } else {
                url += "/api/tags";
            }
        }

        try {
            log.info("Fetching Ollama models from {}", url);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object modelsObj = response.getBody().get("models");
                if (modelsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> ollamaModels = (List<Map<String, Object>>) modelsObj;
                    List<Map<String, Object>> result = new java.util.ArrayList<>();

                    for (Map<String, Object> om : ollamaModels) {
                        Map<String, Object> model = new java.util.HashMap<>();
                        String name = (String) om.get("name");
                        model.put("id", name);
                        model.put("name", name);

                        // Basic type inference for Ollama
                        if (name.contains("embed") || name.contains("bert") || name.contains("bge")) {
                            model.put("type", "EMBEDDING");
                        } else {
                            model.put("type", "CHAT");
                        }
                        result.add(model);
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch Ollama models: {}", e.getMessage());
        }

        // Fallback to OpenAI compatible /v1/models if we haven't tried it
        if (!baseUrl.contains("/v1/models")) {
            String openAiUrl = baseUrl;
            if (!openAiUrl.contains("/v1")) {
                openAiUrl = openAiUrl.endsWith("/") ? openAiUrl + "v1/models" : openAiUrl + "/v1/models";
            } else if (!openAiUrl.contains("/models")) {
                openAiUrl = openAiUrl.endsWith("/") ? openAiUrl + "models" : openAiUrl + "/models";
            }
            log.info("Ollama /api/tags failed, trying OpenAI compatible at {}", openAiUrl);
            return fetchModelsFromApi(openAiUrl, "");
        }

        return Collections.emptyList();
    }
}
