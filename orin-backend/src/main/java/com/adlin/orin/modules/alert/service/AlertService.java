package com.adlin.orin.modules.alert.service;

import com.adlin.orin.modules.alert.entity.AlertHistory;
import com.adlin.orin.modules.alert.entity.AlertRule;
import com.adlin.orin.modules.alert.repository.AlertHistoryRepository;
import com.adlin.orin.modules.alert.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRuleRepository ruleRepository;
    private final AlertHistoryRepository historyRepository;
    private final NotificationService notificationService;

    /**
     * 创建告警规则
     */
    @Transactional
    public AlertRule createRule(AlertRule rule) {
        rule = ruleRepository.save(rule);
        log.info("Created alert rule: {}", rule.getRuleName());
        return rule;
    }

    /**
     * 获取所有规则
     */
    public List<AlertRule> getAllRules() {
        return ruleRepository.findAll();
    }

    /**
     * 获取启用的规则
     */
    public List<AlertRule> getEnabledRules() {
        return ruleRepository.findByEnabledTrue();
    }

    /**
     * 更新规则
     */
    @Transactional
    public AlertRule updateRule(String id, AlertRule updatedRule) {
        AlertRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found: " + id));

        rule.setRuleName(updatedRule.getRuleName());
        rule.setRuleType(updatedRule.getRuleType());
        rule.setConditionExpr(updatedRule.getConditionExpr());
        rule.setThresholdValue(updatedRule.getThresholdValue());
        rule.setSeverity(updatedRule.getSeverity());
        rule.setEnabled(updatedRule.getEnabled());
        rule.setNotificationChannels(updatedRule.getNotificationChannels());
        rule.setRecipientList(updatedRule.getRecipientList());
        rule.setCooldownMinutes(updatedRule.getCooldownMinutes());

        return ruleRepository.save(rule);
    }

    /**
     * 删除规则
     */
    @Transactional
    public void deleteRule(String id) {
        ruleRepository.deleteById(id);
        log.info("Deleted alert rule: {}", id);
    }

    /**
     * 触发告警
     */
    @Transactional
    public AlertHistory triggerAlert(String ruleId, String agentId, String message) {
        AlertRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Rule not found: " + ruleId));

        // 创建告警历史
        AlertHistory history = AlertHistory.builder()
                .ruleId(ruleId)
                .agentId(agentId)
                .alertMessage(message)
                .severity(rule.getSeverity())
                .status("TRIGGERED")
                .build();

        history = historyRepository.save(history);

        // 发送通知
        try {
            notificationService.sendNotification(rule, message);
            log.info("Alert triggered and notification sent: {}", message);
        } catch (Exception e) {
            log.error("Failed to send alert notification", e);
        }

        return history;
    }

    /**
     * 解决告警
     */
    @Transactional
    public AlertHistory resolveAlert(String historyId) {
        AlertHistory history = historyRepository.findById(historyId)
                .orElseThrow(() -> new RuntimeException("Alert history not found: " + historyId));

        history.setStatus("RESOLVED");
        history.setResolvedAt(LocalDateTime.now());

        return historyRepository.save(history);
    }

    /**
     * 获取告警历史
     */
    public Page<AlertHistory> getAlertHistory(Pageable pageable) {
        return historyRepository.findAllByOrderByTriggeredAtDesc(pageable);
    }

    /**
     * 按智能体获取告警历史
     */
    public List<AlertHistory> getAlertHistoryByAgent(String agentId) {
        return historyRepository.findByAgentIdOrderByTriggeredAtDesc(agentId);
    }

    /**
     * 测试告警通知
     */
    public void testNotification(AlertRule rule) {
        String testMessage = "这是一条测试告警消息 - " + LocalDateTime.now();
        notificationService.sendNotification(rule, testMessage);
    }

    /**
     * 获取统计信息
     */
    public AlertStats getStats() {
        long totalRules = ruleRepository.count();
        long enabledRules = ruleRepository.findByEnabledTrue().size();
        long activeAlerts = historyRepository.countByStatus("TRIGGERED");
        long totalAlerts = historyRepository.count();

        return new AlertStats(totalRules, enabledRules, activeAlerts, totalAlerts);
    }

    /**
     * 告警统计信息
     */
    public record AlertStats(
            long totalRules,
            long enabledRules,
            long activeAlerts,
            long totalAlerts) {
    }
}
