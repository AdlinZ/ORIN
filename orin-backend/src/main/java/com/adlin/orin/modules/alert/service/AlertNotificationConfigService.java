package com.adlin.orin.modules.alert.service;

import com.adlin.orin.modules.alert.entity.AlertNotificationConfig;
import com.adlin.orin.modules.alert.repository.AlertNotificationConfigRepository;
import com.adlin.orin.modules.system.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 告警通知配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotificationConfigService {

    private final AlertNotificationConfigRepository repository;
    private final MailService mailService;
    private final RestTemplate restTemplate;

    /**
     * 获取通知配置
     */
    public AlertNotificationConfig getConfig() {
        return repository.findFirstConfig().orElseGet(() -> {
            // 如果没有配置，创建默认配置
            AlertNotificationConfig config = new AlertNotificationConfig();
            return repository.save(config);
        });
    }

    /**
     * 保存通知配置
     */
    public AlertNotificationConfig saveConfig(AlertNotificationConfig config) {
        // 确保 id 存在（更新而非创建）
        AlertNotificationConfig existing = repository.findFirstConfig().orElse(null);
        if (existing != null) {
            config.setId(existing.getId());
            config.setCreatedAt(existing.getCreatedAt());
        }
        return repository.save(config);
    }

    /**
     * 获取配置状态
     */
    public Map<String, Object> getStatus() {
        AlertNotificationConfig config = getConfig();
        Map<String, Object> status = new HashMap<>();

        // 邮件状态
        status.put("email", Map.of(
            "enabled", config.getEmailEnabled() != null && config.getEmailEnabled(),
            "configured", config.getEmailRecipients() != null && !config.getEmailRecipients().isEmpty(),
            "recipients", config.getEmailRecipients()
        ));

        // 钉钉状态
        status.put("dingtalk", Map.of(
            "enabled", config.getDingtalkEnabled() != null && config.getDingtalkEnabled(),
            "configured", config.getDingtalkWebhook() != null && !config.getDingtalkWebhook().isEmpty()
        ));

        // 企业微信状态
        status.put("wecom", Map.of(
            "enabled", config.getWecomEnabled() != null && config.getWecomEnabled(),
            "configured", config.getWecomWebhook() != null && !config.getWecomWebhook().isEmpty()
        ));

        return status;
    }

    /**
     * 测试通知
     */
    public boolean testNotification(String channel) {
        AlertNotificationConfig config = getConfig();

        switch (channel.toLowerCase()) {
            case "email":
                return testEmail(config);
            case "dingtalk":
                return testDingtalk(config);
            case "wecom":
                return testWecom(config);
            default:
                log.warn("未知的通知渠道: {}", channel);
                return false;
        }
    }

    private boolean testEmail(AlertNotificationConfig config) {
        if (config.getEmailRecipients() == null || config.getEmailRecipients().isEmpty()) {
            log.warn("邮件收件人未配置");
            return false;
        }
        try {
            String[] recipients = config.getEmailRecipients().split(",");
            return mailService.sendAlertEmail(recipients, "[测试] ORIN 邮件通知", "这是一条测试通知，如果您收到此消息，说明邮件通道配置正常。");
        } catch (Exception e) {
            log.error("邮件测试发送失败", e);
            return false;
        }
    }

    private boolean testDingtalk(AlertNotificationConfig config) {
        if (config.getDingtalkWebhook() == null || config.getDingtalkWebhook().isEmpty()) {
            log.warn("钉钉 Webhook 未配置");
            return false;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            String markdownContent = "## ORIN 测试通知\n\n这是一条测试通知，如果您收到此消息，说明钉钉通道配置正常。";

            body.put("msgtype", "markdown");
            body.put("markdown", Map.of(
                "title", "ORIN 测试通知",
                "text", markdownContent
            ));

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Content-Type", "application/json");

            org.springframework.http.HttpEntity<Map<String, Object>> entity =
                new org.springframework.http.HttpEntity<>(body, headers);

            restTemplate.postForObject(config.getDingtalkWebhook(), entity, String.class);
            log.info("钉钉测试通知发送成功");
            return true;
        } catch (Exception e) {
            log.error("钉钉测试通知发送失败", e);
            return false;
        }
    }

    private boolean testWecom(AlertNotificationConfig config) {
        if (config.getWecomWebhook() == null || config.getWecomWebhook().isEmpty()) {
            log.warn("企业微信 Webhook 未配置");
            return false;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "text");
            body.put("text", Map.of(
                "content", "ORIN 测试通知\n这是一条测试通知，如果您收到此消息，说明企业微信通道配置正常。"
            ));

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Content-Type", "application/json");

            org.springframework.http.HttpEntity<Map<String, Object>> entity =
                new org.springframework.http.HttpEntity<>(body, headers);

            restTemplate.postForObject(config.getWecomWebhook(), entity, String.class);
            log.info("企业微信测试通知发送成功");
            return true;
        } catch (Exception e) {
            log.error("企业微信测试通知发送失败", e);
            return false;
        }
    }
}
