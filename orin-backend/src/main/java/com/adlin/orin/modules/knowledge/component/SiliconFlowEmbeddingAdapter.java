package com.adlin.orin.modules.knowledge.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SiliconFlowEmbeddingAdapter implements EmbeddingService {

    @Value("${siliconflow.api.key}")
    private String apiKey;

    @Value("${siliconflow.api.base-url:https://api.siliconflow.cn/v1}")
    private String baseUrl;

    @Value("${siliconflow.embedding.model:BAAI/bge-m3}")
    private String modelName;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Float> embed(String text) {
        String url = baseUrl + "/embeddings";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
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
