package com.adlin.orin.modules.monitor.task;

import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentStatus;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import com.adlin.orin.modules.model.service.SiliconFlowIntegrationService;
import com.adlin.orin.modules.model.service.ZhipuIntegrationService;
import com.adlin.orin.modules.model.service.DeepSeekIntegrationService;
import com.adlin.orin.modules.model.service.KimiIntegrationService;
import com.adlin.orin.modules.model.service.OllamaIntegrationService;
import com.adlin.orin.modules.model.service.MinimaxIntegrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能体健康检查定时任务
 * 每5分钟检查一次所有智能体的健康状况
 */
@Slf4j
@Component
public class AgentHealthCheckTask {

    private final AgentHealthStatusRepository healthStatusRepository;
    private final AgentAccessProfileRepository accessProfileRepository;
    private final DifyIntegrationService difyIntegrationService;
    private final SiliconFlowIntegrationService siliconFlowIntegrationService;
    private final ZhipuIntegrationService zhipuIntegrationService;
    private final DeepSeekIntegrationService deepSeekIntegrationService;
    private final KimiIntegrationService kimiIntegrationService;
    private final OllamaIntegrationService ollamaIntegrationService;
    private final MinimaxIntegrationService minimaxIntegrationService;

    public AgentHealthCheckTask(
            AgentHealthStatusRepository healthStatusRepository,
            AgentAccessProfileRepository accessProfileRepository,
            DifyIntegrationService difyIntegrationService,
            SiliconFlowIntegrationService siliconFlowIntegrationService,
            ZhipuIntegrationService zhipuIntegrationService,
            DeepSeekIntegrationService deepSeekIntegrationService,
            KimiIntegrationService kimiIntegrationService,
            OllamaIntegrationService ollamaIntegrationService,
            MinimaxIntegrationService minimaxIntegrationService) {
        this.healthStatusRepository = healthStatusRepository;
        this.accessProfileRepository = accessProfileRepository;
        this.difyIntegrationService = difyIntegrationService;
        this.siliconFlowIntegrationService = siliconFlowIntegrationService;
        this.zhipuIntegrationService = zhipuIntegrationService;
        this.deepSeekIntegrationService = deepSeekIntegrationService;
        this.kimiIntegrationService = kimiIntegrationService;
        this.ollamaIntegrationService = ollamaIntegrationService;
        this.minimaxIntegrationService = minimaxIntegrationService;
    }

    /**
     * 定时检查所有智能体的健康状况
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void checkAgentHealth() {
        log.info("Starting agent health check...");

        try {
            List<AgentHealthStatus> allAgents = healthStatusRepository.findAll();
            log.info("Checking health for {} agents", allAgents.size());

            for (AgentHealthStatus agent : allAgents) {
                checkSingleAgent(agent);
            }

            log.info("Agent health check completed");
        } catch (Exception e) {
            log.error("Error during agent health check", e);
        }
    }

    /**
     * 检查单个智能体的健康状况
     */
    private void checkSingleAgent(AgentHealthStatus agent) {
        try {
            String agentId = agent.getAgentId();
            String providerType = agent.getProviderType();

            // 获取访问凭证
            AgentAccessProfile profile = accessProfileRepository.findById(agentId).orElse(null);
            if (profile == null) {
                log.warn("No access profile found for agent: {}", agentId);
                // 找不到凭证时不改为 ERROR，保持原有状态
                return;
            }

            boolean isHealthy = false;

            // 根据不同的 Provider 类型进行健康检查
            if ("SiliconFlow".equalsIgnoreCase(providerType)) {
                isHealthy = checkSiliconFlowAgent(profile);
            } else if ("Dify".equalsIgnoreCase(providerType)) {
                isHealthy = checkDifyAgent(profile);
            } else if ("Zhipu".equalsIgnoreCase(providerType)) {
                isHealthy = checkZhipuAgent(profile, agent);
            } else if ("DeepSeek".equalsIgnoreCase(providerType)) {
                isHealthy = checkDeepSeekAgent(profile, agent);
            } else if ("Kimi".equalsIgnoreCase(providerType) || "Moonshot".equalsIgnoreCase(providerType)) {
                isHealthy = checkKimiAgent(profile);
            } else if ("Ollama".equalsIgnoreCase(providerType)) {
                isHealthy = checkOllamaAgent(profile, agent);
            } else if ("MiniMax".equalsIgnoreCase(providerType)) {
                isHealthy = checkMiniMaxAgent(profile, agent);
            } else {
                log.warn("Unknown provider type for agent {}: {}", agentId, providerType);
                updateAgentStatus(agent, AgentStatus.UNKNOWN, 50);
                return;
            }

            // 更新健康状态
            if (isHealthy) {
                updateAgentStatus(agent, AgentStatus.RUNNING, 100);
                log.debug("Agent {} is healthy", agentId);
            } else {
                // 健康检查失败时不改为 ERROR，保留原状态避免误报
                // 因为外部 API 可能暂时不可达但实际使用正常
                log.warn("Agent {} health check failed, keeping existing status: {}", agentId, agent.getStatus());
            }

        } catch (Exception e) {
            log.error("Error checking agent {}: {}", agent.getAgentId(), e.getMessage());
            // 检查异常时不改为 ERROR，保留原状态避免误报
        }
    }

