package com.adlin.orin.modules.alert.service;

import com.adlin.orin.modules.alert.entity.AlertHistory;
import com.adlin.orin.modules.alert.entity.AlertRule;
import com.adlin.orin.modules.alert.repository.AlertHistoryRepository;
import com.adlin.orin.modules.alert.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 告警服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private static final Pattern SIMPLE_CONDITION = Pattern.compile(
            "^\\s*([A-Za-z][A-Za-z0-9_]*)\\s*(==|!=|>=|<=|>|<)\\s*(.+?)\\s*$");

    private final AlertRuleRepository ruleRepository;
    private final AlertHistoryRepository historyRepository;
    private final AlertNotificationService notificationService;

    /**
     * 创建告警规则
     */
    @Transactional
    public AlertRule createRule(AlertRule rule) {
        normalizeRuleDefaults(rule);
        rule = ruleRepository.save(rule);
        log.info("Created alert rule: {}", rule.getRuleName());
        return rule;
    }

    /**
     * 获取所有规则
     */
    public List<AlertRule> getAllRules() {
        return ruleRepository.findAll();
    }

    /**
     * 获取启用的规则
     */
    public List<AlertRule> getEnabledRules() {
        return ruleRepository.findByEnabledTrue();
    }

    /**
     * 更新规则
     */
    @Transactional
    public AlertRule updateRule(String id, AlertRule updatedRule) {
        AlertRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found: " + id));

        rule.setRuleName(updatedRule.getRuleName());
        rule.setRuleType(updatedRule.getRuleType());
        rule.setConditionExpr(updatedRule.getConditionExpr());
        rule.setThresholdValue(updatedRule.getThresholdValue());
        rule.setTargetScope(updatedRule.getTargetScope());
        rule.setTargetId(updatedRule.getTargetId());
        rule.setMetricWindowMinutes(updatedRule.getMetricWindowMinutes());
        rule.setMinSampleCount(updatedRule.getMinSampleCount());
        rule.setSeverity(updatedRule.getSeverity());
        rule.setEnabled(updatedRule.getEnabled());
        rule.setNotificationChannels(updatedRule.getNotificationChannels());
        rule.setRecipientList(updatedRule.getRecipientList());
        rule.setCooldownMinutes(updatedRule.getCooldownMinutes());

        normalizeRuleDefaults(rule);
        return ruleRepository.save(rule);
    }

    /**
     * 删除规则
     */
    @Transactional
    public void deleteRule(String id) {
        ruleRepository.deleteById(id);
        log.info("Deleted alert rule: {}", id);
    }

    /**
     * 触发告警
     * 同一规则类型和目标只保留一个活跃告警实例
     */
    @Transactional
    public AlertHistory triggerAlert(String ruleId, String agentId, String message) {
        return triggerAlert(ruleId, agentId, message, null);
    }

    /**
     * 触发告警（带 traceId）
     * 同一规则类型和目标只保留一个活跃告警实例
     */
    @Transactional
    public AlertHistory triggerAlert(String ruleId, String agentId, String message, String traceId) {
        AlertRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Rule not found: " + ruleId));

        String fingerprint = buildFingerprint(rule.getRuleType(), agentId);
        LocalDateTime now = LocalDateTime.now();
        Optional<AlertHistory> active = historyRepository.findFirstByFingerprintAndStatusOrderByTriggeredAtDesc(
                fingerprint, "TRIGGERED");
        if (active.isPresent()) {
            AlertHistory history = active.get();
            history.setAlertMessage(message);
            history.setTraceId(traceId);
            history.setLastTriggeredAt(now);
            history.setRepeatCount(safeRepeatCount(history.getRepeatCount()) + 1);
            history = historyRepository.save(history);
            notificationService.updateRuleNotification(rule, message, fingerprint, history.getRepeatCount());
            log.info("Alert instance updated: fingerprint={}, repeats={}", fingerprint, history.getRepeatCount());
            return history;
        }

        // 创建告警历史
        AlertHistory history = AlertHistory.builder()
                .ruleId(ruleId)
                .ruleName(rule.getRuleName())
                .agentId(agentId)
                .traceId(traceId)
                .fingerprint(fingerprint)
                .alertMessage(message)
                .severity(rule.getSeverity())
                .status("TRIGGERED")
                .repeatCount(1)
                .lastTriggeredAt(now)
                .build();

        history = historyRepository.save(history);

        // 发送通知
        try {
            notificationService.sendRuleNotification(rule, message, fingerprint, history.getRepeatCount());
            log.info("Alert triggered and notification sent: {}", message);
        } catch (Exception e) {
            log.error("Failed to send alert notification", e);
        }

        return history;
    }

    /**
     * 触发系统级告警 (无具体规则ID，依据类型兜底匹配)
     * 依据规则类型和目标聚合为同一个活跃告警实例
     */
    @Transactional
    public void triggerSystemAlert(String ruleType, String agentId, String message) {
        triggerSystemAlert(ruleType, agentId, message, null);
    }

    /**
     * 触发系统级告警（带 traceId）
     * 依据规则类型和目标聚合为同一个活跃告警实例
     */
    @Transactional
    public void triggerSystemAlert(String ruleType, String agentId, String message, String traceId) {
        triggerSystemAlert(ruleType, agentId, message, traceId, null);
    }

    @Transactional
    public int triggerSystemAlert(String ruleType, String agentId, String message, String traceId,
                                  Map<String, Object> context) {
        List<AlertRule> candidateRules = ruleRepository.findByEnabledTrue().stream()
                .filter(r -> ruleType.equals(r.getRuleType()))
                .filter(r -> matchesRuleTarget(r, agentId, context))
                .toList();
        List<AlertRule> rules = candidateRules.stream()
                .filter(r -> matchesRuleCondition(r, context))
                .toList();

        if (!rules.isEmpty()) {
            // Trigger alert for matched rules
            for (AlertRule rule : rules) {
                triggerAlert(rule.getId(), agentId, message, traceId);
            }
            return rules.size();
        }

        if (!candidateRules.isEmpty()) {
            log.debug("System alert skipped because no enabled {} rule matched context={}", ruleType, context);
            return 0;
        }

        // Fallback: create a generic alert history if no rule is configured
        log.warn("No active rule found for type {}. Generating generic alert.", ruleType);
        String fingerprint = buildFingerprint(ruleType, agentId);
        LocalDateTime now = LocalDateTime.now();
        Optional<AlertHistory> active = historyRepository.findFirstByFingerprintAndStatusOrderByTriggeredAtDesc(
                fingerprint, "TRIGGERED");
        if (active.isPresent()) {
            AlertHistory history = active.get();
            history.setAlertMessage(message);
            history.setTraceId(traceId);
            history.setLastTriggeredAt(now);
            history.setRepeatCount(safeRepeatCount(history.getRepeatCount()) + 1);
            historyRepository.save(history);
            notificationService.updateSystemNotification("系统默认告警", "WARNING", message,
                    fingerprint, history.getRepeatCount());
            return 1;
        }
        AlertHistory history = AlertHistory.builder()
                .ruleId("SYSTEM_DEFAULT")
                .ruleName("系统默认告警")
                .agentId(agentId)
                .traceId(traceId)
                .fingerprint(fingerprint)
                .alertMessage(message)
                .severity("WARNING") // Default severity
                .status("TRIGGERED")
                .repeatCount(1)
                .lastTriggeredAt(now)
                .build();

        historyRepository.save(history);
        try {
            notificationService.sendSystemNotification("系统默认告警", "WARNING", message,
                    fingerprint, history.getRepeatCount());
        } catch (Exception e) {
            log.error("Failed to send generic system alert notification", e);
        }
        return 1;
    }

    public boolean matchesSystemAlertRule(String ruleType, Map<String, Object> context) {
        return ruleRepository.findByEnabledTrue().stream()
                .filter(rule -> ruleType.equals(rule.getRuleType()))
                .filter(rule -> matchesRuleTarget(rule, null, context))
                .anyMatch(rule -> matchesRuleCondition(rule, context));
    }

    @Transactional
    public int triggerErrorRateAlert(String providerId, String message, String traceId,
                                     Function<Integer, Map<String, Object>> contextFactory) {
        List<AlertRule> candidateRules = ruleRepository.findByEnabledTrue().stream()
                .filter(rule -> "ERROR_RATE".equals(rule.getRuleType()))
                .filter(rule -> matchesRuleTarget(rule, providerId, Map.of("providerId", providerId)))
                .toList();
        int triggered = 0;
        for (AlertRule rule : candidateRules) {
            int windowMinutes = rule.getMetricWindowMinutes() != null && rule.getMetricWindowMinutes() > 0
                    ? rule.getMetricWindowMinutes()
                    : 5;
            Map<String, Object> context = contextFactory.apply(windowMinutes);
            if (matchesRuleCondition(rule, context)) {
                triggerAlert(rule.getId(), providerId, message, traceId);
                triggered++;
            }
        }
        if (triggered == 0 && candidateRules.isEmpty()) {
            triggerSystemAlert("ERROR_RATE", providerId, message, traceId, Map.of(
                    "providerId", providerId,
                    "lastFailure", true
            ));
            return 1;
        }
        return triggered;
    }

    @Transactional
    public void resolveSystemAlert(String ruleType, String agentId, String message) {
        String fingerprint = buildFingerprint(ruleType, agentId);
        LocalDateTime now = LocalDateTime.now();
        Optional<AlertHistory> active = historyRepository.findFirstByFingerprintAndStatusOrderByTriggeredAtDesc(
                fingerprint, "TRIGGERED");
        if (active.isEmpty()) {
            return;
        }

        AlertHistory history = active.get();
        history.setStatus("RESOLVED");
        history.setResolvedAt(now);
        history.setLastTriggeredAt(now);
        history.setAlertMessage(message);
        historyRepository.save(history);

        ruleRepository.findByEnabledTrue().stream()
                .filter(r -> ruleType.equals(r.getRuleType()))
                .filter(r -> matchesRuleTarget(r, agentId, null))
                .findFirst()
                .ifPresentOrElse(
                        rule -> notificationService.resolveRuleNotification(rule, message, fingerprint, history.getRepeatCount()),
                        () -> notificationService.resolveSystemNotification("系统默认告警", message, fingerprint, history.getRepeatCount())
                );
        log.info("Alert instance resolved: fingerprint={}", fingerprint);
    }

    /**
     * 解决告警
     */
    @Transactional
    public AlertHistory resolveAlert(String historyId) {
        AlertHistory history = historyRepository.findById(historyId)
                .orElseThrow(() -> new RuntimeException("Alert history not found: " + historyId));

        history.setStatus("RESOLVED");
        history.setResolvedAt(LocalDateTime.now());

        return historyRepository.save(history);
    }

    /**
     * 标记所有未解决告警为已解决
     */
    @Transactional
    public int resolveAllAlerts() {
        List<AlertHistory> unresolvedAlerts = historyRepository.findByStatusOrderByTriggeredAtDesc("TRIGGERED");
        LocalDateTime now = LocalDateTime.now();

        for (AlertHistory alert : unresolvedAlerts) {
            alert.setStatus("RESOLVED");
            alert.setResolvedAt(now);
        }

        historyRepository.saveAll(unresolvedAlerts);
        log.info("Resolved {} alerts", unresolvedAlerts.size());
        return unresolvedAlerts.size();
    }

    /**
     * 清空所有告警历史
     */
    @Transactional
    public void clearAllAlerts() {
        historyRepository.deleteAll();
        log.info("Cleared all alert history");
    }

    /**
     * 获取未读告警数量
     */
    public long getUnreadCount() {
        return historyRepository.countByStatusIn(List.of("TRIGGERED"));
    }

    /**
     * 获取告警历史
     */
    public Page<AlertHistory> getAlertHistory(Pageable pageable) {
        return historyRepository.findAllByOrderByTriggeredAtDesc(pageable);
    }

    /**
     * 按智能体获取告警历史
     */
    public List<AlertHistory> getAlertHistoryByAgent(String agentId) {
        return historyRepository.findByAgentIdOrderByTriggeredAtDesc(agentId);
    }

    /**
     * 测试告警通知
     */
    public void testNotification(AlertRule rule) {
        String testMessage = "这是一条测试告警消息 - " + LocalDateTime.now();
        notificationService.sendRuleNotification(rule, testMessage);
    }

    /**
     * 获取统计信息
     */
    public AlertStats getStats() {
        long totalRules = ruleRepository.count();
        long enabledRules = ruleRepository.findByEnabledTrue().size();
        long activeAlerts = historyRepository.countByStatus("TRIGGERED");
        long totalAlerts = historyRepository.count();

        return new AlertStats(totalRules, enabledRules, activeAlerts, totalAlerts);
    }

    /**
     * 告警统计信息
     */
    public record AlertStats(
            long totalRules,
            long enabledRules,
            long activeAlerts,
            long totalAlerts) {
    }

    private String buildFingerprint(String ruleType, String agentId) {
        String normalizedRuleType = ruleType != null && !ruleType.isBlank() ? ruleType : "UNKNOWN";
        String normalizedAgentId = agentId != null && !agentId.isBlank() ? agentId : "GLOBAL";
        return normalizedRuleType + ":" + normalizedAgentId;
    }

    boolean matchesRuleCondition(AlertRule rule, Map<String, Object> context) {
        if (rule == null || !Boolean.TRUE.equals(rule.getEnabled())) {
            return false;
        }
        if (!matchesMinimumSample(rule, context)) {
            return false;
        }
        String expr = rule.getConditionExpr();
        if (expr == null || expr.isBlank() || context == null || context.isEmpty()) {
            return true;
        }

        Matcher matcher = SIMPLE_CONDITION.matcher(expr);
        if (!matcher.matches()) {
            log.warn("Unsupported alert condition expression: rule={}, expr={}", rule.getRuleName(), expr);
            return false;
        }

        Object actual = context.get(matcher.group(1));
        if (actual == null) {
            return false;
        }

        String operator = matcher.group(2);
        String expectedRaw = stripQuotes(matcher.group(3));
        Optional<Double> actualNumber = toDouble(actual);
        Optional<Double> expectedNumber = toDouble(expectedRaw);
        if (actualNumber.isPresent() && expectedNumber.isPresent()) {
            return compareNumbers(actualNumber.get(), expectedNumber.get(), operator);
        }

        int textCompare = String.valueOf(actual).trim().compareToIgnoreCase(expectedRaw);
        return switch (operator) {
            case "==" -> textCompare == 0;
            case "!=" -> textCompare != 0;
            default -> false;
        };
    }

    private boolean matchesMinimumSample(AlertRule rule, Map<String, Object> context) {
        if (!"ERROR_RATE".equals(rule.getRuleType())
                || rule.getConditionExpr() == null
                || !rule.getConditionExpr().contains("errorRate")) {
            return true;
        }
        int minSampleCount = rule.getMinSampleCount() != null && rule.getMinSampleCount() > 0
                ? rule.getMinSampleCount()
                : 1;
        double totalCount = toDouble(context != null ? context.get("totalCount") : null).orElse(0.0);
        return totalCount >= minSampleCount;
    }

    private boolean matchesRuleTarget(AlertRule rule, String agentId, Map<String, Object> context) {
        String scope = normalizeTargetScope(rule != null ? rule.getTargetScope() : null);
        String targetId = normalizeTargetId(rule != null ? rule.getTargetId() : null);
        if ("ALL".equals(scope) || targetId == null) {
            return true;
        }

        String actual = switch (scope) {
            case "DEPENDENCY" -> firstNonBlank(contextValue(context, "dependency"), agentId);
            case "PROVIDER" -> firstNonBlank(contextValue(context, "providerId"), agentId);
            default -> null;
        };
        return actual != null && targetId.equals(normalizeTargetId(actual));
    }

    private void normalizeRuleDefaults(AlertRule rule) {
        if (rule == null) {
            return;
        }
        rule.setTargetScope(normalizeTargetScope(rule.getTargetScope()));
        rule.setTargetId(blankToNull(rule.getTargetId()));
        if (rule.getMetricWindowMinutes() == null || rule.getMetricWindowMinutes() < 1) {
            rule.setMetricWindowMinutes(5);
        }
        if (rule.getMinSampleCount() == null || rule.getMinSampleCount() < 1) {
            rule.setMinSampleCount(1);
        }
    }

    private String normalizeTargetScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "ALL";
        }
        return scope.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeTargetId(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = blankToNull(first);
        return normalizedFirst != null ? normalizedFirst : blankToNull(second);
    }

    private String contextValue(Map<String, Object> context, String key) {
        if (context == null || !context.containsKey(key)) {
            return null;
        }
        Object value = context.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean compareNumbers(double actual, double expected, String operator) {
        return switch (operator) {
            case ">" -> actual > expected;
            case ">=" -> actual >= expected;
            case "<" -> actual < expected;
            case "<=" -> actual <= expected;
            case "==" -> Double.compare(actual, expected) == 0;
            case "!=" -> Double.compare(actual, expected) != 0;
            default -> false;
        };
    }

    private Optional<Double> toDouble(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.doubleValue());
        }
        try {
            return Optional.of(Double.parseDouble(String.valueOf(value).trim()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String stripQuotes(String value) {
        String trimmed = value == null ? "" : value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private int safeRepeatCount(Integer repeatCount) {
        return repeatCount != null && repeatCount > 0 ? repeatCount : 1;
    }
}
