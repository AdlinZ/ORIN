package com.adlin.orin.modules.monitor.service.impl;

import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentMetric;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import com.adlin.orin.modules.monitor.repository.AgentMetricRepository;
import com.adlin.orin.modules.monitor.service.MonitorService;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorServiceImpl implements MonitorService {

        private final AgentHealthStatusRepository healthStatusRepository;
        private final AgentMetricRepository metricRepository;
        private final AuditLogRepository auditLogRepository;
        private final DifyIntegrationService difyIntegrationService;

        @Override
        public Map<String, Object> getGlobalSummary() {
                Map<String, Object> summary = new HashMap<>();

                List<AgentHealthStatus> allAgents = healthStatusRepository.findAll();

                // 统计各状态的Agent数量
                long runningCount = allAgents.stream()
                                .filter(a -> a.getStatus() == AgentHealthStatus.Status.RUNNING)
                                .count();
                long stoppedCount = allAgents.stream()
                                .filter(a -> a.getStatus() == AgentHealthStatus.Status.STOPPED)
                                .count();
                long highLoadCount = allAgents.stream()
                                .filter(a -> a.getStatus() == AgentHealthStatus.Status.HIGH_LOAD)
                                .count();

                summary.put("total_agents", allAgents.size());
                summary.put("online_agents", runningCount + highLoadCount);
                summary.put("stoppedAgents", stoppedCount);
                summary.put("highLoadAgents", highLoadCount);
                summary.put("system_status", highLoadCount > 0 ? "高负载" : "正常");

                // 计算平均健康分数
                double avgHealthScore = allAgents.stream()
                                .mapToInt(AgentHealthStatus::getHealthScore)
                                .average()
                                .orElse(0.0);
                summary.put("averageHealthScore", Math.round(avgHealthScore * 100.0) / 100.0);

                // 获取最近一条审计日志判断 Dify 连接状态
                LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
                List<com.adlin.orin.modules.audit.entity.AuditLog> todayLogs = auditLogRepository
                                .findByCreatedAtBetween(startOfDay, LocalDateTime.now());

                // Frontend expects: daily_requests, total_tokens, avg_latency
                summary.put("daily_requests", todayLogs.size());

                int totalTokens = todayLogs.stream()
                                .mapToInt(log -> log.getTotalTokens() != null ? log.getTotalTokens() : 0)
                                .sum();
                summary.put("total_tokens", totalTokens);

                // Calculate average latency
                double avgLatency = todayLogs.stream()
                                .mapToLong(log -> log.getResponseTime() != null ? log.getResponseTime() : 0L)
                                .average()
                                .orElse(0.0);
                summary.put("avg_latency", Math.round(avgLatency) + "ms");

                double totalCost = todayLogs.stream()
                                .mapToDouble(log -> log.getEstimatedCost() != null ? log.getEstimatedCost() : 0.0)
                                .sum();
                summary.put("todayCost", Math.round(totalCost * 10000.0) / 10000.0);

                return summary;
        }

        @Override
        public AgentHealthStatus getAgentStatus(String agentId) {
                return healthStatusRepository.findById(agentId)
                                .orElseThrow(() -> new RuntimeException("Agent not found: " + agentId));
        }

        @Override
        public List<AgentMetric> getAgentMetrics(String agentId, Long startTime, Long endTime, String interval) {
                // 1. 获取持久化的指标数据
                List<AgentMetric> metrics = metricRepository
                                .findByAgentIdAndTimestampBetweenOrderByTimestampAsc(agentId, startTime, endTime);

                // 2. 将 AuditLog 转换为实时指标数据点（如果持久化数据较少）
                LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault());
                LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault());

                List<com.adlin.orin.modules.audit.entity.AuditLog> logs = auditLogRepository
                                .findByProviderIdAndCreatedAtBetweenOrderByCreatedAtAsc(agentId, start, end);

                if (!logs.isEmpty()) {
                        List<AgentMetric> logMetrics = logs.stream().map(l -> AgentMetric.builder()
                                        .agentId(agentId)
                                        .timestamp(l.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                                                        .toEpochMilli())
                                        .responseLatency(l.getResponseTime() != null ? l.getResponseTime().intValue()
                                                        : 0)
                                        .tokenCost(l.getTotalTokens())
                                        .cpuUsage(0.0) // 外部模型无法获取瞬时 CPU
                                        .memoryUsage(0.0)
                                        .build()).collect(Collectors.toList());

                        metrics.addAll(logMetrics);
                }

                // 按照时间排序
                metrics.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

                return metrics;
        }

        @Override
        public List<AgentHealthStatus> getAgentList() {
                return healthStatusRepository.findAll();
        }

        @Override
        public void triggerMockDataGeneration() {
                log.info("Triggering mock performance data generation...");
                List<AgentHealthStatus> agents = healthStatusRepository.findAll();
                Random random = new Random();
                long now = System.currentTimeMillis();

                List<AgentMetric> mockData = new ArrayList<>();
                for (AgentHealthStatus agent : agents) {
                        // 为每个代理生成过去 10 分钟内每分钟一个数据点
                        for (int i = 10; i >= 0; i--) {
                                long ts = now - (i * 60 * 1000L);
                                mockData.add(AgentMetric.builder()
                                                .agentId(agent.getAgentId())
                                                .timestamp(ts)
                                                .cpuUsage(10 + random.nextDouble() * 40)
                                                .memoryUsage(128 + random.nextDouble() * 512)
                                                .responseLatency(200 + random.nextInt(1800))
                                                .tokenCost(random.nextInt(1000))
                                                .dailyRequests(10 + random.nextInt(100))
                                                .build());
                        }
                }
                metricRepository.saveAll(mockData);
                log.info("Successfully generated {} mock metric points.", mockData.size());
        }

        @Override
        public boolean testDifyConnection(String endpointUrl, String apiKey) {
                return difyIntegrationService.testConnection(endpointUrl, apiKey);
        }

        @Override
        public Object getDifyApps(String endpointUrl, String apiKey) {
                return difyIntegrationService.getApplications(endpointUrl, apiKey)
                                .orElse(null);
        }
}
