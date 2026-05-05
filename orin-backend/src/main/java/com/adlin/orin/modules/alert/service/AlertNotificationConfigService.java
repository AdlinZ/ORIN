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
        applyDefaults(config);
        return repository.save(config);
    }

    /**
     * 获取配置状态
     */
    public Map<String, Object> getStatus() {
        AlertNotificationConfig config = getConfig();
        Map<String, Object> status = new HashMap<>();

        Map<String, Object> email = new HashMap<>();
        email.put("enabled", config.getEmailEnabled() != null && config.getEmailEnabled());
        email.put("configured", config.getEmailRecipients() != null && !config.getEmailRecipients().isEmpty());
        email.put("recipients", config.getEmailRecipients());
        status.put("email", email);

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

    private void applyDefaults(AlertNotificationConfig config) {
        if (config.getEmailEnabled() == null) config.setEmailEnabled(true);
        if (config.getDingtalkEnabled() == null) config.setDingtalkEnabled(false);
        if (config.getWecomEnabled() == null) config.setWecomEnabled(false);
        if (config.getCriticalOnly() == null) config.setCriticalOnly(false);
        if (config.getInstantPush() == null) config.setInstantPush(true);
        if (config.getMergeIntervalMinutes() == null) config.setMergeIntervalMinutes(0);
        if (config.getDesktopNotification() == null) config.setDesktopNotification(true);
        if (config.getNotifyEmail() == null) config.setNotifyEmail(true);
        if (config.getNotifyInapp() == null) config.setNotifyInapp(true);
    }
}
