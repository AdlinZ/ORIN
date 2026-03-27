package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统基础配置 Controller
 * 提供系统基本参数配置API - 持久化到数据库
 */
@RestController
@RequestMapping("/api/v1/system/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    /**
     * 获取系统基础配置
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(systemConfigService.getConfig());
    }

    /**
     * 更新系统基础配置 - 持久化到数据库
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> config) {
        Map<String, Object> result = systemConfigService.saveConfig(config);
        result.put("success", true);
        return ResponseEntity.ok(result);
    }
}