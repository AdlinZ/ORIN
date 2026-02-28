package com.adlin.orin.modules.alert.controller;

import com.adlin.orin.modules.alert.entity.AlertHistory;
import com.adlin.orin.modules.alert.entity.AlertRule;
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
}
