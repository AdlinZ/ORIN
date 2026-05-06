package com.adlin.orin.modules.alert.service;

import com.adlin.orin.modules.alert.entity.AlertNotificationConfig;
import com.adlin.orin.modules.alert.entity.AlertRecord;
import com.adlin.orin.modules.alert.entity.AlertRule;
import com.adlin.orin.modules.alert.repository.AlertNotificationConfigRepository;
import com.adlin.orin.modules.notification.service.SystemNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 告警通知服务
 * 支持邮件、钉钉、企业微信等多种通知渠道
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotificationService {

    private final AlertNotificationConfigRepository configRepository;
    private final AlertChannelGateway alertChannelUnifiedGateway;
    private final SystemNotificationService systemNotificationService;

    /**
     * 发送告警通知
     */
    public void sendAlertNotification(AlertRecord alert, Map<String, Object> details) {
        AlertNotificationConfig config = getConfig();
        String severity = alert.getSeverity() != null ? alert.getSeverity() : "INFO";
        if (shouldSkipBySeverity(config, severity)) {
            log.info("Alert notification skipped by criticalOnly setting: severity={}", severity);
            return;
        }
        String title = buildAlertTitle(alert);
        String content = buildAlertContent(alert, details);

        sendInAppIfEnabled(config, severity, title, content);
        for (String channel : enabledConfigChannels(config)) {
            sendExternalChannel(channel, config, title, content, null);
        }
    }

    /**
     * 批量发送告警通知
     */
    public void sendBatchNotifications(List<AlertRecord> alerts) {
        for (AlertRecord alert : alerts) {
            sendAlertNotification(alert, null);
        }
    }

    /**
     * 发送测试通知
     */
    public boolean sendTestNotification(String channel) {
        AlertNotificationConfig config = getConfig();
        String title = "[ORIN 告警系统] 测试通知";
        String content = "这是一条测试通知，如果您收到此消息，说明告警通道配置正常。";
        return alertChannelUnifiedGateway.send(channel, config, title, content, null);
    }

    /**
     * 发送规则通知（兼容历史规则配置）。
     */
    public void sendRuleNotification(AlertRule rule, String message) {
        sendRuleNotification(rule, message, null, 1);
    }

    public void sendRuleNotification(AlertRule rule, String message, String fingerprint, Integer repeatCount) {
        AlertNotificationConfig config = getConfig();
        String severity = rule.getSeverity() != null ? rule.getSeverity() : "INFO";
        if (shouldSkipBySeverity(config, severity)) {
            log.info("Rule notification skipped by criticalOnly setting: rule={}, severity={}", rule.getRuleName(), severity);
            return;
        }

        String title = String.format("[ORIN Alert] %s - %s", severity, rule.getRuleName());
        String content = String.format(
            "Alert Rule: %s\nSeverity: %s\nTime: %s\n\nMessage:\n%s",
            rule.getRuleName(), severity, java.time.LocalDateTime.now(), message
        );

        sendInAppIfEnabled(config, severity, title, content, fingerprint, "TRIGGERED", repeatCount, message);
        for (String channel : resolveChannels(rule, config)) {
            sendExternalChannel(channel, config, title, content, rule.getRecipientList());
        }
    }

    public void updateRuleNotification(AlertRule rule, String message, String fingerprint, Integer repeatCount) {
        AlertNotificationConfig config = getConfig();
        String severity = rule.getSeverity() != null ? rule.getSeverity() : "INFO";
        if (shouldSkipBySeverity(config, severity)) {
            return;
        }
        String title = String.format("[ORIN Alert] %s - %s", severity, rule.getRuleName());
        String content = String.format(
                "Alert Rule: %s\nSeverity: %s\nTime: %s\n\nMessage:\n%s",
                rule.getRuleName(), severity, java.time.LocalDateTime.now(), message
        );
        sendInAppIfEnabled(config, severity, title, content, fingerprint, "TRIGGERED", repeatCount, message);
    }

    public void resolveRuleNotification(AlertRule rule, String message, String fingerprint, Integer repeatCount) {
        AlertNotificationConfig config = getConfig();
        String severity = rule.getSeverity() != null ? rule.getSeverity() : "INFO";
        String title = String.format("[ORIN Alert] RESOLVED - %s", rule.getRuleName());
        String content = String.format(
                "Alert Rule: %s\nSeverity: %s\nTime: %s\n\nMessage:\n%s",
                rule.getRuleName(), severity, java.time.LocalDateTime.now(), message
        );
        sendInAppIfEnabled(config, "SUCCESS", title, content, fingerprint, "RESOLVED", repeatCount, message);
    }

    public void sendSystemNotification(String title, String severity, String message) {
        sendSystemNotification(title, severity, message, null, 1);
    }

    public void sendSystemNotification(String title, String severity, String message, String fingerprint, Integer repeatCount) {
        AlertNotificationConfig config = getConfig();
        String normalizedSeverity = severity != null ? severity : "WARNING";
        if (shouldSkipBySeverity(config, normalizedSeverity)) {
            log.info("System alert notification skipped by criticalOnly setting: severity={}", normalizedSeverity);
            return;
        }
        String content = String.format(
                "Alert Rule: %s\nSeverity: %s\nTime: %s\n\nMessage:\n%s",
                title, normalizedSeverity, java.time.LocalDateTime.now(), message
        );
        sendInAppIfEnabled(config, normalizedSeverity, title, content, fingerprint, "TRIGGERED", repeatCount, message);
        for (String channel : enabledConfigChannels(config)) {
            sendExternalChannel(channel, config, title, content, null);
        }
    }

    public void updateSystemNotification(String title, String severity, String message, String fingerprint, Integer repeatCount) {
        AlertNotificationConfig config = getConfig();
        String normalizedSeverity = severity != null ? severity : "WARNING";
        if (shouldSkipBySeverity(config, normalizedSeverity)) {
            return;
        }
        String content = String.format(
                "Alert Rule: %s\nSeverity: %s\nTime: %s\n\nMessage:\n%s",
                title, normalizedSeverity, java.time.LocalDateTime.now(), message
        );
        sendInAppIfEnabled(config, normalizedSeverity, title, content, fingerprint, "TRIGGERED", repeatCount, message);
    }

    public void resolveSystemNotification(String title, String message, String fingerprint, Integer repeatCount) {
        AlertNotificationConfig config = getConfig();
        String content = String.format(
                "Alert Rule: %s\nSeverity: RESOLVED\nTime: %s\n\nMessage:\n%s",
                title, java.time.LocalDateTime.now(), message
        );
        sendInAppIfEnabled(config, "SUCCESS", "[ORIN Alert] RESOLVED - " + title,
                content, fingerprint, "RESOLVED", repeatCount, message);
    }

    private AlertNotificationConfig getConfig() {
        return configRepository.findFirstConfig().orElseGet(() -> {
            AlertNotificationConfig config = new AlertNotificationConfig();
            config.setEmailEnabled(true);
            config.setDingtalkEnabled(false);
            config.setWecomEnabled(false);
            config.setNotifyEmail(true);
            config.setNotifyInapp(true);
            return config;
        });
    }

    private Set<String> resolveChannels(AlertRule rule, AlertNotificationConfig config) {
        if (rule.getNotificationChannels() == null || rule.getNotificationChannels().isBlank()) {
            return enabledConfigChannels(config);
        }
        Set<String> channels = new LinkedHashSet<>();
        Arrays.stream(rule.getNotificationChannels().split(","))
                .map(alertChannelUnifiedGateway::normalizeChannel)
                .filter(channel -> !channel.isBlank())
                .forEach(channels::add);
        return channels;
    }

    private Set<String> enabledConfigChannels(AlertNotificationConfig config) {
        Set<String> channels = new LinkedHashSet<>();
        if (Boolean.TRUE.equals(config.getEmailEnabled())) {
            channels.add("email");
        }
        if (Boolean.TRUE.equals(config.getDingtalkEnabled())) {
            channels.add("dingtalk");
        }
        if (Boolean.TRUE.equals(config.getWecomEnabled())) {
            channels.add("wecom");
        }
        return channels;
    }

    private boolean sendExternalChannel(String channel, AlertNotificationConfig config, String title,
                                        String content, String receiverOverride) {
        String normalized = alertChannelUnifiedGateway.normalizeChannel(channel);
        if ("email".equals(normalized) && (!Boolean.TRUE.equals(config.getNotifyEmail())
                || !Boolean.TRUE.equals(config.getEmailEnabled()))) {
            return false;
        }
        if ("dingtalk".equals(normalized) && !Boolean.TRUE.equals(config.getDingtalkEnabled())) {
            return false;
        }
        if ("wecom".equals(normalized) && !Boolean.TRUE.equals(config.getWecomEnabled())) {
            return false;
        }
        return alertChannelUnifiedGateway.send(normalized, config, title, content, receiverOverride);
    }

    private void sendInAppIfEnabled(AlertNotificationConfig config, String severity, String title, String content) {
        sendInAppIfEnabled(config, severity, title, content, null, "TRIGGERED", 1, content);
    }

    private void sendInAppIfEnabled(AlertNotificationConfig config, String severity, String title, String content,
                                    String fingerprint, String status, Integer repeatCount, String summary) {
        if (!Boolean.TRUE.equals(config.getNotifyInapp())) {
            return;
        }
        if (fingerprint == null || fingerprint.isBlank()) {
            systemNotificationService.sendMessage(title, content, toMessageType(severity), null, "ALERT", "BROADCAST");
            return;
        }
        systemNotificationService.sendAggregatedAlert(title, content, toMessageType(severity), null, "ALERT", "BROADCAST",
                fingerprint, fingerprint, status, repeatCount, summary);
    }

    private boolean shouldSkipBySeverity(AlertNotificationConfig config, String severity) {
        return Boolean.TRUE.equals(config.getCriticalOnly()) && !isCriticalSeverity(severity);
    }

    private boolean isCriticalSeverity(String severity) {
        String normalized = severity == null ? "" : severity.toUpperCase(Locale.ROOT);
        return "CRITICAL".equals(normalized) || "ERROR".equals(normalized);
    }

    private String toMessageType(String severity) {
        String normalized = severity == null ? "INFO" : severity.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CRITICAL", "ERROR" -> "ERROR";
            case "WARNING", "WARN" -> "WARNING";
            case "SUCCESS", "RESOLVED" -> "SUCCESS";
            default -> "INFO";
        };
    }

    private String buildAlertTitle(AlertRecord alert) {
        String severity = alert.getSeverity() != null ? alert.getSeverity() : "INFO";
        return String.format("[%s] %s - %s", severity, alert.getAlertType(), alert.getAgentId());
    }

    private String buildAlertContent(AlertRecord alert, Map<String, Object> details) {
        StringBuilder sb = new StringBuilder();
        sb.append("**告警详情**\n\n");
        sb.append("- **智能体ID**: ").append(alert.getAgentId()).append("\n");
        sb.append("- **告警类型**: ").append(alert.getAlertType()).append("\n");
        sb.append("- **严重程度**: ").append(alert.getSeverity()).append("\n");
        sb.append("- **告警时间**: ").append(alert.getCreatedAt()).append("\n");
        sb.append("- **消息**: ").append(alert.getMessage()).append("\n");

        if (details != null && !details.isEmpty()) {
            sb.append("\n**附加信息**\n");
            details.forEach((key, value) -> {
                sb.append("- ").append(key).append(": ").append(value).append("\n");
            });
        }

        return sb.toString();
    }
}
