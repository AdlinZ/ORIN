package com.adlin.orin.modules.alert.service;

import com.adlin.orin.modules.alert.entity.AlertHistory;
import com.adlin.orin.modules.alert.entity.AlertRule;
import com.adlin.orin.modules.alert.repository.AlertHistoryRepository;
import com.adlin.orin.modules.alert.repository.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRuleRepository ruleRepository;

    @Mock
    private AlertHistoryRepository historyRepository;

    @Mock
    private AlertNotificationService notificationService;

    @InjectMocks
    private AlertService alertService;

    private AlertRule testRule;

    @BeforeEach
    void setUp() {
        testRule = AlertRule.builder()
                .id("rule-1")
                .ruleName("Test Rule")
                .ruleType("PERFORMANCE")
                .severity("WARNING")
                .enabled(true)
                .notificationChannels("EMAIL")
                .build();
    }

    @Test
    void testCreateRule() {
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);

        AlertRule result = alertService.createRule(testRule);

        assertNotNull(result);
        assertEquals("Test Rule", result.getRuleName());
        verify(ruleRepository).save(testRule);
    }

    @Test
    void testGetAllRules() {
        List<AlertRule> rules = Arrays.asList(testRule);
        when(ruleRepository.findAll()).thenReturn(rules);

        List<AlertRule> result = alertService.getAllRules();

        assertEquals(1, result.size());
        assertEquals("Test Rule", result.get(0).getRuleName());
    }

    @Test
    void testGetEnabledRules() {
        List<AlertRule> rules = Arrays.asList(testRule);
        when(ruleRepository.findByEnabledTrue()).thenReturn(rules);

        List<AlertRule> result = alertService.getEnabledRules();

        assertEquals(1, result.size());
        assertTrue(result.get(0).getEnabled());
    }

    @Test
    void testTriggerAlert() {
        when(ruleRepository.findById("rule-1")).thenReturn(Optional.of(testRule));
        when(historyRepository.findFirstByFingerprintAndStatusOrderByTriggeredAtDesc("PERFORMANCE:agent-1", "TRIGGERED"))
                .thenReturn(Optional.empty());

        when(historyRepository.save(any(AlertHistory.class))).thenAnswer(invocation -> {
            AlertHistory history = invocation.getArgument(0);
            history.setId("history-1");
            return history;
        });

        AlertHistory result = alertService.triggerAlert("rule-1", "agent-1", "Test alert");

        assertNotNull(result);
        assertEquals("Test alert", result.getAlertMessage());
        assertEquals("PERFORMANCE:agent-1", result.getFingerprint());
        verify(notificationService).sendRuleNotification(eq(testRule), eq("Test alert"), eq("PERFORMANCE:agent-1"), eq(1));
    }

    @Test
    void testTriggerAlertUpdatesActiveInstance() {
        AlertHistory active = AlertHistory.builder()
                .id("history-1")
                .ruleId("rule-1")
                .ruleName("Test Rule")
                .agentId("agent-1")
                .fingerprint("PERFORMANCE:agent-1")
                .alertMessage("old")
                .severity("WARNING")
                .status("TRIGGERED")
                .repeatCount(2)
                .build();

        when(ruleRepository.findById("rule-1")).thenReturn(Optional.of(testRule));
        when(historyRepository.findFirstByFingerprintAndStatusOrderByTriggeredAtDesc("PERFORMANCE:agent-1", "TRIGGERED"))
                .thenReturn(Optional.of(active));
        when(historyRepository.save(any(AlertHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertHistory result = alertService.triggerAlert("rule-1", "agent-1", "new alert");

        assertEquals("new alert", result.getAlertMessage());
        assertEquals(3, result.getRepeatCount());
        assertNotNull(result.getLastTriggeredAt());
        verify(notificationService).updateRuleNotification(eq(testRule), eq("new alert"), eq("PERFORMANCE:agent-1"), eq(3));
        verify(notificationService, never()).sendRuleNotification(any(), anyString(), anyString(), anyInt());
    }

    @Test
    void testResolveAlert() {
        AlertHistory history = AlertHistory.builder()
                .id("history-1")
                .status("TRIGGERED")
                .build();

        when(historyRepository.findById("history-1")).thenReturn(Optional.of(history));
        when(historyRepository.save(any(AlertHistory.class))).thenReturn(history);

        AlertHistory result = alertService.resolveAlert("history-1");

        assertEquals("RESOLVED", result.getStatus());
        assertNotNull(result.getResolvedAt());
    }

    @Test
    void matchesSimpleTextAndNumericRuleConditions() {
        AlertRule levelRule = AlertRule.builder()
                .ruleName("Collab Red")
                .enabled(true)
                .conditionExpr("overallLevel == RED")
                .build();
        AlertRule latencyRule = AlertRule.builder()
                .ruleName("P95 High")
                .enabled(true)
                .conditionExpr("p95LatencyMs >= 60000")
                .build();
        Map<String, Object> context = Map.of(
                "overallLevel", "RED",
                "p95LatencyMs", 62000.0
        );

        assertTrue(alertService.matchesRuleCondition(levelRule, context));
        assertTrue(alertService.matchesRuleCondition(latencyRule, context));
    }

    @Test
    void rejectsNonMatchingRuleCondition() {
        AlertRule successRule = AlertRule.builder()
                .ruleName("Success Low")
                .enabled(true)
                .conditionExpr("successRate <= 0.70")
                .build();

        assertFalse(alertService.matchesRuleCondition(successRule, Map.of("successRate", 0.95)));
    }

    @Test
    void systemHealthRulesMatchSpecificDependencyTargets() {
        AlertRule mysqlRule = AlertRule.builder()
                .id("mysql-rule")
                .ruleName("MySQL Down")
                .ruleType("SYSTEM_HEALTH")
                .targetScope("DEPENDENCY")
                .targetId("MYSQL")
                .conditionExpr("status == DOWN")
                .severity("ERROR")
                .enabled(true)
                .build();
        AlertRule redisRule = AlertRule.builder()
                .id("redis-rule")
                .ruleName("Redis Down")
                .ruleType("SYSTEM_HEALTH")
                .targetScope("DEPENDENCY")
                .targetId("REDIS")
                .conditionExpr("status == DOWN")
                .severity("ERROR")
                .enabled(true)
                .build();

        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(mysqlRule, redisRule));
        when(ruleRepository.findById("redis-rule")).thenReturn(Optional.of(redisRule));
        when(historyRepository.findFirstByFingerprintAndStatusOrderByTriggeredAtDesc("SYSTEM_HEALTH:REDIS", "TRIGGERED"))
                .thenReturn(Optional.empty());
        when(historyRepository.save(any(AlertHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int triggered = alertService.triggerSystemAlert("SYSTEM_HEALTH", "REDIS", "Redis down", null,
                Map.of("dependency", "REDIS", "status", "DOWN"));

        assertEquals(1, triggered);
        verify(ruleRepository, never()).findById("mysql-rule");
        verify(notificationService).sendRuleNotification(eq(redisRule), eq("Redis down"), eq("SYSTEM_HEALTH:REDIS"), eq(1));
    }

    @Test
    void errorRateRuleRequiresMinimumSampleCount() {
        AlertRule rateRule = AlertRule.builder()
                .ruleName("Provider Error Rate")
                .ruleType("ERROR_RATE")
                .conditionExpr("errorRate >= 0.30")
                .minSampleCount(10)
                .enabled(true)
                .build();

        assertFalse(alertService.matchesRuleCondition(rateRule, Map.of("errorRate", 0.5, "totalCount", 5)));
        assertTrue(alertService.matchesRuleCondition(rateRule, Map.of("errorRate", 0.5, "totalCount", 10)));
    }

    @Test
    void errorRateAlertSupportsSingleCountAndRateRules() {
        AlertRule singleRule = AlertRule.builder()
                .id("single-rule")
                .ruleName("Single Failure")
                .ruleType("ERROR_RATE")
                .conditionExpr("lastFailure == TRUE")
                .targetScope("ALL")
                .severity("ERROR")
                .enabled(true)
                .build();
        AlertRule countRule = AlertRule.builder()
                .id("count-rule")
                .ruleName("Failure Count")
                .ruleType("ERROR_RATE")
                .conditionExpr("errorCount >= 3")
                .metricWindowMinutes(10)
                .targetScope("PROVIDER")
                .targetId("provider-a")
                .severity("ERROR")
                .enabled(true)
                .build();
        AlertRule rateRule = AlertRule.builder()
                .id("rate-rule")
                .ruleName("Failure Rate")
                .ruleType("ERROR_RATE")
                .conditionExpr("errorRate >= 0.50")
                .metricWindowMinutes(10)
                .minSampleCount(5)
                .targetScope("PROVIDER")
                .targetId("provider-a")
                .severity("WARNING")
                .enabled(true)
                .build();

        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(singleRule, countRule, rateRule));
        when(ruleRepository.findById("single-rule")).thenReturn(Optional.of(singleRule));
        when(ruleRepository.findById("count-rule")).thenReturn(Optional.of(countRule));
        when(ruleRepository.findById("rate-rule")).thenReturn(Optional.of(rateRule));
        when(historyRepository.findFirstByFingerprintAndStatusOrderByTriggeredAtDesc("ERROR_RATE:provider-a", "TRIGGERED"))
                .thenReturn(Optional.empty());
        when(historyRepository.save(any(AlertHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int triggered = alertService.triggerErrorRateAlert("provider-a", "failed", "trace-1", window -> Map.of(
                "providerId", "provider-a",
                "lastFailure", true,
                "totalCount", 6,
                "errorCount", 3,
                "errorRate", 0.5,
                "metricWindowMinutes", window
        ));

        assertEquals(3, triggered);
        verify(notificationService, times(3)).sendRuleNotification(any(AlertRule.class), eq("failed"),
                eq("ERROR_RATE:provider-a"), eq(1));
    }
}
