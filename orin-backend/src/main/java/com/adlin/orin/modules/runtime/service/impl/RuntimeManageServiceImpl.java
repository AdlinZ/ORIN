package com.adlin.orin.modules.runtime.service.impl;

import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import com.adlin.orin.modules.runtime.entity.AgentLog;
import com.adlin.orin.modules.runtime.repository.AgentLogRepository;
import com.adlin.orin.modules.runtime.service.RuntimeManageService;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeManageServiceImpl implements RuntimeManageService {

    private final AgentHealthStatusRepository healthStatusRepository;
    private final AgentLogRepository logRepository;
    private final com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository profileRepository;
    private final DifyIntegrationService difyIntegrationService;
    private final AuditLogRepository auditLogRepository;
    private final com.adlin.orin.modules.system.service.LogConfigService logConfigService;

    @Override
    public void controlAgent(String agentId, String action) {
        AgentHealthStatus status = healthStatusRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found: " + agentId));

        switch (action.toLowerCase()) {
            case "stop":
                status.setStatus(AgentHealthStatus.Status.STOPPED);
                log.info("Stopping agent: {}", agentId);
                break;
            case "start":
                status.setStatus(AgentHealthStatus.Status.RUNNING);
                log.info("Starting agent: {}", agentId);
                break;
            case "restart":
                status.setStatus(AgentHealthStatus.Status.RUNNING);
                log.info("Restarting agent: {}", agentId);
                break;
            default:
                throw new IllegalArgumentException("Unknown action: " + action);
        }

        healthStatusRepository.save(status);

        // Record operation log
        AgentLog operLog = AgentLog.builder()
                .agentId(agentId)
                .type("SYSTEM")
                .content("User triggered action: " + action)
                .status("SUCCESS")
                .timestamp(LocalDateTime.now())
                .duration(0)
                .build();
        logRepository.save(operLog);
    }

    @Override
    public List<AgentLog> getAgentLogs(String agentId) {
        // 首先尝试从审计日志获取真实数据
        int retentionDays = logConfigService.getRetentionDays();
        LocalDateTime startTime = LocalDateTime.now().minusDays(Math.max(retentionDays, 7)); // At least 7 days default

        List<com.adlin.orin.modules.audit.entity.AuditLog> auditLogs = auditLogRepository
                .findByProviderIdAndCreatedAtBetweenOrderByCreatedAtAsc(agentId, startTime, LocalDateTime.now());

        log.info("Retrieving logs for agent: {}, found {} audit logs from {}", agentId, auditLogs.size(), startTime);

        // 转换审计日志为AgentLog
        List<AgentLog> logsFromAudit = auditLogs.stream()
                .map(audit -> AgentLog.builder()
                        .agentId(agentId)
                        .type(audit.getSuccess() ? "API_CALL" : "ERROR")
                        .content(audit.getSuccess() ? audit.getRequestParams() : audit.getErrorMessage())
                        .status(audit.getSuccess() ? "SUCCESS" : "FAILED")
                        .duration(audit.getResponseTime() != null ? audit.getResponseTime().intValue() : 0)
                        .tokens(audit.getTotalTokens())
                        .sessionId(audit.getId())
                        .response(audit.getResponseContent())
                        .timestamp(audit.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        if (!logsFromAudit.isEmpty()) {
            return logsFromAudit;
        }

        // 尝试从Dify获取对话历史
        java.util.Optional<com.adlin.orin.modules.agent.entity.AgentAccessProfile> profileOpt = profileRepository
                .findById(agentId);
        if (profileOpt.isPresent()) {
            String apiKey = profileOpt.get().getApiKey();
            String endpoint = profileOpt.get().getEndpointUrl();

            try {
                var conversationsResult = difyIntegrationService.getConversations(endpoint, apiKey, agentId);
                if (conversationsResult.isPresent()) {
                    @SuppressWarnings("unchecked")
                    var conversationsData = (java.util.Map<String, Object>) conversationsResult.get();
                    if (conversationsData.containsKey("data")) {
                        @SuppressWarnings("unchecked")
                        var conversationsList = (java.util.List<java.util.Map<String, Object>>) conversationsData
                                .get("data");
                        if (!conversationsList.isEmpty()) {
                            return conversationsList.stream().map(conv -> AgentLog.builder()
                                    .agentId(agentId)
                                    .type("CONVERSATION")
                                    .content("Conversation: " + conv.getOrDefault("name", "Unnamed"))
                                    .sessionId((String) conv.get("id"))
                                    .tokens(0)
                                    .duration(0)
                                    .status("SUCCESS")
                                    .timestamp(LocalDateTime.now())
                                    .build()).collect(Collectors.toList());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch Dify logs for agent {}: {}", agentId, e.getMessage());
            }
        }

        // 最后从数据库获取已保存的日志
        return logRepository.findByAgentIdOrderByTimestampDesc(agentId);
    }
}
