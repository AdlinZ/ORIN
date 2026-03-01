package com.adlin.orin.modules.zeroclaw.service;

import com.adlin.orin.modules.monitor.entity.AgentMetric;
import com.adlin.orin.modules.monitor.repository.AgentMetricRepository;
import com.adlin.orin.modules.model.entity.ModelConfig;
import com.adlin.orin.modules.model.service.ModelConfigService;
import com.adlin.orin.modules.zeroclaw.client.ZeroClawClient;
import com.adlin.orin.modules.zeroclaw.dto.ZeroClawAiConfigRequest;
import com.adlin.orin.modules.zeroclaw.dto.ZeroClawAnalysisRequest;
import com.adlin.orin.modules.zeroclaw.dto.ZeroClawSelfHealingRequest;
import com.adlin.orin.modules.zeroclaw.entity.ZeroClawAnalysisReport;
import com.adlin.orin.modules.zeroclaw.entity.ZeroClawConfig;
import com.adlin.orin.modules.zeroclaw.entity.ZeroClawSelfHealingLog;
import com.adlin.orin.modules.zeroclaw.repository.ZeroClawAnalysisReportRepository;
import com.adlin.orin.modules.zeroclaw.repository.ZeroClawConfigRepository;
import com.adlin.orin.modules.zeroclaw.repository.ZeroClawSelfHealingLogRepository;
import com.adlin.orin.security.EncryptionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * ZeroClaw 服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ZeroClawServiceImpl implements ZeroClawService {

    private final ZeroClawConfigRepository configRepository;
    private final ZeroClawAnalysisReportRepository analysisReportRepository;
    private final ZeroClawSelfHealingLogRepository selfHealingLogRepository;
    private final ZeroClawClient zeroClawClient;
    private final AgentMetricRepository agentMetricRepository;
    private final ObjectMapper objectMapper;
    private final EncryptionUtil encryptionUtil;
    private final ModelConfigService modelConfigService;

    @Override
    @Transactional
    public ZeroClawConfig createConfig(ZeroClawConfig config) {
        // Skip SSRF validation for ZeroClaw local service
        // ZeroClaw is a local lightweight agent, so localhost connections are expected and safe
        // Encrypt access token before saving
        if (config.getAccessToken() != null && !config.getAccessToken().isBlank()) {
            config.setAccessToken(encryptionUtil.encrypt(config.getAccessToken()));
        }
        // Set agentId if provided (handle blank string)
        if (config.getAgentId() != null && config.getAgentId().isBlank()) {
            config.setAgentId(null);
        }
        ZeroClawConfig saved = configRepository.save(config);
        log.info("Created ZeroClaw config: {}", saved.getConfigName());
        return saved;
    }

    @Override
    @Transactional
    public ZeroClawConfig updateConfig(String id, ZeroClawConfig config) {
        ZeroClawConfig existing = configRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ZeroClaw config not found: " + id));

        existing.setConfigName(config.getConfigName());

        // Skip SSRF validation for ZeroClaw local service
        existing.setEndpointUrl(config.getEndpointUrl());
        // Encrypt access token before saving
        if (config.getAccessToken() != null && !config.getAccessToken().isBlank()) {
            existing.setAccessToken(encryptionUtil.encrypt(config.getAccessToken()));
        }
        existing.setEnabled(config.getEnabled());
        existing.setEnableAnalysis(config.getEnableAnalysis());
        existing.setEnableSelfHealing(config.getEnableSelfHealing());
        existing.setHeartbeatInterval(config.getHeartbeatInterval());
        // Handle agentId (handle blank string as null)
        if (config.getAgentId() != null && config.getAgentId().isBlank()) {
            existing.setAgentId(null);
        } else {
            existing.setAgentId(config.getAgentId());
        }

        ZeroClawConfig saved = configRepository.save(existing);
        log.info("Updated ZeroClaw config: {}", saved.getConfigName());
        return saved;
    }

    @Override
    @Transactional
    public void deleteConfig(String id) {
        configRepository.deleteById(id);
        log.info("Deleted ZeroClaw config: {}", id);
    }

    @Override
    public List<ZeroClawConfig> getAllConfigs() {
        return configRepository.findAll();
    }

    @Override
    public ZeroClawConfig getActiveConfig() {
        return configRepository.findFirstByEnabledTrue()
                .orElse(null);
    }

    @Override
    public boolean testConnection(String endpointUrl, String accessToken) {
        // Skip SSRF validation for ZeroClaw local service
        // ZeroClaw is a local lightweight agent, so localhost connections are expected and safe
        return zeroClawClient.testConnection(endpointUrl, accessToken);
    }

    @Override
    @Transactional
    public ZeroClawAnalysisReport performAnalysis(ZeroClawAnalysisRequest request) {
        ZeroClawConfig config = getActiveConfig();
        if (config == null || !Boolean.TRUE.equals(config.getEnableAnalysis())) {
            log.warn("ZeroClaw analysis is not enabled or no active config");
            return null;
        }

        LocalDateTime startTime = LocalDateTime.now();

        // 收集监控数据
        Map<String, Object> metricsData = collectMetricsData(request);

        // 调用 ZeroClaw 进行分析
        Map<String, Object> result = zeroClawClient.requestAnalysis(
                config.getEndpointUrl(),
                decryptAccessToken(config.getAccessToken()),
                request.getAnalysisType(),
                metricsData);

        if (result == null) {
            log.error("ZeroClaw analysis returned null");
            return null;
        }

        // 保存分析报告
        ZeroClawAnalysisReport report = ZeroClawAnalysisReport.builder()
                .agentId(request.getAgentId())
                .reportType(request.getAnalysisType())
                .title((String) result.getOrDefault("title", "Analysis Report"))
                .summary((String) result.getOrDefault("summary", ""))
                .rootCause((String) result.getOrDefault("rootCause", ""))
                .recommendations((String) result.getOrDefault("recommendations", ""))
                .severity((String) result.getOrDefault("severity", "INFO"))
                .analysisStart(startTime)
                .analysisEnd(LocalDateTime.now())
                .dataStartTime(request.getStartTime())
                .dataEndTime(request.getEndTime())
                .build();

        try {
            report.setDetails(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            report.setDetails(result.toString());
        }

        ZeroClawAnalysisReport saved = analysisReportRepository.save(report);
        log.info("ZeroClaw analysis completed: {}", saved.getId());
        return saved;
    }

    @Override
    public Page<ZeroClawAnalysisReport> getAnalysisReports(Pageable pageable) {
        return analysisReportRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Override
    public List<ZeroClawAnalysisReport> getAnalysisReportsByAgent(String agentId) {
        return analysisReportRepository.findByAgentIdOrderByCreatedAtDesc(agentId);
    }

    @Override
    @Transactional
    public ZeroClawSelfHealingLog executeSelfHealing(ZeroClawSelfHealingRequest request) {
        ZeroClawConfig config = getActiveConfig();
        if (config == null || !Boolean.TRUE.equals(config.getEnableSelfHealing())) {
            log.warn("ZeroClaw self-healing is not enabled or no active config");
            return null;
        }

        // 创建操作记录
        ZeroClawSelfHealingLog log = ZeroClawSelfHealingLog.builder()
                .actionType(request.getActionType())
                .targetResource(request.getTargetResource())
                .triggerReason(request.getReason())
                .status("RUNNING")
                .startedAt(LocalDateTime.now())
                .autoExecuted(!Boolean.TRUE.equals(request.getForceExecute()))
                .build();

        log = selfHealingLogRepository.save(log);

        // 执行前快照
        Map<String, Object> beforeSnapshot = captureSystemSnapshot();
        try {
            log.setBeforeSnapshot(objectMapper.writeValueAsString(beforeSnapshot));
        } catch (JsonProcessingException e) {
            log.setBeforeSnapshot(beforeSnapshot.toString());
        }

        // 调用 ZeroClaw 执行操作
        Map<String, Object> params = new HashMap<>();
        params.put("target", request.getTargetResource());
        params.put("force", request.getForceExecute());

        Map<String, Object> result = zeroClawClient.requestSelfHealing(
                config.getEndpointUrl(),
                decryptAccessToken(config.getAccessToken()),
                request.getActionType(),
                params);

        // 更新操作结果
        if (result != null && Boolean.TRUE.equals(result.get("success"))) {
            log.setStatus("SUCCESS");
            try {
                log.setExecutionDetails(objectMapper.writeValueAsString(result));
            } catch (JsonProcessingException e) {
                log.setExecutionDetails(result.toString());
            }
        } else {
            log.setStatus("FAILED");
            log.setErrorMessage(result != null ? (String) result.get("error") : "Unknown error");
        }

        // 执行后快照
        Map<String, Object> afterSnapshot = captureSystemSnapshot();
        try {
            log.setAfterSnapshot(objectMapper.writeValueAsString(afterSnapshot));
        } catch (JsonProcessingException e) {
            log.setAfterSnapshot(afterSnapshot.toString());
        }

        log.setCompletedAt(LocalDateTime.now());
        ZeroClawSelfHealingLog saved = selfHealingLogRepository.save(log);
        ZeroClawServiceImpl.log.info("ZeroClaw self-healing action completed: {} - {}", saved.getActionType(),
                saved.getStatus());
        return saved;
    }

    @Override
    public Page<ZeroClawSelfHealingLog> getSelfHealingLogs(Pageable pageable) {
        return selfHealingLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Override
    public Map<String, Object> getZeroClawStatus() {
        ZeroClawConfig config = getActiveConfig();
        if (config == null) {
            Map<String, Object> status = new HashMap<>();
            status.put("connected", false);
            status.put("message", "No active ZeroClaw configuration");
            return status;
        }

        boolean connected = zeroClawClient.testConnection(config.getEndpointUrl(), decryptAccessToken(config.getAccessToken()));
        Map<String, Object> status = new HashMap<>();
        status.put("connected", connected);
        status.put("configName", config.getConfigName());
        status.put("enabled", config.getEnabled());
        status.put("analysisEnabled", config.getEnableAnalysis());
        status.put("selfHealingEnabled", config.getEnableSelfHealing());

        if (connected) {
            Map<String, Object> remoteStatus = zeroClawClient.getStatus(config.getEndpointUrl(),
                    decryptAccessToken(config.getAccessToken()));
            if (remoteStatus != null) {
                status.putAll(remoteStatus);
            }
        }

        return status;
    }

    @Override
    @Transactional
    public ZeroClawAnalysisReport generateDailyTrendReport() {
        long endTime = System.currentTimeMillis();
        long startTime = endTime - (24 * 60 * 60 * 1000); // 24 hours ago

        ZeroClawAnalysisRequest request = new ZeroClawAnalysisRequest();
        request.setAnalysisType("TREND_FORECAST");
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setContext("Generate 24-hour trend analysis report");

        return performAnalysis(request);
    }

    @Override
    public Map<String, Object> chat(String agentId, List<Map<String, String>> messages, String systemPrompt) {
        ZeroClawConfig config = getActiveConfig();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            log.warn("ZeroClaw is not enabled or no active config");
            return null;
        }

        String endpointUrl = config.getEndpointUrl();
        String accessToken = config.getAccessToken();

        // Decrypt access token if exists
        if (accessToken != null && !accessToken.isEmpty()) {
            accessToken = decryptAccessToken(accessToken);
        }

        log.info("Sending chat request to ZeroClaw for agent: {}", agentId);

        Map<String, Object> response = zeroClawClient.chat(endpointUrl, accessToken, messages, systemPrompt);

        if (response != null) {
            log.info("ZeroClaw chat response received successfully");
        } else {
            log.warn("ZeroClaw chat request failed");
        }

        return response;
    }

    /**
     * 收集监控数据
     */
    private Map<String, Object> collectMetricsData(ZeroClawAnalysisRequest request) {
        Map<String, Object> data = new HashMap<>();

        Long startTime = request.getStartTime();
        Long endTime = request.getEndTime();

        if (startTime == null) {
            startTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        }
        if (endTime == null) {
            endTime = System.currentTimeMillis();
        }

        // 获取指定时间范围内的指标数据
        List<AgentMetric> metrics;
        if (request.getAgentId() != null && !request.getAgentId().isEmpty()) {
            metrics = agentMetricRepository.findByAgentIdAndTimestampBetween(
                    request.getAgentId(), startTime, endTime);
            data.put("targetAgentId", request.getAgentId());
        } else {
            metrics = agentMetricRepository.findByTimestampBetween(startTime, endTime);
        }

        data.put("metrics", metrics);
        data.put("timeRange", Map.of("start", startTime, "end", endTime));
        data.put("totalRecords", metrics.size());
        data.put("analysisType", request.getAnalysisType());
        data.put("context", request.getContext());

        return data;
    }

    /**
     * 解密 access token
     */
    private String decryptAccessToken(String encryptedToken) {
        if (encryptedToken == null || encryptedToken.isBlank()) {
            return null;
        }
        return encryptionUtil.decrypt(encryptedToken);
    }

    /**
     * 捕获系统快照
     */
    private Map<String, Object> captureSystemSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();

        Runtime runtime = Runtime.getRuntime();
        snapshot.put("timestamp", System.currentTimeMillis());
        snapshot.put("memory", Map.of(
                "total", runtime.totalMemory(),
                "free", runtime.freeMemory(),
                "max", runtime.maxMemory(),
                "used", runtime.totalMemory() - runtime.freeMemory()));
        snapshot.put("processors", runtime.availableProcessors());

        return snapshot;
    }

    @Override
    public Map<String, Object> configureAi(ZeroClawAiConfigRequest request) {
        ZeroClawConfig config = getActiveConfig();
        if (config == null) {
            return Map.of("success", false, "message", "No active ZeroClaw configuration");
        }

        String endpointUrl = config.getEndpointUrl();

        // 如果不是自定义配置，从 ModelConfig 获取 API Key
        if (request.getApiKey() == null || request.getApiKey().isBlank()) {
            ModelConfig modelConfig = modelConfigService.getConfig();
            if (modelConfig != null) {
                String provider = request.getProvider();
                if ("deepseek".equals(provider)) {
                    // DeepSeek 使用 siliconFlow 相关配置
                    request.setBaseUrl("https://api.deepseek.com");
                    request.setModel("deepseek-chat");
                    // DeepSeek API Key 需要用户配置（ModelConfig 中没有存储）
                    if (modelConfig.getSiliconFlowApiKey() != null && !modelConfig.getSiliconFlowApiKey().isBlank()) {
                        request.setApiKey(modelConfig.getSiliconFlowApiKey());
                    }
                } else if ("siliconflow".equals(provider)) {
                    request.setBaseUrl(modelConfig.getSiliconFlowEndpoint() != null ?
                            modelConfig.getSiliconFlowEndpoint() : "https://api.siliconflow.cn/v1");
                    request.setModel(modelConfig.getSiliconFlowModel() != null ?
                            modelConfig.getSiliconFlowModel() : "Qwen/Qwen2-7B-Instruct");
                    request.setApiKey(modelConfig.getSiliconFlowApiKey());
                } else if ("ollama".equals(provider)) {
                    request.setBaseUrl(modelConfig.getOllamaEndpoint() != null ?
                            modelConfig.getOllamaEndpoint() + "/v1" : "http://localhost:11434/v1");
                    request.setModel(modelConfig.getOllamaModel() != null ?
                            modelConfig.getOllamaModel() : "llama3");
                    request.setApiKey(modelConfig.getOllamaApiKey());
                }
            }
        }

        // 如果仍然没有 API Key，返回错误
        if (request.getApiKey() == null || request.getApiKey().isBlank()) {
            return Map.of("success", false, "message",
                    "请在 ModelConfig 中配置对应的 API Key，或选择自定义配置并输入 API Key");
        }

        try {
            // 调用 ZeroClaw 服务器的 AI 配置端点
            Map<String, Object> result = zeroClawClient.configureAi(endpointUrl,
                    decryptAccessToken(config.getAccessToken()), request);
            if (result != null && Boolean.TRUE.equals(result.get("success"))) {
                log.info("AI configuration updated successfully for ZeroClaw");
                return Map.of("success", true, "message", "AI configuration updated",
                        "provider", result.getOrDefault("provider", request.getProvider()),
                        "model", result.getOrDefault("model", request.getModel()));
            } else {
                return Map.of("success", false, "message", result != null ? result.get("message") : "Configuration failed");
            }
        } catch (Exception e) {
            log.error("Failed to configure AI for ZeroClaw: {}", e.getMessage());
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getAiConfig() {
        ZeroClawConfig config = getActiveConfig();
        if (config == null) {
            return Map.of("success", false, "message", "No active ZeroClaw configuration");
        }

        String endpointUrl = config.getEndpointUrl();
        try {
            Map<String, Object> result = zeroClawClient.getAiConfig(endpointUrl,
                    decryptAccessToken(config.getAccessToken()));
            if (result != null) {
                result.put("success", true);
                return result;
            }
        } catch (Exception e) {
            log.error("Failed to get AI config from ZeroClaw: {}", e.getMessage());
        }
        return Map.of("success", false, "message", "Failed to get AI configuration");
    }
}
