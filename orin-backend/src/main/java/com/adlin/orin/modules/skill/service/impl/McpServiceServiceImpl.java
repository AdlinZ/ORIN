package com.adlin.orin.modules.skill.service.impl;

import com.adlin.orin.common.exception.ValidationException;
import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.repository.McpServiceRepository;
import com.adlin.orin.modules.skill.service.McpServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServiceServiceImpl implements McpServiceService {

    private final McpServiceRepository mcpServiceRepository;
    private static final List<String> SENSITIVE_ENV_SUFFIXES = List.of("_KEY", "_TOKEN", "_SECRET");

    @Value("${orin.ai-engine.url:http://localhost:8000}")
    private String aiEngineUrl;

    @Override
    public List<McpService> getAllServices() {
        return mcpServiceRepository.findAll();
    }

    @Override
    public McpService getServiceById(Long id) {
        return mcpServiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("MCP 服务不存在: " + id));
    }

    @Override
    @Transactional
    public McpService createService(McpService service) {
        validateEnvVars(service.getEnvVars());
        if (mcpServiceRepository.existsByName(service.getName())) {
            throw new RuntimeException("服务名称已存在: " + service.getName());
        }
        if (service.getEnabled() == null) {
            service.setEnabled(true);
        }
        service.setStatus(McpService.McpStatus.DISCONNECTED);
        service.setHealthScore(100);
        return mcpServiceRepository.save(service);
    }

    @Override
    @Transactional
    public McpService updateService(Long id, McpService service) {
        validateEnvVars(service.getEnvVars());
        McpService existing = mcpServiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("MCP 服务不存在: " + id));

        // 检查名称冲突（排除自身）
        if (!existing.getName().equals(service.getName()) &&
                mcpServiceRepository.existsByNameAndIdNot(service.getName(), id)) {
            throw new RuntimeException("服务名称已存在: " + service.getName());
        }

        existing.setName(service.getName());
        existing.setType(service.getType());
        existing.setCommand(service.getCommand());
        existing.setUrl(service.getUrl());
        if (service.getEnvVars() == null || !service.getEnvVars().contains("******")) {
            existing.setEnvVars(service.getEnvVars());
        }
        existing.setDescription(service.getDescription());
        existing.setToolKey(service.getToolKey());
        existing.setEnabled(service.getEnabled() != null ? service.getEnabled() : existing.getEnabled());

        return mcpServiceRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteService(Long id) {
        McpService service = getServiceById(id);
        mcpServiceRepository.delete(service);
    }

    @Override
    @Transactional
    public Map<String, Object> testConnection(Long id) {
        McpService service = getServiceById(id);
        Map<String, Object> result = new HashMap<>();

        try {
            if (!Boolean.TRUE.equals(service.getEnabled())) {
                service.setStatus(McpService.McpStatus.DISCONNECTED);
                mcpServiceRepository.save(service);
                result.put("success", false);
                result.put("message", "服务已禁用");
                result.put("errorDetail", "请先启用服务后再测试连接");
                return result;
            }

            // 标记为测试中
            service.setStatus(McpService.McpStatus.TESTING);
            mcpServiceRepository.save(service);

            if (service.getType() == McpService.McpType.STDIO || service.getType() == McpService.McpType.SSE) {
                result = testViaAiEngine(service);
            } else {
                result.put("success", false);
                result.put("message", "不支持的 MCP 服务类型");
                result.put("reasonCode", "UNSUPPORTED_TYPE");
                result.put("errorDetail", String.valueOf(service.getType()));
            }

            boolean success = Boolean.TRUE.equals(result.get("success"));
            String errorDetail = (String) result.getOrDefault("errorDetail", "");

            // 更新服务状态
            if (success) {
                service.setStatus(McpService.McpStatus.CONNECTED);
                service.setLastConnected(LocalDateTime.now());
                service.setLastError(null);
                service.setHealthScore(100);
            } else {
                service.setStatus(McpService.McpStatus.ERROR);
                service.setLastError(errorDetail);
                service.setHealthScore(0);
            }

            mcpServiceRepository.save(service);

        } catch (Exception e) {
            log.error("测试连接失败: {}", e.getMessage(), e);
            service.setStatus(McpService.McpStatus.ERROR);
            service.setLastError(e.getMessage());
            service.setHealthScore(0);
            mcpServiceRepository.save(service);

            result.put("success", false);
            result.put("message", "连接测试失败");
            result.put("errorDetail", e.getMessage());
        }

        return result;
    }

    private Map<String, Object> testViaAiEngine(McpService service) {
        Map<String, Object> result = new HashMap<>();
        if (service.getType() == McpService.McpType.STDIO
                && (service.getCommand() == null || service.getCommand().isBlank())) {
            result.put("success", false);
            result.put("message", "命令不能为空");
            result.put("reasonCode", "CONFIG_MISSING");
            result.put("errorDetail", "STDIO 类型服务需要配置启动命令");
            return result;
        }
        if (service.getType() == McpService.McpType.SSE
                && (service.getUrl() == null || service.getUrl().isBlank())) {
            result.put("success", false);
            result.put("message", "URL 不能为空");
            result.put("reasonCode", "CONFIG_MISSING");
            result.put("errorDetail", "SSE 类型服务需要配置 URL");
            return result;
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(3))
                    .build();
            String endpoint = aiEngineUrl.replaceAll("/+$", "") + "/api/mcp/services/" + service.getId() + "/tools";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                result.put("success", false);
                result.put("message", "AI Engine MCP 连接测试失败");
                result.put("reasonCode", response.statusCode() == 502 ? "START_FAILED" : "AI_ENGINE_UNAVAILABLE");
                result.put("httpStatus", response.statusCode());
                result.put("errorDetail", truncate(response.body()));
                return result;
            }

            int toolCount = countToolNames(response.body());
            if (toolCount <= 0) {
                result.put("success", false);
                result.put("message", "工具列表为空");
                result.put("reasonCode", "TOOL_LIST_EMPTY");
                result.put("errorDetail", truncate(response.body()));
                return result;
            }

            result.put("success", true);
            result.put("message", "MCP 协议握手通过");
            result.put("reasonCode", "CONNECTED");
            result.put("toolCount", toolCount);
            return result;
        } catch (java.net.http.HttpTimeoutException e) {
            result.put("success", false);
            result.put("message", "MCP 响应超时");
            result.put("reasonCode", "TIMEOUT");
            result.put("errorDetail", e.getMessage());
            return result;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接测试失败");
            result.put("reasonCode", "AI_ENGINE_UNAVAILABLE");
            result.put("errorDetail", e.getMessage());
            return result;
        }
    }

    private int countToolNames(String toolsResponse) {
        if (!toolsResponse.contains("\"tools\"")) {
            return 0;
        }
        return Math.max(0, toolsResponse.split("\"name\"\\s*:").length - 1);
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    @Override
    public List<McpService> searchServices(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllServices();
        }
        return mcpServiceRepository.findByNameContaining(keyword);
    }

    @Override
    public List<McpService> getServicesByType(McpService.McpType type) {
        return mcpServiceRepository.findByType(type);
    }

    @Override
    @Transactional
    public McpService setServiceEnabled(Long id, boolean enabled) {
        McpService service = getServiceById(id);
        service.setEnabled(enabled);
        if (!enabled) {
            service.setStatus(McpService.McpStatus.DISCONNECTED);
            service.setLastError(null);
        }
        return mcpServiceRepository.save(service);
    }

    private void validateEnvVars(String envVars) {
        if (envVars == null || envVars.isBlank()) {
            return;
        }
        for (String line : envVars.split("\\R")) {
            if (line == null || line.isBlank() || !line.contains("=")) {
                continue;
            }
            String key = line.substring(0, line.indexOf('=')).trim().toUpperCase();
            if (SENSITIVE_ENV_SUFFIXES.stream().anyMatch(key::endsWith)) {
                throw new ValidationException("MCP 环境变量禁止包含敏感字段: " + key);
            }
        }
    }

}
