package com.adlin.orin.modules.alert.service;

import com.adlin.orin.modules.alert.entity.AlertNotificationConfig;
import com.adlin.orin.modules.alert.entity.AlertRule;
import com.adlin.orin.modules.alert.repository.AlertNotificationConfigRepository;
import com.adlin.orin.modules.notification.service.SystemNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertNotificationServiceTest {

    @Mock
    private AlertNotificationConfigRepository configRepository;

    @Mock
    private AlertChannelGateway alertChannelUnifiedGateway;

    @Mock
    private SystemNotificationService systemNotificationService;

    private AlertNotificationService service;

    @BeforeEach
    void setUp() {
        service = new AlertNotificationService(configRepository, alertChannelUnifiedGateway, systemNotificationService);
        lenient().when(alertChannelUnifiedGateway.normalizeChannel(anyString())).thenAnswer(invocation -> invocation.getArgument(0, String.class).toLowerCase());
    }

    @Test
    void criticalOnlySkipsWarningNotifications() {
        AlertNotificationConfig config = baseConfig();
        config.setCriticalOnly(true);
        when(configRepository.findFirstConfig()).thenReturn(Optional.of(config));

        AlertRule rule = AlertRule.builder()
                .ruleName("Warn Rule")
                .severity("WARNING")
                .notificationChannels("EMAIL")
                .recipientList("ops@example.com")
                .build();

        service.sendRuleNotification(rule, "warn message");

        verifyNoInteractions(systemNotificationService);
        verify(alertChannelUnifiedGateway, never()).send(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void ruleNotificationSendsInAppAndEmailWhenEnabled() {
        AlertNotificationConfig config = baseConfig();
        when(configRepository.findFirstConfig()).thenReturn(Optional.of(config));
        when(alertChannelUnifiedGateway.send(eq("email"), eq(config), anyString(), anyString(), eq("ops@example.com"))).thenReturn(true);

        AlertRule rule = AlertRule.builder()
                .ruleName("Error Rule")
                .severity("ERROR")
                .notificationChannels("EMAIL")
                .recipientList("ops@example.com")
                .build();

        service.sendRuleNotification(rule, "error message");

        verify(systemNotificationService).sendMessage(contains("Error Rule"), contains("error message"), eq("ERROR"), isNull(), eq("ALERT"), eq("BROADCAST"));
        verify(alertChannelUnifiedGateway).send(eq("email"), eq(config), contains("Error Rule"), contains("error message"), eq("ops@example.com"));
    }

    @Test
    void ruleWithoutChannelsFallsBackToEnabledConfigChannels() {
        AlertNotificationConfig config = baseConfig();
        config.setDingtalkEnabled(true);
        config.setDingtalkWebhook("https://example.test/hook");
        when(configRepository.findFirstConfig()).thenReturn(Optional.of(config));

        AlertRule rule = AlertRule.builder()
                .ruleName("Fallback Rule")
                .severity("CRITICAL")
                .notificationChannels("")
                .build();

        service.sendRuleNotification(rule, "critical message");

        verify(alertChannelUnifiedGateway).send(eq("email"), eq(config), anyString(), anyString(), isNull());
        verify(alertChannelUnifiedGateway).send(eq("dingtalk"), eq(config), anyString(), anyString(), isNull());
    }

    private AlertNotificationConfig baseConfig() {
        AlertNotificationConfig config = new AlertNotificationConfig();
        config.setEmailEnabled(true);
        config.setEmailRecipients("fallback@example.com");
        config.setNotifyEmail(true);
        config.setNotifyInapp(true);
        config.setCriticalOnly(false);
        config.setDingtalkEnabled(false);
        config.setWecomEnabled(false);
        return config;
    }
}
