package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.entity.LogConfig;
import com.adlin.orin.modules.system.service.DynamicLoggerService;
import com.adlin.orin.modules.system.service.LogConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/log-config")
@RequiredArgsConstructor
@Tag(name = "Phase 5: Log Configuration", description = "日志配置管理")
@CrossOrigin(origins = "*")
public class LogConfigController {

    private final LogConfigService logConfigService;
    private final DynamicLoggerService dynamicLoggerService;
    private final com.adlin.orin.modules.audit.service.AuditLogService auditLogService;

    @Operation(summary = "获取所有日志配置")
    @GetMapping
    public List<LogConfig> getAllConfigs() {
        return logConfigService.getAllConfigs();
    }

    @Operation(summary = "获取日志运行指标")
    @GetMapping("/stats")
    public java.util.Map<String, Object> getLogStats() {
        return auditLogService.getLogStats();
    }

    @Operation(summary = "手动清理日志")
    @PostMapping("/cleanup")
    public Map<String, Object> manualCleanup(@RequestParam(defaultValue = "0") int days) {
        int deletedCount = auditLogService.manualCleanup(days);
        return Map.of(
                "success", true,
                "deletedCount", deletedCount,
                "days", days,
                "message", String.format("Successfully deleted %d log records older than %d days", deletedCount, days));
    }

    @Operation(summary = "更新日志配置")
    @PutMapping("/{key}")
    public LogConfig updateConfig(@PathVariable String key, @RequestBody Map<String, String> payload) {
        String value = payload.get("value");
        if (value == null) {
            throw new IllegalArgumentException("Payload must contain 'value'");
        }
        return logConfigService.updateConfig(key, value);
    }

    // ==================== 动态日志级别管理 API ====================

    @Operation(summary = "获取所有 Logger 及其级别")
    @GetMapping("/loggers")
    public Map<String, String> getAllLoggers() {
        return dynamicLoggerService.getAllLoggers();
    }

    @Operation(summary = "获取指定 Logger 的配置")
    @GetMapping("/loggers/{loggerName}")
    public Map<String, Object> getLogger(@PathVariable String loggerName) {
        return dynamicLoggerService.getLoggerConfiguration(loggerName);
    }

    @Operation(summary = "设置 Logger 日志级别")
    @PutMapping("/loggers/{loggerName}")
    public Map<String, String> setLogLevel(
            @PathVariable String loggerName,
            @RequestBody Map<String, String> payload) {
        String level = payload.get("level");
        if (level == null) {
            throw new IllegalArgumentException("Payload must contain 'level' (TRACE, DEBUG, INFO, WARN, ERROR, OFF)");
        }

        dynamicLoggerService.setLogLevel(loggerName, level);

        return Map.of(
                "logger", loggerName,
                "level", level,
                "status", "success");
    }

    @Operation(summary = "重置 Logger 到默认级别")
    @DeleteMapping("/loggers/{loggerName}")
    public Map<String, String> resetLogger(@PathVariable String loggerName) {
        dynamicLoggerService.resetLogger(loggerName);

        return Map.of(
                "logger", loggerName,
                "status", "reset to default");
    }

    @Operation(summary = "批量设置日志级别")
    @PostMapping("/loggers/batch")
    public Map<String, Object> batchSetLogLevel(@RequestBody Map<String, String> loggerLevels) {
        dynamicLoggerService.batchSetLogLevel(loggerLevels);

        return Map.of(
                "updated", loggerLevels.size(),
                "status", "success");
    }

    @Operation(summary = "重置所有 Logger 到默认级别")
    @PostMapping("/loggers/reset-all")
    public Map<String, String> resetAllLoggers() {
        dynamicLoggerService.resetAllLoggers();

        return Map.of("status", "all loggers reset to default");
    }

    @Operation(summary = "获取支持的日志级别列表")
    @GetMapping("/loggers/levels")
    public List<String> getSupportedLevels() {
        return dynamicLoggerService.getSupportedLevels();
    }

    @Operation(summary = "临时设置日志级别（用于调试）")
    @PostMapping("/loggers/{loggerName}/temporary")
    public Map<String, Object> temporarySetLogLevel(
            @PathVariable String loggerName,
            @RequestBody Map<String, Object> payload) {
        String level = (String) payload.get("level");
        Integer duration = (Integer) payload.getOrDefault("durationSeconds", 300); // 默认 5 分钟

        if (level == null) {
            throw new IllegalArgumentException("Payload must contain 'level'");
        }

        dynamicLoggerService.temporarySetLogLevel(loggerName, level, duration);

        return Map.of(
                "logger", loggerName,
                "level", level,
                "durationSeconds", duration,
                "status", "temporarily set");
    }
}
