package com.adlin.orin.modules.collaboration.task;

import com.adlin.orin.modules.alert.service.AlertService;
import com.adlin.orin.modules.collaboration.dto.CollabSessionDtos;
import com.adlin.orin.modules.collaboration.service.CollaborationSessionService;
import com.adlin.orin.modules.notification.service.SystemNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 协作会话指标自动告警巡检器
 *
 * 策略：
 * - 每轮采集会话指标
 * - 仅在 overallLevel=RED 时累加连续计数
 * - 连续 RED 达阈值且超过冷却窗口后，发送告警
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollaborationMetricsAlertScheduler {

    private static final String RED_STREAK_KEY = "collab:metrics:alert:red_streak";
    private static final String LAST_ALERT_TS_KEY = "collab:metrics:alert:last_sent_ts";

    private final CollaborationSessionService sessionService;
    private final SystemNotificationService systemNotificationService;
    private final AlertService alertService;
    private final StringRedisTemplate redisTemplate;

    @Value("${orin.collab.metrics.alert.scheduler.enabled:true}")
    private boolean enabled;

    @Value("${orin.collab.metrics.alert.scheduler.window-hours:24}")
    private int windowHours;

    @Value("${orin.collab.metrics.alert.scheduler.red-consecutive-threshold:5}")
    private int redConsecutiveThreshold;

    @Value("${orin.collab.metrics.alert.scheduler.cooldown-seconds:900}")
    private int cooldownSeconds;

    @Value("${orin.collab.metrics.alert.scheduler.fixed-delay-ms:60000}")
    private long fixedDelayMs;

    @Scheduled(fixedDelayString = "${orin.collab.metrics.alert.scheduler.fixed-delay-ms:60000}")
    public void inspectAndAlert() {
        if (!enabled) {
            return;
        }

        try {
            CollabSessionDtos.SessionMetricsView metrics = sessionService.getSessionMetrics(windowHours);
            String overallLevel = metrics.getOverallLevel() != null ? metrics.getOverallLevel() : "GREEN";

            if (!"RED".equalsIgnoreCase(overallLevel)) {
                resetRedStreak();
                return;
            }

            long streak = incrementRedStreak();
            if (streak < redConsecutiveThreshold) {
                log.warn("Collaboration metrics RED detected, waiting for threshold. streak={}/{}", streak, redConsecutiveThreshold);
                return;
            }

            long now = Instant.now().getEpochSecond();
            long lastAlertTs = readLong(LAST_ALERT_TS_KEY, 0L);
            if (now - lastAlertTs < cooldownSeconds) {
                log.warn("Collaboration RED alert suppressed by cooldown. remaining={}s", cooldownSeconds - (now - lastAlertTs));
                return;
            }

            String title = "多智能体协作健康告警";
            String content = buildAlertContent(metrics, streak);

            // 1) 消息中心广播
            systemNotificationService.sendWarning(title, content, null);

            // 2) 统一告警中心（支持规则渠道）
            alertService.triggerSystemAlert("COLLAB_HEALTH", "COLLAB_ORCHESTRATOR", content);

            redisTemplate.opsForValue().set(LAST_ALERT_TS_KEY, String.valueOf(now), Duration.ofDays(2));
            log.error("Collaboration RED alert sent. streak={}, metrics={}", streak, summarize(metrics));

        } catch (Exception e) {
            log.error("Failed to inspect collaboration metrics alerts", e);
        }
    }

    private long incrementRedStreak() {
        Long value = redisTemplate.opsForValue().increment(RED_STREAK_KEY);
        long streak = value != null ? value : 1L;
        redisTemplate.expire(RED_STREAK_KEY, Duration.ofDays(2));
        return streak;
    }

    private void resetRedStreak() {
        redisTemplate.delete(RED_STREAK_KEY);
    }

    private long readLong(String key, long fallback) {
        try {
            String raw = redisTemplate.opsForValue().get(key);
            return raw == null ? fallback : Long.parseLong(raw);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String buildAlertContent(CollabSessionDtos.SessionMetricsView metrics, long streak) {
        List<String> alerts = metrics.getAlerts() != null ? metrics.getAlerts() : List.of();
        String reasons = alerts.isEmpty() ? "无详细告警项" : String.join("；", alerts);
        return String.format(
                "窗口=%dh，连续RED=%d（阈值=%d，采样间隔=%dms）；成功率=%s，P95=%.0fms，DLQ=%d，竞标后成功率=%s，平均Critique=%.2f。触发原因：%s",
                metrics.getHours() != null ? metrics.getHours() : windowHours,
                streak,
                redConsecutiveThreshold,
                fixedDelayMs,
                percent(metrics.getSuccessRate()),
                metrics.getP95LatencyMs() != null ? metrics.getP95LatencyMs() : 0.0,
                metrics.getDlqBacklog() != null ? metrics.getDlqBacklog() : 0L,
                percent(metrics.getBiddingPostSuccessRate()),
                metrics.getAvgCritiqueRounds() != null ? metrics.getAvgCritiqueRounds() : 0.0,
                reasons
        );
    }

    private String summarize(CollabSessionDtos.SessionMetricsView metrics) {
        return String.format(
                "level=%s success=%s p95=%.0f dlq=%d bidSuccess=%s critique=%.2f",
                metrics.getOverallLevel(),
                percent(metrics.getSuccessRate()),
                metrics.getP95LatencyMs() != null ? metrics.getP95LatencyMs() : 0.0,
                metrics.getDlqBacklog() != null ? metrics.getDlqBacklog() : 0L,
                percent(metrics.getBiddingPostSuccessRate()),
                metrics.getAvgCritiqueRounds() != null ? metrics.getAvgCritiqueRounds() : 0.0
        );
    }

    private String percent(Double value) {
        double v = value != null ? value : 0.0;
        return String.format("%.1f%%", v * 100.0);
    }
}