    /**
     * 检查 SiliconFlow 智能体健康状况
     */
    private boolean checkSiliconFlowAgent(AgentAccessProfile profile) {
        try {
            // 使用 testConnection 方法检查连接
            return siliconFlowIntegrationService.testConnection(
                    profile.getEndpointUrl(),
                    profile.getApiKey());
        } catch (Exception e) {
            log.debug("SiliconFlow agent {} connection test failed: {}",
                    profile.getAgentId(), e.getMessage());
            return false;
        }
    }

    /**
     * 检查 Dify 智能体健康状况
     */
    private boolean checkDifyAgent(AgentAccessProfile profile) {
        try {
            // 使用 testConnection 方法检查连接
            return difyIntegrationService.testConnection(
                    profile.getEndpointUrl(),
                    profile.getApiKey());
        } catch (Exception e) {
            log.debug("Dify agent {} connection test failed: {}",
                    profile.getAgentId(), e.getMessage());
            return false;
        }
    }

    /**
     * 检查智谱AI智能体健康状况
     */
    private boolean checkZhipuAgent(AgentAccessProfile profile, AgentHealthStatus agent) {
        try {
            // 使用 testConnection 方法检查连接
            // 需要传入模型名称，从 agent 的 modelName 获取
            String modelName = agent.getModelName() != null ? agent.getModelName() : "glm-4";
            return zhipuIntegrationService.testConnection(
                    profile.getEndpointUrl(),
                    profile.getApiKey(),
                    modelName);
        } catch (Exception e) {
            log.debug("Zhipu AI agent {} connection test failed: {}",
                    profile.getAgentId(), e.getMessage());
            return false;
        }
    }

    /**
     * 检查DeepSeek智能体健康状况
     */
    private boolean checkDeepSeekAgent(AgentAccessProfile profile, AgentHealthStatus agent) {
        try {
            // 使用 testConnection 方法检查连接
            // 需要传入模型名称，从 agent 的 modelName 获取
            String modelName = agent.getModelName() != null ? agent.getModelName() : "deepseek-chat";
            return deepSeekIntegrationService.testConnection(
                    profile.getEndpointUrl(),
                    profile.getApiKey(),
                    modelName);
        } catch (Exception e) {
            log.debug("DeepSeek agent {} connection test failed: {}",
                    profile.getAgentId(), e.getMessage());
            return false;
        }
    }

    /**
     * 检查Kimi智能体健康状况
     */
    private boolean checkKimiAgent(AgentAccessProfile profile) {
        try {
            return kimiIntegrationService.testConnection(
                    profile.getEndpointUrl(),
                    profile.getApiKey());
        } catch (Exception e) {
            log.debug("Kimi agent {} connection test failed: {}",
                    profile.getAgentId(), e.getMessage());
            return false;
        }
    }

    /**
     * 检查Ollama智能体健康状况
     */
    private boolean checkOllamaAgent(AgentAccessProfile profile, AgentHealthStatus agent) {
        try {
            String modelName = agent.getModelName() != null ? agent.getModelName() : "llama3";
            return ollamaIntegrationService.testConnection(
                    profile.getEndpointUrl(),
                    profile.getApiKey(),
                    modelName);
        } catch (Exception e) {
            log.debug("Ollama agent {} connection test failed: {}",
                    profile.getAgentId(), e.getMessage());
            return false;
        }
    }

    /**
     * 检查MiniMax智能体健康状况
     */
    private boolean checkMiniMaxAgent(AgentAccessProfile profile, AgentHealthStatus agent) {
        try {
            String modelName = agent.getModelName() != null ? agent.getModelName() : "abab6.5g-chat";
            return minimaxIntegrationService.testConnection(
                    profile.getEndpointUrl(),
                    profile.getApiKey(),
                    modelName);
        } catch (Exception e) {
            log.debug("MiniMax agent {} connection test failed: {}",
                    profile.getAgentId(), e.getMessage());
            return false;
        }
    }

    /**
     * 更新智能体状态
     */
    private void updateAgentStatus(AgentHealthStatus agent, AgentStatus status, int healthScore) {
        agent.setStatus(status);
        agent.setHealthScore(healthScore);
        agent.setLastHeartbeat(System.currentTimeMillis());
        healthStatusRepository.save(agent);
    }
}
