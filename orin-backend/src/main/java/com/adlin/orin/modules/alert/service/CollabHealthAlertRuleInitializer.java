package com.adlin.orin.modules.alert.service;

import com.adlin.orin.modules.alert.entity.AlertRule;
import com.adlin.orin.modules.alert.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时自动补齐协作健康告警规则
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollabHealthAlertRuleInitializer {

    private final AlertRuleRepository alertRuleRepository;

    @Value("${orin.collab.alert.rule.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;

    @Value("${orin.collab.alert.rule.name:协作健康告警规则}")
    private String ruleName;

    @Value("${orin.collab.alert.rule.severity:CRITICAL}")
    private String severity;

    @Value("${orin.collab.alert.rule.channels:}")
    private String channels;

    @Value("${orin.collab.alert.rule.recipients:}")
    private String recipients;

    @Value("${orin.collab.alert.rule.cooldown-minutes:15}")
    private Integer cooldownMinutes;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureRuleExists() {
        if (!bootstrapEnabled) {
            return;
        }

        List<AlertRule> rules = alertRuleRepository.findByRuleType("COLLAB_HEALTH");
        boolean exists = rules.stream().anyMatch(r -> Boolean.TRUE.equals(r.getEnabled()));
        if (exists) {
            log.info("COLLAB_HEALTH alert rule already exists, skip bootstrap.");
            return;
        }

        AlertRule rule = AlertRule.builder()
                .ruleName(ruleName)
                .ruleType("COLLAB_HEALTH")
                .conditionExpr("overallLevel == RED")
                .thresholdValue(1.0)
                .severity(severity)
                .enabled(true)
                .notificationChannels(channels)
                .recipientList(recipients)
                .cooldownMinutes(cooldownMinutes != null && cooldownMinutes > 0 ? cooldownMinutes : 15)
                .build();

        AlertRule saved = alertRuleRepository.save(rule);
        log.warn("Bootstrapped COLLAB_HEALTH alert rule: id={}, channels={}, recipients={}",
                saved.getId(), channels, recipients);
    }
}
