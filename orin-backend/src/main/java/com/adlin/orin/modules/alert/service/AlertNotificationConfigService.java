package com.adlin.orin.modules.alert.service;

import com.adlin.orin.modules.alert.entity.AlertNotificationConfig;
import com.adlin.orin.modules.alert.repository.AlertNotificationConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 告警通知配置服务
 */
@Service
@RequiredArgsConstructor
public class AlertNotificationConfigService {

    private final AlertNotificationConfigRepository repository;
    private final AlertNotificationService alertNotificationService;

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
        return alertNotificationService.sendTestNotification(channel);
    }
}
