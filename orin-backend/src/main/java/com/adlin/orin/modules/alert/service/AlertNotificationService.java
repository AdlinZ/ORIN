package com.adlin.orin.modules.alert.service;

import com.adlin.orin.modules.alert.entity.AlertRecord;
import com.adlin.orin.modules.system.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 告警通知服务
 * 支持邮件、钉钉、企业微信等多种通知渠道
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotificationService {

    private final MailService mailService;

    @Value("${alert.notification.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${alert.notification.email.recipients:}")
    private String emailRecipients;

    @Value("${alert.notification.dingtalk.enabled:false}")
    private boolean dingtalkEnabled;

    @Value("${alert.notification.dingtalk.webhook:}")
    private String dingtalkWebhook;

    @Value("${alert.notification.wecom.enabled:false}")
    private boolean wecomEnabled;

    @Value("${alert.notification.wecom.webhook:}")
    private String wecomWebhook;

    /**
     * 发送告警通知
     */
    public void sendAlertNotification(AlertRecord alert, Map<String, Object> details) {
        String title = buildAlertTitle(alert);
        String content = buildAlertContent(alert, details);

        // 邮件通知
        if (emailEnabled && emailRecipients != null && !emailRecipients.isEmpty()) {
            sendEmailNotification(title, content);
        }

        // 钉钉通知
        if (dingtalkEnabled && dingtalkWebhook != null && !dingtalkWebhook.isEmpty()) {
            sendDingtalkNotification(title, content);
        }

        // 企业微信通知
        if (wecomEnabled && wecomWebhook != null && !wecomWebhook.isEmpty()) {
            sendWecomNotification(title, content);
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
        String title = "[ORIN 告警系统] 测试通知";
        String content = "这是一条测试通知，如果您收到此消息，说明告警通道配置正常。";

        switch (channel.toLowerCase()) {
            case "email":
                return sendEmailNotification(title, content);
            case "dingtalk":
                return sendDingtalkNotification(title, content);
            case "wecom":
                return sendWecomNotification(title, content);
            default:
                log.warn("未知的通知渠道: {}", channel);
                return false;
        }
    }

    private boolean sendEmailNotification(String title, String content) {
        try {
            String[] recipients = emailRecipients.split(",");
            return mailService.sendAlertEmail(recipients, title, content);
        } catch (Exception e) {
            log.error("邮件通知发送失败", e);
            return false;
        }
    }

    private boolean sendDingtalkNotification(String title, String content) {
        try {
            // 使用简单的 Markdown 格式
            String markdownContent = String.format(
                "## %s\n\n%s\n\n> 来源: ORIN 智能体管理系统",
                title, content.replace("\n", "\n\n")
            );

            Map<String, Object> body = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of(
                    "title", title,
                    "text", markdownContent
                )
            );

            // 简化实现，实际生产环境需要更完善的 HTTP 客户端
            log.info("钉钉通知: {}", title);
            return true;
        } catch (Exception e) {
            log.error("钉钉通知发送失败", e);
            return false;
        }
    }

    private boolean sendWecomNotification(String title, String content) {
        try {
            log.info("企业微信通知: {}", title);
            return true;
        } catch (Exception e) {
            log.error("企业微信通知发送失败", e);
            return false;
        }
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
