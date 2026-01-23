package com.adlin.orin.modules.alert.service;

import com.adlin.orin.modules.alert.entity.AlertRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * 通知服务
 * 支持邮件、钉钉、企业微信等多种通知渠道
 */
@Service
@Slf4j
public class NotificationService {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    /**
     * 发送通知
     */
    public void sendNotification(AlertRule rule, String message) {
        if (rule.getNotificationChannels() == null || rule.getNotificationChannels().isEmpty()) {
            log.warn("No notification channels configured for rule: {}", rule.getRuleName());
            return;
        }

        String[] channels = rule.getNotificationChannels().split(",");

        Arrays.stream(channels).forEach(channel -> {
            switch (channel.trim().toUpperCase()) {
                case "EMAIL":
                    sendEmail(rule, message);
                    break;
                case "DINGTALK":
                    sendDingTalk(rule, message);
                    break;
                case "WECHAT":
                    sendWeChat(rule, message);
                    break;
                default:
                    log.warn("Unknown notification channel: {}", channel);
            }
        });
    }

    /**
     * 发送邮件通知
     */
    private void sendEmail(AlertRule rule, String message) {
        if (rule.getRecipientList() == null || rule.getRecipientList().isEmpty()) {
            log.warn("No recipients configured for email notification: {}", rule.getRuleName());
            return;
        }

        try {
            // Check if mailSender is available (it might be null if no config provided)
            if (mailSender == null) {
                log.warn("JavaMailSender is not available. Please configure spring.mail properties.");
                log.info("MOCK EMAIL SENT TO: {} | SUBJECT: [ORIN Alert] {} | CONTENT: {}",
                        rule.getRecipientList(), rule.getRuleName(), message);
                return;
            }

            org.springframework.mail.SimpleMailMessage mailMessage = new org.springframework.mail.SimpleMailMessage();
            mailMessage.setTo(rule.getRecipientList().split(","));
            mailMessage.setSubject("[ORIN Alert] " + rule.getSeverity() + " - " + rule.getRuleName());
            mailMessage.setText(String.format("Alert Rule: %s\nSeverity: %s\nTime: %s\n\nMessage:\n%s",
                    rule.getRuleName(), rule.getSeverity(), java.time.LocalDateTime.now(), message));

            mailSender.send(mailMessage);
            log.info("Email sent to: {}", rule.getRecipientList());

        } catch (Exception e) {
            log.error("Failed to send email notification", e);
        }
    }

    /**
     * 发送钉钉通知
     */
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.web.client.RestTemplate restTemplate;

    // ... existing code ...

    /**
     * 发送钉钉通知
     */
    private void sendDingTalk(AlertRule rule, String message) {
        if (rule.getRecipientList() == null || rule.getRecipientList().isEmpty()) {
            return;
        }

        String[] webhooks = rule.getRecipientList().split(",");
        for (String webhook : webhooks) {
            webhook = webhook.trim();
            if (!webhook.startsWith("http")) {
                continue;
            }

            try {
                java.util.Map<String, Object> text = new java.util.HashMap<>();
                text.put("content", "[ORIN " + rule.getSeverity() + "] " + message);

                java.util.Map<String, Object> body = new java.util.HashMap<>();
                body.put("msgtype", "text");
                body.put("text", text);

                restTemplate.postForEntity(webhook, body, String.class);
                log.info("DingTalk notification sent: {}", rule.getRuleName());
            } catch (Exception e) {
                log.error("Failed to send DingTalk notification: {}", e.getMessage());
            }
        }
    }

    /**
     * 发送企业微信通知
     */
    private void sendWeChat(AlertRule rule, String message) {
        if (rule.getRecipientList() == null || rule.getRecipientList().isEmpty()) {
            return;
        }

        String[] webhooks = rule.getRecipientList().split(",");
        for (String webhook : webhooks) {
            webhook = webhook.trim();
            if (!webhook.startsWith("http")) {
                continue;
            }

            try {
                java.util.Map<String, Object> text = new java.util.HashMap<>();
                text.put("content", "[ORIN " + rule.getSeverity() + "] " + message);

                java.util.Map<String, Object> body = new java.util.HashMap<>();
                body.put("msgtype", "text");
                body.put("text", text);

                restTemplate.postForEntity(webhook, body, String.class);
                log.info("WeChat notification sent: {}", rule.getRuleName());
            } catch (Exception e) {
                log.error("Failed to send WeChat notification: {}", e.getMessage());
            }
        }
    }
}
