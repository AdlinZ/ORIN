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
        if (baseUrl.contains("siliconflow")) {
            return fetchSiliconFlowModels(baseUrl, apiKey);
        }

        if (baseUrl.contains("11434") || baseUrl.toLowerCase().contains("ollama")) {
            return fetchOllamaModels(baseUrl);
        }

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
        java.util.Map<String, Map<String, Object>> masterList = new java.util.LinkedHashMap<>();

        // 定义抓取任务： sub_type -> internal type
        // 参考官方参数：chat, embedding, reranker, text-to-image, image-to-image,
        // speech-to-text, text-to-video, image-to-video, text-to-speech, image-to-text
        Map<String, String> subTypeQueries = new java.util.HashMap<>();
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

        // 1. 根据 sub_type 抓取
        for (Map.Entry<String, String> entry : subTypeQueries.entrySet()) {
            String url = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "sub_type=" + entry.getKey();
            fetchAndMerge(url, apiKey, entry.getValue(), masterList);
        }

        // 2. 根据 type 抓取 (补充 type=audio/image/video 整体分类)
        fetchAndMerge(baseUrl + (baseUrl.contains("?") ? "&" : "?") + "type=audio", apiKey, "TEXT_TO_SPEECH",
                masterList);
        fetchAndMerge(baseUrl + (baseUrl.contains("?") ? "&" : "?") + "type=image", apiKey, "TEXT_TO_IMAGE",
                masterList);
        fetchAndMerge(baseUrl + (baseUrl.contains("?") ? "&" : "?") + "type=video", apiKey, "TEXT_TO_VIDEO",
                masterList);

        // 3. 最后抓取全部，查漏补缺 (对未知类型的兜底)
        fetchAndMerge(baseUrl, apiKey, "CHAT", masterList);

        return new java.util.ArrayList<>(masterList.values());
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
