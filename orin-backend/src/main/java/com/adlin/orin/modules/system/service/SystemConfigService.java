package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.SystemConfigEntity;
import com.adlin.orin.modules.system.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 系统配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    // 默认配置值
    private static final Map<String, String> DEFAULT_CONFIG = new HashMap<>();

    static {
        DEFAULT_CONFIG.put("systemName", "ORIN 系统");
        DEFAULT_CONFIG.put("systemDescription", "高级 Agent 管理系统");
        DEFAULT_CONFIG.put("sessionRetentionDays", "30");
        DEFAULT_CONFIG.put("maxConcurrentRequests", "100");
        DEFAULT_CONFIG.put("auditLogEnabled", "true");
        DEFAULT_CONFIG.put("auditLogRetentionDays", "90");
    }

    /**
     * 获取系统基础配置
     */
    public Map<String, Object> getConfig() {
        Map<String, Object> result = new HashMap<>();

        for (String key : DEFAULT_CONFIG.keySet()) {
            Optional<SystemConfigEntity> entity = systemConfigRepository.findByConfigKey(key);
            if (entity.isPresent()) {
                String value = entity.get().getConfigValue();
                // 根据键名转换类型
                result.put(key, convertValue(key, value));
            } else {
                // 使用默认值
                result.put(key, convertValue(key, DEFAULT_CONFIG.get(key)));
            }
        }

        return result;
    }

    /**
     * 保存系统基础配置
     */
    @Transactional
    public Map<String, Object> saveConfig(Map<String, Object> config) {
        // 校验允许的键
        Set<String> allowedKeys = new HashSet<>(DEFAULT_CONFIG.keySet());
        allowedKeys.addAll(Set.of(
            "smtpHost", "smtpPort", "smtpUsername", "smtpPassword", "smtpFrom",
            "defaultModel", "embeddingModel", "rerankModel"
        ));

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            String value = String.valueOf(entry.getValue());

            // 校验键是否允许
            if (!allowedKeys.contains(key)) {
                log.warn("忽略未知配置键: {}", key);
                continue;
            }

            // 基础校验
            validateValue(key, value);

            Optional<SystemConfigEntity> existing = systemConfigRepository.findByConfigKey(key);

            if (existing.isPresent()) {
                SystemConfigEntity entity = existing.get();
                entity.setConfigValue(value);
                systemConfigRepository.save(entity);
                log.info("更新系统配置: {} = {}", key, value);
            } else {
                SystemConfigEntity entity = new SystemConfigEntity();
                entity.setConfigKey(key);
                entity.setConfigValue(value);
                entity.setDescription(getConfigDescription(key));
                systemConfigRepository.save(entity);
                log.info("创建系统配置: {} = {}", key, value);
            }
        }

        return getConfig();
    }

    /**
     * 校验配置值
     */
    private void validateValue(String key, String value) {
        switch (key) {
            case "sessionRetentionDays":
            case "auditLogRetentionDays":
                int days = Integer.parseInt(value);
                if (days < 1 || days > 365) {
                    throw new IllegalArgumentException(key + " 必须介于 1-365 天之间");
                }
                break;
            case "maxConcurrentRequests":
                int maxReq = Integer.parseInt(value);
                if (maxReq < 1 || maxReq > 10000) {
                    throw new IllegalArgumentException(key + " 必须介于 1-10000 之间");
                }
                break;
            case "smtpPort":
                int port = Integer.parseInt(value);
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("SMTP 端口必须介于 1-65535 之间");
                }
                break;
            case "systemName":
                if (value == null || value.trim().isEmpty()) {
                    throw new IllegalArgumentException("系统名称不能为空");
                }
                if (value.length() > 100) {
                    throw new IllegalArgumentException("系统名称不能超过 100 字符");
                }
                break;
        }
    }

    /**
     * 根据键获取配置描述
     */
    private String getConfigDescription(String key) {
        switch (key) {
            case "systemName": return "系统名称";
            case "systemDescription": return "系统描述";
            case "sessionRetentionDays": return "会话保留天数";
            case "maxConcurrentRequests": return "最大并发请求数";
            case "auditLogEnabled": return "审计日志启用";
            case "auditLogRetentionDays": return "审计日志保留天数";
            default: return "";
        }
    }

    /**
     * 转换配置值为合适类型
     */
    private Object convertValue(String key, String value) {
        switch (key) {
            case "sessionRetentionDays":
            case "maxConcurrentRequests":
            case "auditLogRetentionDays":
                return Integer.parseInt(value);
            case "auditLogEnabled":
                return Boolean.parseBoolean(value);
            default:
                return value;
        }
    }
}