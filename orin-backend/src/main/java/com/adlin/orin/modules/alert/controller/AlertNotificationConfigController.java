package com.adlin.orin.modules.alert.controller;

import com.adlin.orin.modules.alert.entity.AlertNotificationConfig;
import com.adlin.orin.modules.alert.service.AlertNotificationConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 告警通知配置 Controller
 */
@RestController
@RequestMapping("/api/alerts/notification-config")
@RequiredArgsConstructor
public class AlertNotificationConfigController {

    private final AlertNotificationConfigService service;

    /**
     * 获取通知配置
     */
    @GetMapping
    public ResponseEntity<AlertNotificationConfig> getConfig() {
        return ResponseEntity.ok(service.getConfig());
    }

    /**
     * 保存通知配置
     */
    @PostMapping
    public ResponseEntity<AlertNotificationConfig> saveConfig(@RequestBody AlertNotificationConfig config) {
        return ResponseEntity.ok(service.saveConfig(config));
    }

    /**
     * 获取配置状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(service.getStatus());
    }

    /**
     * 测试通知
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testNotification(@RequestBody Map<String, String> request) {
        String channel = request.get("channel");
        boolean success = service.testNotification(channel);

        if (success) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", channel + " 测试通知发送成功"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", channel + " 测试通知发送失败"
            ));
        }
    }
}
