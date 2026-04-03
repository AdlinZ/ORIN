package com.adlin.orin.modules.alert.service;

import com.adlin.orin.modules.alert.entity.AlertNotificationConfig;
import com.adlin.orin.modules.alert.entity.AlertRecord;
import com.adlin.orin.modules.alert.entity.AlertRule;
import com.adlin.orin.modules.alert.repository.AlertNotificationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Arrays;

/**
 * 告警通知服务
 * 支持邮件、钉钉、企业微信等多种通知渠道
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotificationService {

    private final AlertNotificationConfigRepository configRepository;
    private final AlertChannelGateway alertChannelGateway;

    /**
     * 发送告警通知
     */
    public void sendAlertNotification(AlertRecord alert, Map<String, Object> details) {
        AlertNotificationConfig config = getConfig();
        String title = buildAlertTitle(alert);
        String content = buildAlertContent(alert, details);

        // 邮件通知
        if (config.getEmailEnabled() != null && config.getEmailEnabled()
            && config.getEmailRecipients() != null && !config.getEmailRecipients().isEmpty()) {
            alertChannelGateway.send("email", config, title, content, null);
        }

        // 钉钉通知
        if (config.getDingtalkEnabled() != null && config.getDingtalkEnabled()
            && config.getDingtalkWebhook() != null && !config.getDingtalkWebhook().isEmpty()) {
            alertChannelGateway.send("dingtalk", config, title, content, null);
        }

        // 企业微信通知
        if (config.getWecomEnabled() != null && config.getWecomEnabled()
            && config.getWecomWebhook() != null && !config.getWecomWebhook().isEmpty()) {
            alertChannelGateway.send("wecom", config, title, content, null);
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
        return alertChannelGateway.send(channel, config, title, content, null);
    }

    /**
     * 发送规则通知（兼容历史规则配置）。
     */
    public void sendRuleNotification(AlertRule rule, String message) {
        if (rule.getNotificationChannels() == null || rule.getNotificationChannels().isBlank()) {
            log.warn("规则未配置通知渠道: {}", rule.getRuleName());
            return;
        }
        AlertNotificationConfig config = getConfig();
        String title = String.format("[ORIN Alert] %s - %s", rule.getSeverity(), rule.getRuleName());
        String content = String.format(
            "Alert Rule: %s\nSeverity: %s\nTime: %s\n\nMessage:\n%s",
            rule.getRuleName(), rule.getSeverity(), java.time.LocalDateTime.now(), message
        );

        Arrays.stream(rule.getNotificationChannels().split(","))
            .map(alertChannelGateway::normalizeChannel)
            .filter(channel -> !channel.isBlank())
            .forEach(channel -> alertChannelGateway.send(channel, config, title, content, rule.getRecipientList()));
    }

    private AlertNotificationConfig getConfig() {
        return configRepository.findFirstConfig().orElseGet(() -> {
            AlertNotificationConfig config = new AlertNotificationConfig();
            config.setEmailEnabled(true);
            config.setDingtalkEnabled(false);
            config.setWecomEnabled(false);
            return config;
        });
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
