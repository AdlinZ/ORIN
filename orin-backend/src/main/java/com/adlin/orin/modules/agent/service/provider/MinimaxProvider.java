package com.adlin.orin.modules.agent.service.provider;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.model.service.MinimaxIntegrationService;
import com.adlin.orin.modules.multimodal.service.MultimodalFileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinimaxProvider implements MultiModalProvider {

    private final MinimaxIntegrationService minimaxIntegrationService;
    private final AgentAccessProfileRepository accessProfileRepository;
    private final MultimodalFileService multimodalFileService;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderName() {
        return "MINIMAX";
    }

    @Override
    public InteractionResult process(AgentMetadata meta, InteractionRequest request) {
        AgentAccessProfile profile = accessProfileRepository.findById(meta.getAgentId())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        String type = request.getType();
        if ("TEXT".equalsIgnoreCase(type) || "CHAT".equalsIgnoreCase(type)) {
            return handleChat(meta, profile, request);
        } else if ("AUDIO".equalsIgnoreCase(type) || "TTS".equalsIgnoreCase(type)) {
            return handleTTS(meta, profile, request);
        }

        return InteractionResult.error("Unsupported interaction type: " + type);
    }

    private InteractionResult handleChat(AgentMetadata meta, AgentAccessProfile profile, InteractionRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        // Simple chat for now, history should be handled by the caller or here
        // The implementation in AgentManageServiceImpl handles history for SiliconFlow
        // Let's keep it simple here as it's usually called from there
        messages.add(Map.of("role", "user", "content", request.getContent()));

        Optional<Object> response = minimaxIntegrationService.sendMessage(
                profile.getEndpointUrl(),
                profile.getApiKey(),
                meta.getModelName(),
                messages,
                meta.getTemperature() != null ? meta.getTemperature() : 0.7);

        if (response.isPresent()) {
            return InteractionResult.success("TEXT", response.get());
        }
        return InteractionResult.error("MiniMax chat failed");
    }

    private InteractionResult handleTTS(AgentMetadata meta, AgentAccessProfile profile, InteractionRequest request) {
        String prompt = request.getContent();
        Map<String, Object> params = new HashMap<>();

        try {
            if (prompt != null && prompt.trim().startsWith("{")) {
                Map<String, Object> payload = objectMapper.readValue(prompt, new TypeReference<Map<String, Object>>() {
                });
                if (payload.containsKey("input"))
                    prompt = (String) payload.get("input");
                if (payload.containsKey("text"))
                    prompt = (String) payload.get("text");
                params.putAll(payload);
            }
        } catch (Exception e) {
            log.debug("TTS input is not JSON, using as raw prompt");
        }

        Optional<byte[]> audioOpt = minimaxIntegrationService.generateAudio(
                profile.getEndpointUrl(),
                profile.getApiKey(),
                meta.getModelName(),
                prompt,
                params);

        if (audioOpt.isPresent()) {
            String filename = "minimax_tts_" + UUID.randomUUID().toString() + ".mp3";
            try {
                var savedFile = multimodalFileService.uploadFile(audioOpt.get(), filename, "audio/mpeg",
                        "agent:" + meta.getAgentId());
                Map<String, Object> data = new HashMap<>();
                data.put("audio_url", "/api/v1/multimodal/files/" + savedFile.getId() + "/download");
                data.put("file_id", savedFile.getId());
                data.put("text", prompt);
                return InteractionResult.success("AUDIO", data);
            } catch (Exception e) {
                return InteractionResult.error("Failed to save audio file: " + e.getMessage());
            }
        }
        return InteractionResult.error("MiniMax TTS failed");
    }
}
