package com.adlin.orin.modules.mcp.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.collaboration.config.CollaborationOrchestrationMode;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.repository.CollabSubtaskRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.collaboration.service.CollaborationExecutor;
import com.adlin.orin.modules.collaboration.service.CollaborationRedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalMcpAgentExecutionService {
    private static final String SOURCE = "external_mcp";

    private final CollaborationPackageRepository packageRepository;
    private final CollabSubtaskRepository subtaskRepository;
    private final CollaborationExecutor collaborationExecutor;
    private final CollaborationOrchestrationMode orchestrationMode;
    private final CollaborationRedisService redisService;
    private final ObjectMapper objectMapper;

    public String execute(AgentMetadata agent, String message, String context, Integer maxTokens, String userId) {
        String packageId = "mcp_" + UUID.randomUUID().toString().replace("-", "");
        String traceId = UUID.randomUUID().toString();
        String path = orchestrationMode.isMqEnabled("SEQUENTIAL") ? "mq" : "fallback";
        try {
            Map<String, Object> sourceMeta = Map.of(
                    "source", SOURCE,
                    "executionPath", path,
                    "agentId", agent.getAgentId(),
                    "userId", userId
            );
            packageRepository.save(CollaborationPackageEntity.builder()
                    .packageId(packageId)
                    .intent(message)
                    .intentCategory("EXTERNAL_MCP")
                    .intentPriority("NORMAL")
                    .intentComplexity("SIMPLE")
                    .collaborationMode("SEQUENTIAL")
                    .strategy(objectMapper.writeValueAsString(Map.of(
                            "mainAgentPolicy", "STATIC_ONLY",
                            "mainAgentStaticDefault", agent.getAgentId()
                    )))
                    .sharedContext(objectMapper.writeValueAsString(sourceMeta))
                    .status("EXECUTING")
                    .traceId(traceId)
                    .createdBy("api-key:" + userId)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build());
            Map<String, Object> input = new java.util.LinkedHashMap<>(sourceMeta);
            input.put("preferred_agent_id", agent.getAgentId());
            input.put("_trace_id", traceId);
            if (maxTokens != null) input.put("agentMaxTokens", maxTokens);
            CollabSubtaskEntity subtask = CollabSubtaskEntity.builder()
                    .packageId(packageId)
                    .subTaskId("mcp_call")
                    .description(context == null || context.isBlank() ? message : context + "\n\n" + message)
                    .expectedRole("SPECIALIST")
                    .inputData(objectMapper.writeValueAsString(input))
                    .status("PENDING")
                    .retryCount(0)
                    .build();
            subtaskRepository.save(subtask);
            redisService.updateContextField(packageId, "source", SOURCE);
            redisService.updateContextField(packageId, "mcp", sourceMeta);
            log.info("External MCP agent call started: packageId={}, agentId={}, source={}, path={}",
                    packageId, agent.getAgentId(), SOURCE, path);
            String result = collaborationExecutor.executeSubtask(subtask, packageId, traceId).get(5, TimeUnit.MINUTES);
            subtaskRepository.findByPackageIdAndSubTaskId(packageId, "mcp_call").ifPresent(done -> {
                try {
                    done.setOutputData(objectMapper.writeValueAsString(sourceMeta));
                    subtaskRepository.save(done);
                } catch (Exception e) {
                    log.warn("Failed to persist external MCP metadata: packageId={}", packageId, e);
                }
            });
            log.info("External MCP agent call completed: packageId={}, source={}, path={}", packageId, SOURCE, path);
            return result;
        } catch (Exception e) {
            log.warn("External MCP agent call failed: packageId={}, source={}, path={}", packageId, SOURCE, path, e);
            throw new IllegalStateException("Agent execution failed: " + e.getMessage(), e);
        }
    }
}
