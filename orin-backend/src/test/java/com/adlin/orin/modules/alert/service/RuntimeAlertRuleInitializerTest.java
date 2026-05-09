package com.adlin.orin.modules.alert.service;

import com.adlin.orin.modules.alert.entity.AlertRule;
import com.adlin.orin.modules.alert.repository.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeAlertRuleInitializerTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    private RuntimeAlertRuleInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new RuntimeAlertRuleInitializer(alertRuleRepository);
        ReflectionTestUtils.setField(initializer, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(initializer, "ruleName", "协作健康告警规则");
        ReflectionTestUtils.setField(initializer, "severity", "CRITICAL");
        ReflectionTestUtils.setField(initializer, "channels", "");
        ReflectionTestUtils.setField(initializer, "recipients", "");
        ReflectionTestUtils.setField(initializer, "cooldownMinutes", 15);
        ReflectionTestUtils.setField(initializer, "runtimeBootstrapEnabled", true);
    }

    @Test
    void bootstrapsRuntimeRulesWhenMissing() {
        when(alertRuleRepository.findByRuleType(anyString())).thenReturn(List.of());
        when(alertRuleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        initializer.ensureRuleExists();

        ArgumentCaptor<AlertRule> captor = ArgumentCaptor.forClass(AlertRule.class);
        verify(alertRuleRepository, times(3)).save(captor.capture());

        Map<String, AlertRule> rulesByType = captor.getAllValues().stream()
                .collect(Collectors.toMap(AlertRule::getRuleType, rule -> rule));
        assertEquals("overallLevel == RED", rulesByType.get("COLLAB_HEALTH").getConditionExpr());
        assertEquals("status == DOWN", rulesByType.get("SYSTEM_HEALTH").getConditionExpr());
        assertEquals("lastFailure == TRUE", rulesByType.get("ERROR_RATE").getConditionExpr());
        assertTrue(rulesByType.values().stream().allMatch(rule -> Boolean.TRUE.equals(rule.getEnabled())));
    }

    @Test
    void doesNotDuplicateMatchingRules() {
        when(alertRuleRepository.findByRuleType("COLLAB_HEALTH")).thenReturn(List.of(AlertRule.builder()
                .ruleType("COLLAB_HEALTH")
                .conditionExpr("overallLevel == RED")
                .targetScope("ALL")
                .enabled(false)
                .build()));
        when(alertRuleRepository.findByRuleType("SYSTEM_HEALTH")).thenReturn(List.of(AlertRule.builder()
                .ruleType("SYSTEM_HEALTH")
                .conditionExpr("status == DOWN")
                .targetScope("ALL")
                .enabled(false)
                .build()));
        when(alertRuleRepository.findByRuleType("ERROR_RATE")).thenReturn(List.of(AlertRule.builder()
                .ruleType("ERROR_RATE")
                .conditionExpr("lastFailure == TRUE")
                .targetScope("ALL")
                .enabled(false)
                .build()));

        initializer.ensureRuleExists();

        verify(alertRuleRepository, never()).save(any(AlertRule.class));
    }
}
