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
    private NotificationService notificationService;

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

        AlertHistory mockHistory = AlertHistory.builder()
                .id("history-1")
                .ruleId("rule-1")
                .agentId("agent-1")
                .alertMessage("Test alert")
                .severity("WARNING")
                .build();

        when(historyRepository.save(any(AlertHistory.class))).thenReturn(mockHistory);

        AlertHistory result = alertService.triggerAlert("rule-1", "agent-1", "Test alert");

        assertNotNull(result);
        assertEquals("Test alert", result.getAlertMessage());
        verify(notificationService).sendNotification(eq(testRule), eq("Test alert"));
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
}
