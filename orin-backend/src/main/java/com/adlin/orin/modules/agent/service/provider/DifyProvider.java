package com.adlin.orin.modules.agent.service.provider;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DifyProvider implements MultiModalProvider {

    private final DifyIntegrationService difyService;
    private final AgentAccessProfileRepository profileRepository;

    @Override
    public String getProviderName() {
        return "DIFY";
    }

    @Override
    public InteractionResult process(AgentMetadata meta, InteractionRequest request) {
        // Fetch Access Profile for Endpoint & API Key
        AgentAccessProfile profile = profileRepository.findById(meta.getAgentId())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        if ("AUDIO".equals(request.getType())) {
            // TODO: Implement Audio to Text for Dify
            return InteractionResult.error("Audio not yet supported for Dify");
        }

        // Handle TEXT inputs (Chat / Workflow / Completion)
        String mode = meta.getMode();
        Map<String, Object> inputs = parseInputs(request.getContent());

        Optional<Object> response;
        if ("workflow".equalsIgnoreCase(mode)) {
            response = difyService.runWorkflow(profile.getEndpointUrl(), profile.getApiKey(), inputs);
        } else if ("completion".equalsIgnoreCase(mode)) {
            response = difyService.sendCompletion(profile.getEndpointUrl(), profile.getApiKey(), request.getContent(),
                    inputs);
        } else {
            // Default Chat
            String conversationId = (String) request.getContext().getOrDefault("conversationId", "");
            response = difyService.sendMessage(profile.getEndpointUrl(), profile.getApiKey(), conversationId,
                    request.getContent());
        }

        if (response.isPresent()) {
            return parseResponse(response.get(), mode);
        } else {
            return InteractionResult.error("No response from Dify");
        }
    }

    private Map<String, Object> parseInputs(String content) {
        try {
            if (content != null && content.trim().startsWith("{")) {
                return new ObjectMapper().readValue(content, new TypeReference<Map<String, Object>>() {
                });
            }
        } catch (Exception e) {
            /* ignore */}

        // Fallback
        Map<String, Object> map = new HashMap<>();
        map.put("query", content);
        return map;
    }

    private InteractionResult parseResponse(Object responseObj, String mode) {
        // Logic to extract actual content from Dify Response Map
        // Simplified for brevity
        if (responseObj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) responseObj;
            if ("workflow".equalsIgnoreCase(mode)) {
                Object outputs = map.get("data"); // workflow usually returns data.outputs
                if (map.containsKey("outputs"))
                    outputs = map.get("outputs");
                return InteractionResult.success("JSON", outputs);
            } else {
                // Chat/Completion
                if (map.containsKey("answer"))
                    return InteractionResult.success("TEXT", map.get("answer"));
                // Check choices/message for other formats if needed
            }
        }
        return InteractionResult.success("JSON", responseObj);
    }
}
