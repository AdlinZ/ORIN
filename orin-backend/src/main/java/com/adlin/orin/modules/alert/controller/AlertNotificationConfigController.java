package com.adlin.orin.modules.alert.controller;

import com.adlin.orin.modules.alert.entity.AlertNotificationConfig;
import com.adlin.orin.modules.alert.service.AlertNotificationConfigService;
import com.adlin.orin.modules.audit.service.AuditHelper;
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
    private final AuditHelper auditHelper;

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
        AlertNotificationConfig saved = service.saveConfig(config);
        auditHelper.log("SYSTEM", "ALERT_CONFIG_SAVE", "/api/alerts/notification-config",
                "保存告警通知配置: emailEnabled=" + config.getEmailEnabled()
                        + ", dingtalkEnabled=" + config.getDingtalkEnabled()
                        + ", wecomEnabled=" + config.getWecomEnabled(), true, null);
        return ResponseEntity.ok(saved);
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
            auditHelper.log("SYSTEM", "ALERT_NOTIFICATION_TEST", "/api/alerts/notification-config/test",
                    "测试告警通知成功: channel=" + channel, true, null);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", channel + " 测试通知发送成功"
            ));
        } else {
            auditHelper.log("SYSTEM", "ALERT_NOTIFICATION_TEST", "/api/alerts/notification-config/test",
                    "测试告警通知失败: channel=" + channel, false, "通知发送失败");
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", channel + " 测试通知发送失败"
            ));
        }
    }
}
