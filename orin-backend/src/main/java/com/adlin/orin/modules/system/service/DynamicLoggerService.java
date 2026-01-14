package com.adlin.orin.modules.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 动态日志级别管理服务
 * 支持运行时调整日志级别，无需重启应用
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicLoggerService {

    private final LoggingSystem loggingSystem;

    /**
     * 获取所有已配置的 Logger 及其级别
     */
    public Map<String, String> getAllLoggers() {
        Map<String, String> loggers = new LinkedHashMap<>();

        // 获取常见的包路径
        String[] commonPackages = {
                "com.adlin.orin",
                "org.springframework",
                "org.hibernate",
                "com.zaxxer.hikari",
                "ROOT"
        };

        for (String packageName : commonPackages) {
            LogLevel level = loggingSystem.getLoggerConfiguration(packageName).getEffectiveLevel();
            loggers.put(packageName, level != null ? level.name() : "NOT_SET");
        }

        return loggers;
    }

    /**
     * 获取特定 Logger 的配置
     */
    public Map<String, Object> getLoggerConfiguration(String loggerName) {
        var config = loggingSystem.getLoggerConfiguration(loggerName);

        if (config == null) {
            throw new IllegalArgumentException("Logger not found: " + loggerName);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("name", loggerName);
        result.put("configuredLevel", config.getConfiguredLevel() != null ? config.getConfiguredLevel().name() : null);
        result.put("effectiveLevel",
                config.getEffectiveLevel() != null ? config.getEffectiveLevel().name() : "INHERITED");

        return result;
    }

    /**
     * 设置 Logger 级别
     */
    public void setLogLevel(String loggerName, String level) {
        LogLevel logLevel;

        try {
            logLevel = level.equalsIgnoreCase("NULL") ? null : LogLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid log level: " + level +
                    ". Valid values are: TRACE, DEBUG, INFO, WARN, ERROR, OFF, NULL");
        }

        loggingSystem.setLogLevel(loggerName, logLevel);

        log.info("Logger '{}' level changed to: {}", loggerName, level);
    }

    /**
     * 重置 Logger 到默认级别
     */
    public void resetLogger(String loggerName) {
        loggingSystem.setLogLevel(loggerName, null);
        log.info("Logger '{}' reset to default level", loggerName);
    }

    /**
     * 批量设置日志级别
     */
    public void batchSetLogLevel(Map<String, String> loggerLevels) {
        loggerLevels.forEach(this::setLogLevel);
        log.info("Batch updated {} loggers", loggerLevels.size());
    }

    /**
     * 重置所有日志级别到默认
     */
    public void resetAllLoggers() {
        String[] commonPackages = {
                "com.adlin.orin",
                "org.springframework",
                "org.hibernate",
                "com.zaxxer.hikari"
        };

        for (String packageName : commonPackages) {
            loggingSystem.setLogLevel(packageName, null);
        }

        log.info("All loggers reset to default levels");
    }

    /**
     * 获取支持的日志级别列表
     */
    public List<String> getSupportedLevels() {
        return Arrays.stream(LogLevel.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    /**
     * 临时调整日志级别（用于调试）
     * 
     * @param loggerName      Logger 名称
     * @param level           日志级别
     * @param durationSeconds 持续时间（秒）
     */
    public void temporarySetLogLevel(String loggerName, String level, int durationSeconds) {
        var originalConfig = loggingSystem.getLoggerConfiguration(loggerName);
        LogLevel originalLevel = originalConfig.getConfiguredLevel();

        // 设置临时级别
        setLogLevel(loggerName, level);

        // 定时器恢复原级别
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                loggingSystem.setLogLevel(loggerName, originalLevel);
                log.info("Logger '{}' restored to original level: {}",
                        loggerName, originalLevel != null ? originalLevel.name() : "DEFAULT");
            }
        }, durationSeconds * 1000L);

        log.info("Logger '{}' temporarily set to {} for {} seconds",
                loggerName, level, durationSeconds);
    }
}
