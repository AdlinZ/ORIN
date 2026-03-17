package com.adlin.orin.modules.alert.controller;

import com.adlin.orin.modules.alert.entity.AlertHistory;
import com.adlin.orin.modules.alert.entity.AlertRule;
import com.adlin.orin.modules.alert.entity.AlertNotificationConfig;
import com.adlin.orin.modules.alert.service.AlertNotificationService;
import com.adlin.orin.modules.alert.service.AlertNotificationConfigService;
import com.adlin.orin.modules.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alert Management", description = "告警管理")
public class AlertController {

    private final AlertService alertService;
    private final AlertNotificationService notificationService;
    private final AlertNotificationConfigService configService;

    @Operation(summary = "创建告警规则")
    @PostMapping("/rules")
    public AlertRule createRule(@RequestBody AlertRule rule) {
        return alertService.createRule(rule);
    }

    @Operation(summary = "获取所有告警规则")
    @GetMapping("/rules")
    public List<AlertRule> getAllRules() {
        return alertService.getAllRules();
    }

    @Operation(summary = "更新告警规则")
    @PutMapping("/rules/{id}")
    public AlertRule updateRule(@PathVariable String id, @RequestBody AlertRule rule) {
        return alertService.updateRule(id, rule);
    }

    @Operation(summary = "删除告警规则")
    @DeleteMapping("/rules/{id}")
    public Map<String, String> deleteRule(@PathVariable String id) {
        alertService.deleteRule(id);
        return Map.of("status", "deleted", "ruleId", id);
    }

    @Operation(summary = "测试告警通知")
    @PostMapping("/rules/{id}/test")
    public Map<String, String> testNotification(@PathVariable String id) {
        AlertRule rule = alertService.getAllRules().stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Rule not found"));

        alertService.testNotification(rule);
        return Map.of("status", "sent", "message", "Test notification sent");
    }

    @Operation(summary = "获取告警历史")
    @GetMapping("/history")
    public Page<AlertHistory> getAlertHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return alertService.getAlertHistory(PageRequest.of(page, size));
    }

    @Operation(summary = "按智能体获取告警历史")
    @GetMapping("/history/agent/{agentId}")
    public List<AlertHistory> getAlertHistoryByAgent(@PathVariable String agentId) {
        return alertService.getAlertHistoryByAgent(agentId);
    }

    @Operation(summary = "解决告警")
    @PostMapping("/history/{id}/resolve")
    public AlertHistory resolveAlert(@PathVariable String id) {
        return alertService.resolveAlert(id);
    }

    @Operation(summary = "标记所有告警为已解决")
    @PostMapping("/history/resolve-all")
    public Map<String, Object> resolveAllAlerts() {
        int count = alertService.resolveAllAlerts();
        return Map.of(
            "success", true,
            "message", "已标记 " + count + " 条告警为已解决",
            "resolvedCount", count
        );
    }

    @Operation(summary = "清空所有告警历史")
    @DeleteMapping("/history/clear-all")
    public Map<String, Object> clearAllAlerts() {
        alertService.clearAllAlerts();
        return Map.of(
            "success", true,
            "message", "已清空所有告警历史"
        );
    }

    @Operation(summary = "获取未读告警数量")
    @GetMapping("/history/unread-count")
    public Map<String, Object> getUnreadCount() {
        long count = alertService.getUnreadCount();
        return Map.of(
            "count", count
        );
    }

    @Operation(summary = "获取告警统计")
    @GetMapping("/stats")
    public AlertService.AlertStats getStats() {
        return alertService.getStats();
    }

    @Operation(summary = "手动触发告警")
    @PostMapping("/trigger")
    public AlertHistory triggerAlert(@RequestBody Map<String, String> payload) {
        String ruleId = payload.get("ruleId");
        String agentId = payload.get("agentId");
        String message = payload.get("message");

        return alertService.triggerAlert(ruleId, agentId, message);
    }

    // ==================== 告警通知配置 ====================

    @Operation(summary = "测试通知渠道")
    @PostMapping("/notification/test")
    public Map<String, Object> testNotificationChannel(@RequestBody Map<String, String> payload) {
        String channel = payload.getOrDefault("channel", "email");
        boolean success = notificationService.sendTestNotification(channel);
        
        return Map.of(
            "success", success,
            "channel", channel,
            "message", success ? "测试通知发送成功" : "测试通知发送失败"
        );
    }

    @Operation(summary = "获取通知配置")
    @GetMapping("/notification/config")
    public AlertNotificationConfig getNotificationConfig() {
        return configService.getConfig();
    }

    @Operation(summary = "保存通知配置")
    @PostMapping("/notification/config")
    public AlertNotificationConfig saveNotificationConfig(@RequestBody AlertNotificationConfig config) {
        return configService.saveConfig(config);
    }
}
