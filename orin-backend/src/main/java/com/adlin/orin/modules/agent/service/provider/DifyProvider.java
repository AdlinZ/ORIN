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
            // 音频处理：调用 Dify 的语音转文字 API
            return processAudioRequest(profile, request);
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

    /**
     * 处理音频请求 - 调用 Dify 语音转文字
     */
    private InteractionResult processAudioRequest(AgentAccessProfile profile, InteractionRequest request) {
        try {
            // 获取音频 URL 或 Base64 数据
            String audioData = request.getContent();
            
            if (audioData == null || audioData.isEmpty()) {
                return InteractionResult.error("No audio data provided");
            }

            // Dify 不直接支持音频输入，返回提示信息
            // 实际生产环境可以通过 Dify 的文件上传接口处理
            log.info("Audio processing for Dify not fully implemented, content: {}", 
                    audioData.substring(0, Math.min(50, audioData.length())));
            
            // 返回提示信息
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Dify provider currently doesn't support direct audio input. Please convert audio to text first.");
            result.put("suggestion", "Use SiliconFlow or other ASR service for audio transcription");
            
            return InteractionResult.success("text", result);
            
        } catch (Exception e) {
            log.error("Audio processing failed", e);
            return InteractionResult.error("Audio processing failed: " + e.getMessage());
        }
    }
}
