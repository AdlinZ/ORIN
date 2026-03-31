package com.adlin.orin.modules.knowledge.component;

import com.adlin.orin.modules.apikey.entity.ExternalProviderKey;
import com.adlin.orin.modules.apikey.repository.ExternalProviderKeyRepository;
import com.adlin.orin.modules.model.entity.ModelConfig;
import com.adlin.orin.modules.model.service.ModelConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SiliconFlow Embedding 服务
 * API Key 优先从数据库 external_provider_keys 读取 (provider='SiliconFlow')，
 * 若数据库无配置则 fallback 到 application.properties 中的 ${SILICONFLOW_API_KEY}。
 * Embedding 模型优先从 ModelConfigService 读取，若无则使用配置文件默认值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiliconFlowEmbeddingAdapter implements EmbeddingService {

    private final ExternalProviderKeyRepository providerKeyRepository;
    private final ModelConfigService modelConfigService;

    @Value("${siliconflow.api.key:sk-placeholder}")
    private String configApiKey;

    @Value("${siliconflow.api.base-url:https://api.siliconflow.cn/v1}")
    private String baseUrl;

    // 运行时使用的实际配置
    private String effectiveApiKey;
    private String modelName;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        // 1. 从 ModelConfigService 获取 Embedding 模型配置
        try {
            ModelConfig modelConfig = modelConfigService.getConfig();
            if (modelConfig != null && modelConfig.getEmbeddingModel() != null
                    && !modelConfig.getEmbeddingModel().isEmpty()) {
                modelName = modelConfig.getEmbeddingModel();
                log.info("SiliconFlowEmbeddingAdapter: loaded embedding model from ModelConfig: {}", modelName);

                // 优先使用 embeddingApiKeyId 查找 API Key
                if (modelConfig.getEmbeddingApiKeyId() != null) {
                    try {
                        var optKey = providerKeyRepository.findById(modelConfig.getEmbeddingApiKeyId());
                        if (optKey.isPresent()) {
                            ExternalProviderKey key = optKey.get();
                            if (key.getEnabled() != null && key.getEnabled()) {
                                effectiveApiKey = key.getApiKey();
                                if (key.getBaseUrl() != null && !key.getBaseUrl().isEmpty()) {
                                    baseUrl = key.getBaseUrl();
                                }
                                log.info("SiliconFlowEmbeddingAdapter: loaded API key from external_provider_keys by ID: {}", key.getProvider());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("SiliconFlowEmbeddingAdapter: could not load key by ID: {}", e.getMessage());
                    }
                }

                // 如果没有通过 ID 找到，回退到从 provider 名称查找
                if ((effectiveApiKey == null || effectiveApiKey.isEmpty()) && modelConfig.getEmbeddingProvider() != null) {
                    try {
                        List<ExternalProviderKey> keys = providerKeyRepository.findByProvider(modelConfig.getEmbeddingProvider());
                        if (!keys.isEmpty()) {
                            ExternalProviderKey key = keys.stream()
                                    .filter(ExternalProviderKey::getEnabled)
                                    .findFirst()
                                    .orElse(keys.get(0));
                            effectiveApiKey = key.getApiKey();
                            if (key.getBaseUrl() != null && !key.getBaseUrl().isEmpty()) {
                                baseUrl = key.getBaseUrl();
                            }
                            log.info("SiliconFlowEmbeddingAdapter: loaded API key from DB (provider={})", modelConfig.getEmbeddingProvider());
                        }
                    } catch (Exception e) {
                        log.warn("SiliconFlowEmbeddingAdapter: could not load key from DB: {}", e.getMessage());
                    }
                }

                // 兼容旧配置：从 ModelConfig 直接读取
                if ((effectiveApiKey == null || effectiveApiKey.isEmpty()) && modelConfig.getSiliconFlowApiKey() != null
                        && !modelConfig.getSiliconFlowApiKey().isEmpty()) {
                    effectiveApiKey = modelConfig.getSiliconFlowApiKey();
                    log.info("SiliconFlowEmbeddingAdapter: loaded API key from ModelConfig (legacy)");
                }
                if ((baseUrl == null || baseUrl.isEmpty() || baseUrl.equals("https://api.siliconflow.cn/v1"))
                        && modelConfig.getSiliconFlowEndpoint() != null && !modelConfig.getSiliconFlowEndpoint().isEmpty()) {
                    baseUrl = modelConfig.getSiliconFlowEndpoint();
                    log.info("SiliconFlowEmbeddingAdapter: loaded endpoint from ModelConfig: {}", baseUrl);
                }
            } else {
                modelName = "BAAI/bge-m3";
            }
        } catch (Exception e) {
            log.warn("SiliconFlowEmbeddingAdapter: could not load from ModelConfig: {}", e.getMessage());
            modelName = "BAAI/bge-m3";
        }

        // 2. 如果 ModelConfig 没有 API Key，从数据库 external_provider_keys 读取
        if (effectiveApiKey == null || effectiveApiKey.isEmpty()) {
            try {
                List<ExternalProviderKey> keys = providerKeyRepository.findByProvider("SiliconFlow");
                if (!keys.isEmpty()) {
                    ExternalProviderKey key = keys.stream()
                            .filter(ExternalProviderKey::getEnabled)
                            .findFirst()
                            .orElse(keys.get(0));
                    effectiveApiKey = key.getApiKey();
                    if (key.getBaseUrl() != null && !key.getBaseUrl().isEmpty()) {
                        baseUrl = key.getBaseUrl();
                    }
                    log.info("SiliconFlowEmbeddingAdapter: loaded API key from DB (provider=SiliconFlow), baseUrl={}",
                            baseUrl);
                }
            } catch (Exception e) {
                log.warn("SiliconFlowEmbeddingAdapter: could not load key from DB: {}", e.getMessage());
            }
        }

        // 3. Fallback 到配置文件
        if (effectiveApiKey == null || effectiveApiKey.isEmpty() || "sk-placeholder".equals(effectiveApiKey)) {
            effectiveApiKey = configApiKey;
            log.warn(
                    "SiliconFlowEmbeddingAdapter: no DB key found, using application.properties key. effectiveKey starts with: {}",
                    effectiveApiKey.substring(0, Math.min(10, effectiveApiKey.length())));
        }

        log.info("SiliconFlowEmbeddingAdapter initialized: model={}, baseUrl={}", modelName, baseUrl);
    }

    @Override
    public List<Float> embed(String text) {
        // 检查 API Key 是否有效
        if (effectiveApiKey == null || effectiveApiKey.isEmpty() || effectiveApiKey.equals("sk-placeholder")) {
            log.error("SiliconFlow API Key is invalid or not configured! Cannot generate embeddings. " +
                      "Please configure a valid API Key in application.properties or ModelConfig.");
            throw new RuntimeException("SiliconFlow API Key is not configured. Please set a valid API Key.");
        }

        String url = baseUrl + "/embeddings";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(effectiveApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", modelName,
                "input", text);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode embeddingNode = root.path("data").get(0).path("embedding");

                List<Float> embedding = new ArrayList<>();
                if (embeddingNode.isArray()) {
                    for (JsonNode node : embeddingNode) {
                        embedding.add((float) node.asDouble());
                    }
                }
                log.info("Successfully generated embedding via SiliconFlow, dimension={}", embedding.size());
                return embedding;
            } else {
                log.error("SiliconFlow Embedding Failed: {}, response: {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Embedding API failed with status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling SiliconFlow Embedding API: {}", e.getMessage());
            throw new RuntimeException("Embedding failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "siliconflow";
    }

    public String getModelName() {
        return modelName;
    }

    public String getEffectiveApiKey() {
        return effectiveApiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Send a chat message to SiliconFlow LLM
     * @param prompt The prompt to send
     * @param model The model to use (defaults to embedding model if not specified)
     * @return The LLM response content
     */
    public String chat(String prompt, String model) {
        if (effectiveApiKey == null || effectiveApiKey.isEmpty() || effectiveApiKey.equals("sk-placeholder")) {
            throw new RuntimeException("SiliconFlow API Key is not configured. Please set a valid API Key.");
        }

        String chatModel = (model != null && !model.isEmpty()) ? model : "Qwen/Qwen2.5-7B-Instruct";
        String url = baseUrl + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(effectiveApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", chatModel,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.7,
                "max_tokens", 2000
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choicesNode = root.path("choices");
                if (choicesNode.isArray() && choicesNode.size() > 0) {
                    JsonNode messageNode = choicesNode.get(0).path("message");
                    String content = messageNode.path("content").asText();
                    log.info("LLM chat call successful via SiliconFlow, response length={}", content.length());
                    return content;
                }
            }
            log.error("SiliconFlow LLM chat failed: {}, response: {}", response.getStatusCode(), response.getBody());
            throw new RuntimeException("LLM chat API failed with status: " + response.getStatusCode());
        } catch (Exception e) {
            log.error("Error calling SiliconFlow LLM API: {}", e.getMessage());
            throw new RuntimeException("LLM chat failed: " + e.getMessage(), e);
        }
    }
}
