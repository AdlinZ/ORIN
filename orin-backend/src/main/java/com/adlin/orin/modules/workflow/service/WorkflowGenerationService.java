package com.adlin.orin.modules.workflow.service;

import com.adlin.orin.modules.apikey.entity.ExternalProviderKey;
import com.adlin.orin.modules.apikey.service.ProviderKeyService;
import com.adlin.orin.modules.model.service.DeepSeekIntegrationService;
import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowGenerationService {

    private final DeepSeekIntegrationService deepSeekService;
    private final ProviderKeyService providerKeyService;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            You are an expert AI Workflow Designer. Your task is to generate a Dify-compatible workflow graph based on user requirements.
             The graph MUST be in JSON format and contain 'nodes' and 'edges'.

            Supported Node Types:
            - start: The entry point. Data: { id: "start_1" }
            - end: The terminal point. Data: { id: "end_1" }
            - llm: Large Language Model node. Data: { label: "...", model: "deepseek-chat", prompt: "..." }
            - answer: Direct response to user. Data: { label: "直接回复" }
            - knowledge_retrieval: Retrieval from knowledge base. Data: { label: "知识检索" }
            - if_else: Logic branching. Data: { label: "条件分支" }
            - code: Python code execution. Data: { label: "代码执行", code: "...", code_language: "python3" }
            - agent: Invoke another agent. Data: { label: "智能体", agentId: "..." }

            Rules:
            1. Always include exactly one 'start' node and at least one 'end' or 'answer' node.
            2. Nodes must have unique IDs (e.g., "start_1", "llm_1", "answer_1").
            3. Edges must connect nodes via 'source' and 'target' IDs.
            4. For 'llm' nodes, provide a clear 'prompt' that uses input variables like {{#sys.query#}}.
            5. Respond ONLY with the JSON object. Do not include markdown formatting or explanations.

            Example Output:
            {
              "nodes": [
                { "id": "start_1", "type": "start", "position": { "x": 100, "y": 200 }, "data": { "label": "开始" } },
                { "id": "llm_1", "type": "llm", "position": { "x": 400, "y": 200 }, "data": { "label": "LLM", "model": "deepseek-chat", "prompt": "Translate: {{#sys.query#}}" } },
                { "id": "answer_1", "type": "answer", "position": { "x": 700, "y": 200 }, "data": { "label": "回复" } }
              ],
              "edges": [
                { "id": "edge_1", "source": "start_1", "sourceHandle": "source", "target": "llm_1", "targetHandle": "target" },
                { "id": "edge_2", "source": "llm_1", "sourceHandle": "source", "target": "answer_1", "targetHandle": "target" }
              ]
            }
            """;

    public Map<String, Object> generateWorkflow(String userPrompt) {
        log.info("Generating workflow for prompt: {}", userPrompt);

        List<ExternalProviderKey> activeKeys = providerKeyService.getActiveKeys();

        if (activeKeys.isEmpty()) {
            throw new BusinessException(ErrorCode.MODEL_API_ERROR, "未配置任何 AI 服务商密钥，请在系统设置中添加 API Key");
        }

        // Try to find a preferred provider
        ExternalProviderKey providerKey = activeKeys.stream()
                .filter(k -> "deepseek".equalsIgnoreCase(k.getProvider())
                        || "siliconflow".equalsIgnoreCase(k.getProvider()))
                .findFirst()
                .orElse(activeKeys.get(0));

        log.info("Using provider: {} for workflow generation", providerKey.getProvider());

        String fullPrompt = SYSTEM_PROMPT + "\n\nUser Requirement: " + userPrompt;

        Optional<Object> response;
        String modelName = "deepseek-chat";

        // Use the appropriate service based on provider
        if ("deepseek".equalsIgnoreCase(providerKey.getProvider())) {
            response = deepSeekService.sendMessage(providerKey.getBaseUrl(), providerKey.getApiKey(), modelName,
                    fullPrompt);
        } else {
            // Default to deepseek-compatible or just use deepseek service if baseUrl is set
            // Most providers (SiliconFlow, etc.) are OpenAI-compatible
            response = deepSeekService.sendMessage(providerKey.getBaseUrl(), providerKey.getApiKey(), modelName,
                    fullPrompt);
        }

        if (response.isEmpty()) {
            throw new BusinessException(ErrorCode.MODEL_API_ERROR, "AI 服务商响应为空，请检查网络或 API Key 余额");
        }

        try {
            Map<String, Object> respMap = (Map<String, Object>) response.get();
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("AI 返回结果格式错误: missing choices");
            }
            String content = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");

            // Extract JSON using regex
            content = extractJson(content);

            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.error("Failed to parse AI generated workflow", e);
            if (e instanceof BusinessException)
                throw (BusinessException) e;
            throw new BusinessException(ErrorCode.MODEL_API_ERROR, "AI 生成失败，格式解析错误: " + e.getMessage());
        }

    }

    private String extractJson(String content) {
        if (content == null)
            return "{}";
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return content.trim();
    }
}
