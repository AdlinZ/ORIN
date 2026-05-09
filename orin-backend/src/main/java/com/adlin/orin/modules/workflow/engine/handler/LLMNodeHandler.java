package com.adlin.orin.modules.workflow.engine.handler;

import com.adlin.orin.modules.apikey.entity.ExternalProviderKey;
import com.adlin.orin.modules.apikey.service.ProviderKeyService;
import com.adlin.orin.modules.model.entity.ModelMetadata;
import com.adlin.orin.modules.model.service.DeepSeekIntegrationService;
import com.adlin.orin.modules.model.service.ModelManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component("llmNodeHandler")
@RequiredArgsConstructor
public class LLMNodeHandler implements NodeHandler {

    private final DeepSeekIntegrationService deepSeekService;
    private final ModelManageService modelManageService;
    private final ProviderKeyService providerKeyService;

    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) { // input context
        String modelName = (String) context.getOrDefault("model", "deepseek-chat");
        String prompt = firstNonBlankString(
                promptTemplateToText(nodeData.get("prompt_template")),
                nodeData.get("prompt"),
                context.get("prompt_template"),
                context.get("prompt"));

        // Also check if model is configured in nodeData directly
        if (nodeData.containsKey("model")) {
            Object modelObj = nodeData.get("model");
            if (modelObj instanceof String) {
                modelName = (String) modelObj;
            } else if (modelObj instanceof Map) {
                modelName = (String) ((Map) modelObj).get("name");
            }
        }

        log.info("LLMNode executing: model={}, promptLength={}", modelName, prompt != null ? prompt.length() : 0);

        String finalModelName = modelName;
        ModelMetadata modelMeta = modelManageService.getAllModels().stream()
                .filter(m -> m.getName().equals(finalModelName) || m.getModelId().equals(finalModelName))
                .findFirst()
                .orElse(null);

        String responseText;

        if (modelMeta != null) {
            String providerName = modelMeta.getProvider();
            ExternalProviderKey providerKey = providerKeyService.getActiveKeys().stream()
                    .filter(k -> k.getProvider().equals(providerName))
                    .findFirst()
                    .orElse(null);

            if (providerKey != null && "deepseek".equalsIgnoreCase(providerName)) {
                Optional<Object> response = deepSeekService.sendMessage(
                        providerKey.getBaseUrl(),
                        providerKey.getApiKey(),
                        modelMeta.getModelId(),
                        prompt);

                if (response.isPresent()) {
                    Map<String, Object> respMap = (Map<String, Object>) response.get();
                    responseText = extractContentFromResponse(respMap);
                } else {
                    responseText = "Error: No response from LLM provider.";
                }
            } else {
                // Fallback or other providers
                responseText = "Error: Provider setup missing for " + providerName;
            }
        } else {
            responseText = "Simulated LLM Output for [" + modelName + "]: " + prompt;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("text", responseText);
        result.put("output", responseText);
        result.put("model", modelName);

        return NodeExecutionResult.success(result);
    }

    private String firstNonBlankString(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private String promptTemplateToText(Object promptTemplate) {
        if (promptTemplate instanceof String text) {
            return text;
        }
        if (promptTemplate instanceof List<?> messages) {
            StringBuilder builder = new StringBuilder();
            for (Object message : messages) {
                if (message instanceof Map<?, ?> map) {
                    Object role = map.get("role");
                    Object text = map.get("text");
                    if (text != null) {
                        if (role != null) {
                            builder.append('[').append(role).append("] ");
                        }
                        builder.append(text).append('\n');
                    }
                }
            }
            return builder.toString().trim();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private String extractContentFromResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }
        } catch (Exception e) {
            log.warn("Failed to parse LLM response", e);
        }
        return response.toString();
    }
}
