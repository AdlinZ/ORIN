package com.adlin.orin.modules.alert.service.channel;

import com.adlin.orin.modules.alert.entity.AlertNotificationConfig;
import com.adlin.orin.modules.system.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 邮件告警渠道。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailAlertChannelSender implements AlertChannelSender {

    private final MailService mailService;

    @Override
    public String channel() {
        return "email";
    }

    @Override
    public boolean send(AlertNotificationConfig config, String title, String content, String receiverOverride) {
        String recipientsRaw = firstNonBlank(receiverOverride, config != null ? config.getEmailRecipients() : null);
        if (recipientsRaw == null || recipientsRaw.isBlank()) {
            log.warn("邮件通知发送失败: 收件人为空");
            return false;
        }
        try {
            String[] recipients = Arrays.stream(recipientsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
            if (recipients.length == 0) {
                log.warn("邮件通知发送失败: 收件人为空");
                return false;
            }
            return mailService.sendAlertEmail(recipients, title, content);
        } catch (Exception e) {
            log.error("邮件通知发送失败", e);
            return false;
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary : fallback;
    }
}
