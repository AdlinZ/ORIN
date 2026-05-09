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
 * 启动时自动补齐运行时默认告警规则
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeAlertRuleInitializer {

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

    @Value("${orin.runtime.alert.rule.bootstrap.enabled:true}")
    private boolean runtimeBootstrapEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureRuleExists() {
        ensureCollabHealthRuleExists();
        ensureRuntimeRuleExists(
                "系统依赖健康告警",
                "SYSTEM_HEALTH",
                "status == DOWN",
                1.0,
                "ALL",
                null,
                5,
                1,
                "ERROR",
                5
        );
        ensureRuntimeRuleExists(
                "API 单次失败告警",
                "ERROR_RATE",
                "lastFailure == TRUE",
                1.0,
                "ALL",
                null,
                5,
                1,
                "ERROR",
                5
        );
    }

    private void ensureCollabHealthRuleExists() {
        if (!bootstrapEnabled) {
            return;
        }
        boolean exists = hasMatchingRule("COLLAB_HEALTH", "overallLevel == RED", "ALL", null);
        if (exists) {
            log.info("COLLAB_HEALTH default alert rule already exists, skip bootstrap.");
            return;
        }

        AlertRule rule = AlertRule.builder()
                .ruleName(ruleName)
                .ruleType("COLLAB_HEALTH")
                .conditionExpr("overallLevel == RED")
                .thresholdValue(1.0)
                .targetScope("ALL")
                .targetId(null)
                .metricWindowMinutes(5)
                .minSampleCount(1)
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

    private void ensureRuntimeRuleExists(String ruleName, String ruleType, String conditionExpr, Double thresholdValue,
                                         String targetScope, String targetId, Integer metricWindowMinutes,
                                         Integer minSampleCount, String severity, Integer cooldownMinutes) {
        if (!runtimeBootstrapEnabled) {
            return;
        }
        if (hasMatchingRule(ruleType, conditionExpr, targetScope, targetId)) {
            log.info("{} default alert rule already exists, skip bootstrap.", ruleType);
            return;
        }

        AlertRule rule = AlertRule.builder()
                .ruleName(ruleName)
                .ruleType(ruleType)
                .conditionExpr(conditionExpr)
                .thresholdValue(thresholdValue)
                .targetScope(normalizeTargetScope(targetScope))
                .targetId(blankToNull(targetId))
                .metricWindowMinutes(metricWindowMinutes != null && metricWindowMinutes > 0 ? metricWindowMinutes : 5)
                .minSampleCount(minSampleCount != null && minSampleCount > 0 ? minSampleCount : 1)
                .severity(severity)
                .enabled(true)
                .notificationChannels("")
                .recipientList("")
                .cooldownMinutes(cooldownMinutes != null && cooldownMinutes > 0 ? cooldownMinutes : 5)
                .build();

        AlertRule saved = alertRuleRepository.save(rule);
        log.warn("Bootstrapped {} alert rule: id={}, condition={}, target={}:{}",
                ruleType, saved.getId(), conditionExpr, rule.getTargetScope(), rule.getTargetId());
    }

    private boolean hasMatchingRule(String ruleType, String conditionExpr, String targetScope, String targetId) {
        String normalizedCondition = normalizeCondition(conditionExpr);
        String normalizedScope = normalizeTargetScope(targetScope);
        String normalizedTargetId = blankToNull(targetId);
        List<AlertRule> rules = alertRuleRepository.findByRuleType(ruleType);
        return rules.stream().anyMatch(rule ->
                normalizedCondition.equals(normalizeCondition(rule.getConditionExpr()))
                        && normalizedScope.equals(normalizeTargetScope(rule.getTargetScope()))
                        && equalsNullable(normalizedTargetId, blankToNull(rule.getTargetId())));
    }

    private String normalizeCondition(String conditionExpr) {
        return conditionExpr == null ? "" : conditionExpr.trim().replaceAll("\\s+", " ");
    }

    private String normalizeTargetScope(String targetScope) {
        return targetScope == null || targetScope.isBlank() ? "ALL" : targetScope.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
