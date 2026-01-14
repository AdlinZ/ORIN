package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.LogConfig;
import com.adlin.orin.modules.system.repository.LogConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogConfigService {

    private final LogConfigRepository logConfigRepository;

    public static final String KEY_RETENTION_DAYS = "log.retention.days";
    public static final String KEY_LOG_LEVEL = "log.level"; // ALL, AUDIT_ONLY, ERROR_ONLY, NONE
    public static final String KEY_AUDIT_ENABLED = "log.audit.enabled";

    @PostConstruct
    public void init() {
        initDefault(KEY_RETENTION_DAYS, "30", "日志保留天数");
        initDefault(KEY_LOG_LEVEL, "ALL", "日志记录级别 (ALL, AUDIT_ONLY, ERROR_ONLY, NONE)");
        initDefault(KEY_AUDIT_ENABLED, "true", "是否启用审计日志");
    }

    private void initDefault(String key, String value, String desc) {
        if (!logConfigRepository.existsById(key)) {
            logConfigRepository.save(LogConfig.builder()
                    .configKey(key)
                    .configValue(value)
                    .description(desc)
                    .updatedAt(LocalDateTime.now())
                    .build());
            log.info("Initialized default log config: {}", key);
        }
    }

    public List<LogConfig> getAllConfigs() {
        return logConfigRepository.findAll();
    }

    public LogConfig updateConfig(String key, String value) {
        LogConfig config = logConfigRepository.findById(key)
                .orElseThrow(() -> new RuntimeException("Config key not found: " + key));
        config.setConfigValue(value);
        config.setUpdatedAt(LocalDateTime.now());
        return logConfigRepository.save(config);
    }

    public int getRetentionDays() {
        return Integer.parseInt(getConfigValue(KEY_RETENTION_DAYS, "30"));
    }

    public boolean isAuditEnabled() {
        return Boolean.parseBoolean(getConfigValue(KEY_AUDIT_ENABLED, "true"));
    }

    public String getLogLevel() {
        return getConfigValue(KEY_LOG_LEVEL, "ALL");
    }

    private String getConfigValue(String key, String defaultValue) {
        return logConfigRepository.findById(key)
                .map(LogConfig::getConfigValue)
                .orElse(defaultValue);
    }
}
