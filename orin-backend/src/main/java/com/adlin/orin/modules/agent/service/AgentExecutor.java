package com.adlin.orin.modules.agent.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Agent执行器 - 用于工作流中统一调用智能体
 * 提供标准化的输入输出接口，屏蔽不同Agent实现的差异
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutor {

    private final AgentManageService agentManageService;

    /**
     * 在工作流中执行智能体
     *
     * @param agentId Agent的数据库ID (Long类型)
     * @param inputs  输入参数 Map，必须包含 "message" 或 "query" 字段
     * @return 标准化的输出 Map，包含 "response", "success" 等字段
     */
    public Map<String, Object> executeAgent(Long agentId, Map<String, Object> inputs) {
        log.info("Executing agent in workflow: agentId={}", agentId);

        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 获取Agent元数据
            AgentMetadata metadata = agentManageService.getAgentMetadata(agentId.toString());
            if (metadata == null) {
                throw new IllegalArgumentException("Agent not found: " + agentId);
            }

            // 2. 提取消息内容
            String message = extractMessage(inputs);
            if (message == null || message.trim().isEmpty()) {
                throw new IllegalArgumentException("Message is required in inputs");
            }

            // 3. 调用Agent
            log.debug("Invoking agent '{}' with message: {}", metadata.getName(), message);
            Optional<Object> response = agentManageService.chat(agentId.toString(), message, (String) null);

            // 4. 标准化输出
            if (response.isPresent()) {
                result.put("success", true);
                result.put("response", response.get());
                result.put("agentId", agentId);
                result.put("agentName", metadata.getName());
                log.info("Agent execution successful: agentId={}", agentId);
            } else {
                result.put("success", false);
                result.put("error", "Agent returned empty response");
                log.warn("Agent returned empty response: agentId={}", agentId);
            }

        } catch (Exception e) {
            log.error("Agent execution failed: agentId={}, error={}", agentId, e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
        }

        return result;
    }

    /**
     * 从输入参数中提取消息内容
     * 支持 "message", "query", "prompt" 等常见字段名
     */
    private String extractMessage(Map<String, Object> inputs) {
        if (inputs == null) {
            return null;
        }

        // 优先级: message > query > prompt > input
        Object message = inputs.get("message");
        if (message != null) {
            return message.toString();
        }

        Object query = inputs.get("query");
        if (query != null) {
            return query.toString();
        }

        Object prompt = inputs.get("prompt");
        if (prompt != null) {
            return prompt.toString();
        }

        Object input = inputs.get("input");
        if (input != null) {
            return input.toString();
        }

        return null;
    }

    /**
     * 验证Agent是否可用
     */
    public boolean isAgentAvailable(Long agentId) {
        try {
            AgentMetadata metadata = agentManageService.getAgentMetadata(agentId.toString());
            return metadata != null;
        } catch (Exception e) {
            log.warn("Failed to check agent availability: agentId={}", agentId, e);
            return false;
        }
    }
}
