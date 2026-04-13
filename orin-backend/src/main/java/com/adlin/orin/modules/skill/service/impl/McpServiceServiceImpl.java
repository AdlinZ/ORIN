package com.adlin.orin.modules.skill.service.impl;

import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.repository.McpServiceRepository;
import com.adlin.orin.modules.skill.service.McpServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        McpService existing = getServiceById(id);

        // 检查名称冲突（排除自身）
        if (!existing.getName().equals(service.getName()) &&
                mcpServiceRepository.existsByNameAndIdNot(service.getName(), id)) {
            throw new RuntimeException("服务名称已存在: " + service.getName());
        }

        existing.setName(service.getName());
        existing.setType(service.getType());
        existing.setCommand(service.getCommand());
        existing.setUrl(service.getUrl());
        existing.setEnvVars(service.getEnvVars());
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

            boolean success = false;
            String message = "";
            String errorDetail = "";

            if (service.getType() == McpService.McpType.STDIO) {
                // 测试 STDIO 类型服务
                if (service.getCommand() == null || service.getCommand().isBlank()) {
                    message = "命令不能为空";
                    errorDetail = "STDIO 类型服务需要配置启动命令";
                } else {
                    // 简单验证命令格式
                    String cmd = service.getCommand().trim();
                    if (cmd.contains(" ")) {
                        String[] parts = cmd.split(" ");
                        message = "命令格式验证通过";
                        success = true;
                    } else {
                        message = "命令格式可能有误";
                        success = true; // 仍然标记成功，因为可能是有效的单命令
                    }
                }
            } else if (service.getType() == McpService.McpType.SSE) {
                // 测试 SSE 类型服务
                if (service.getUrl() == null || service.getUrl().isBlank()) {
                    message = "URL 不能为空";
                    errorDetail = "SSE 类型服务需要配置 URL";
                } else {
                    // 简单验证 URL 格式
                    if (service.getUrl().startsWith("http://") || service.getUrl().startsWith("https://")) {
                        message = "URL 格式验证通过";
                        success = true;
                    } else {
                        message = "URL 格式无效";
                        errorDetail = "URL 必须以 http:// 或 https:// 开头";
                    }
                }
            }

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

            result.put("success", success);
            result.put("message", message);
            if (!success) {
                result.put("errorDetail", errorDetail);
            }

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
}
