package com.adlin.orin.modules.monitor.controller;

import com.adlin.orin.modules.monitor.entity.RateLimitConfig;
import com.adlin.orin.modules.monitor.service.MonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 限流配置控制器
 * 提供限流配置的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/rate-limit")
@RequiredArgsConstructor
@Tag(name = "Rate Limit Config", description = "限流配置管理接口")
public class RateLimitConfigController {

    private final MonitorService monitorService;

    @Operation(summary = "获取限流配置")
    @GetMapping("/config")
    public ResponseEntity<RateLimitConfig> getRateLimitConfig() {
        RateLimitConfig config = monitorService.getRateLimitConfig();
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(config);
    }

    @Operation(summary = "更新限流配置")
    @PutMapping("/config")
    public ResponseEntity<Void> updateRateLimitConfig(
            @RequestBody RateLimitConfig config,
            @RequestHeader(value = "X-Operator-Id", defaultValue = "system") String operator) {

        log.info("Updating rate limit config by operator: {}", operator);
        monitorService.updateRateLimitConfig(config, operator);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "获取当前限流配置(带缓存)")
    @GetMapping("/config/cached")
    public ResponseEntity<RateLimitConfig> getRateLimitConfigCached() {
        RateLimitConfig config = monitorService.getRateLimitConfigCached();
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(config);
    }
}
