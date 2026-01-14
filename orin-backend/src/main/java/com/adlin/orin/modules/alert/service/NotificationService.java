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
        // TODO: 实现邮件发送
        // 需要配置 Spring Boot Mail Starter
        log.info("EMAIL notification: [{}] {}", rule.getSeverity(), message);
        log.info("Recipients: {}", rule.getRecipientList());

        // 示例实现：
        // SimpleMailMessage mailMessage = new SimpleMailMessage();
        // mailMessage.setTo(rule.getRecipientList().split(","));
        // mailMessage.setSubject("[ORIN Alert] " + rule.getRuleName());
        // mailMessage.setText(message);
        // mailSender.send(mailMessage);
    }

    /**
     * 发送钉钉通知
     */
    private void sendDingTalk(AlertRule rule, String message) {
        // TODO: 实现钉钉 Webhook 通知
        log.info("DINGTALK notification: [{}] {}", rule.getSeverity(), message);

        // 示例实现：
        // String webhook = dingTalkWebhook;
        // Map<String, Object> payload = new HashMap<>();
        // payload.put("msgtype", "text");
        // payload.put("text", Map.of("content", message));
        // restTemplate.postForObject(webhook, payload, String.class);
    }

    /**
     * 发送企业微信通知
     */
    private void sendWeChat(AlertRule rule, String message) {
        // TODO: 实现企业微信 Webhook 通知
        log.info("WECHAT notification: [{}] {}", rule.getSeverity(), message);

        // 示例实现：
        // String webhook = weChatWebhook;
        // Map<String, Object> payload = new HashMap<>();
        // payload.put("msgtype", "text");
        // payload.put("text", Map.of("content", message));
        // restTemplate.postForObject(webhook, payload, String.class);
    }
}
