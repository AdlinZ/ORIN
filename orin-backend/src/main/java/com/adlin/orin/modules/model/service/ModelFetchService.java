package com.adlin.orin.modules.model.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ModelFetchService {

    private final RestTemplate restTemplate = new RestTemplate();

    public List<Map<String, Object>> fetchModelsFromApi(String baseUrl, String apiKey) {
        try {
            String url = baseUrl;
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
                            } else if (lowerId.contains("image") || lowerId.contains("vision")
                                    || lowerId.contains("flux") || lowerId.contains("stable-diffusion")) {
                                model.put("type", "TEXT_TO_IMAGE");
                            } else if (lowerId.contains("speech") || lowerId.contains("audio")
                                    || lowerId.contains("whisper")) {
                                model.put("type", "SPEECH_TO_TEXT");
                            } else if (lowerId.contains("video")) {
                                model.put("type", "TEXT_TO_VIDEO");
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
}
