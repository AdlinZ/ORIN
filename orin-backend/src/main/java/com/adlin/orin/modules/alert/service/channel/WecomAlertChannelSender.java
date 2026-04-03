package com.adlin.orin.modules.alert.service.channel;

import com.adlin.orin.modules.alert.entity.AlertNotificationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信告警渠道。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WecomAlertChannelSender implements AlertChannelSender {

    private final RestTemplate restTemplate;
    private final WebhookSecurityValidator webhookSecurityValidator;

    @Override
    public String channel() {
        return "wecom";
    }

    @Override
    public boolean send(AlertNotificationConfig config, String title, String content, String receiverOverride) {
        String webhookRaw = firstNonBlank(receiverOverride, config != null ? config.getWecomWebhook() : null);
        if (webhookRaw == null || webhookRaw.isBlank()) {
            log.warn("企业微信通知发送失败: Webhook 未配置");
            return false;
        }
        boolean allSuccess = true;
        for (String webhook : Arrays.stream(webhookRaw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()) {
            try {
                webhookSecurityValidator.validate(webhook);
                Map<String, Object> body = new HashMap<>();
                body.put("msgtype", "text");
                body.put("text", Map.of(
                    "content", title + "\n" + content
                ));

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                restTemplate.postForObject(webhook, entity, String.class);
            } catch (Exception e) {
                allSuccess = false;
                log.error("企业微信通知发送失败: {}", e.getMessage());
            }
        }
        return allSuccess;
    }

    private String firstNonBlank(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary : fallback;
    }
}
