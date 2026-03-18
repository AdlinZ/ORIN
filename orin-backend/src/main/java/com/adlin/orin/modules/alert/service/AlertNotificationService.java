package com.adlin.orin.modules.alert.service;

import com.adlin.orin.modules.alert.entity.AlertNotificationConfig;
import com.adlin.orin.modules.alert.entity.AlertRecord;
import com.adlin.orin.modules.alert.repository.AlertNotificationConfigRepository;
import com.adlin.orin.modules.system.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 告警通知服务
 * 支持邮件、钉钉、企业微信等多种通知渠道
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotificationService {

    private final MailService mailService;
    private final AlertNotificationConfigRepository configRepository;
    private final RestTemplate restTemplate;

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
            sendEmailNotification(config, title, content);
        }

        // 钉钉通知
        if (config.getDingtalkEnabled() != null && config.getDingtalkEnabled()
            && config.getDingtalkWebhook() != null && !config.getDingtalkWebhook().isEmpty()) {
            sendDingtalkNotification(config, title, content);
        }

        // 企业微信通知
        if (config.getWecomEnabled() != null && config.getWecomEnabled()
            && config.getWecomWebhook() != null && !config.getWecomWebhook().isEmpty()) {
            sendWecomNotification(config, title, content);
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

        switch (channel.toLowerCase()) {
            case "email":
                return sendEmailNotification(config, title, content);
            case "dingtalk":
                return sendDingtalkNotification(config, title, content);
            case "wecom":
                return sendWecomNotification(config, title, content);
            default:
                log.warn("未知的通知渠道: {}", channel);
                return false;
        }
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

    private boolean sendEmailNotification(AlertNotificationConfig config, String title, String content) {
        try {
            String[] recipients = config.getEmailRecipients().split(",");
            return mailService.sendAlertEmail(recipients, title, content);
        } catch (Exception e) {
            log.error("邮件通知发送失败", e);
            return false;
        }
    }

    /**
     * 验证 Webhook URL 是否安全，防止 SSRF 攻击。
     * 仅允许 HTTPS 协议，且不允许请求内网保留地址。
     */
    private void validateWebhookUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook URL 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Webhook URL 格式无效: " + url);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Webhook URL 必须使用 HTTPS 协议");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Webhook URL 缺少合法 Host");
        }
        // 拒绝内网 / 左集地址，防止 SSRF
        if (Pattern.matches(
                "(localhost|.*\\.local|127\\..+|10\\..+|172\\.(1[6-9]|2[0-9]|3[01])\\..+|192\\.168\\..+|0\\.0\\.0\\.0|169\\.254\\..+|::1|fc.+|fd.+)",
                host.toLowerCase())) {
            throw new IllegalArgumentException("Webhook URL 不允许指向内网地址");
        }
    }

    private boolean sendDingtalkNotification(AlertNotificationConfig config, String title, String content) {
        try {
            validateWebhookUrl(config.getDingtalkWebhook());
            String markdownContent = String.format(
                "## %s\n\n%s\n\n> 来源: ORIN 智能体管理系统",
                title, content.replace("\n", "\n\n")
            );

            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");
            body.put("markdown", Map.of(
                "title", title,
                "text", markdownContent
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.postForObject(config.getDingtalkWebhook(), entity, String.class);
            log.info("钉钉通知发送成功: {}", title);
            return true;
        } catch (Exception e) {
            log.error("钉钉通知发送失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendWecomNotification(AlertNotificationConfig config, String title, String content) {
        try {
            validateWebhookUrl(config.getWecomWebhook());
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "text");
            body.put("text", Map.of(
                "content", title + "\n" + content
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.postForObject(config.getWecomWebhook(), entity, String.class);
            log.info("企业微信通知发送成功: {}", title);
            return true;
        } catch (Exception e) {
            log.error("企业微信通知发送失败: {}", e.getMessage());
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
