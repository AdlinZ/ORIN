package com.adlin.orin.modules.knowledge.component;

import com.adlin.orin.modules.apikey.entity.ExternalProviderKey;
import com.adlin.orin.modules.apikey.repository.ExternalProviderKeyRepository;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiliconFlowEmbeddingAdapter implements EmbeddingService {

    private final ExternalProviderKeyRepository providerKeyRepository;

    @Value("${siliconflow.api.key:sk-placeholder}")
    private String configApiKey;

    @Value("${siliconflow.api.base-url:https://api.siliconflow.cn/v1}")
    private String baseUrl;

    @Value("${siliconflow.embedding.model:BAAI/bge-m3}")
    private String modelName;

    // 运行时使用的实际 Key
    private String effectiveApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        // 优先从数据库读取 SiliconFlow Provider Key
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

        // Fallback 到配置文件
        if (effectiveApiKey == null || effectiveApiKey.isEmpty() || "sk-placeholder".equals(effectiveApiKey)) {
            effectiveApiKey = configApiKey;
            log.warn(
                    "SiliconFlowEmbeddingAdapter: no DB key found, using application.properties key. effectiveKey starts with: {}",
                    effectiveApiKey.substring(0, Math.min(10, effectiveApiKey.length())));
        }
    }

    @Override
    public List<Float> embed(String text) {
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
                return embedding;
            } else {
                log.error("SiliconFlow Embedding Failed: {}", response.getStatusCode());
                throw new RuntimeException("Embedding API failed with status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling SiliconFlow Embedding API", e);
            throw new RuntimeException("Embedding failed", e);
        }
    }

    @Override
    public String getProviderName() {
        return "siliconflow";
    }
}
