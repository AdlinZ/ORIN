package com.adlin.orin.modules.settings.service;

import com.adlin.orin.modules.system.entity.SystemConfigEntity;
import com.adlin.orin.modules.system.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 系统级 Dify 配置读取服务
 * 供同步服务注入，统一从 sys_system_config 表读取 Dify 连接信息
 */
@Service
@RequiredArgsConstructor
public class SystemDifyConfigProvider {

    private static final String API_URL_KEY = "dify.apiUrl";
    private static final String API_KEY_KEY  = "dify.apiKey";
    private static final String ENABLED_KEY  = "dify.enabled";

    private final SystemConfigRepository systemConfigRepository;

    public String getApiUrl() {
        return getValue(API_URL_KEY, "");
    }

    public String getApiKey() {
        return getValue(API_KEY_KEY, "");
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(getValue(ENABLED_KEY, "false"));
    }

    /** 返回 true 表示 apiUrl 和 apiKey 均已配置 */
    public boolean isConfigured() {
        return StringUtils.hasText(getApiUrl()) && StringUtils.hasText(getApiKey());
    }

    /**
     * 返回 true 表示 Dify 已启用且配置完整
     */
    public boolean isActive() {
        return isEnabled() && isConfigured();
    }

    private String getValue(String key, String defaultValue) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfigEntity::getConfigValue)
                .orElse(defaultValue);
    }
}
