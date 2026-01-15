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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.adlin.orin.modules.audit.entity.AuditLog;

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

        @Override
        public Map<String, Long> getTokenStats() {
                Map<String, Long> stats = new HashMap<>();
                LocalDateTime now = LocalDateTime.now();

                // 今日
                LocalDateTime startOfDay = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
                Long daily = auditLogRepository.sumTotalTokensAfter(startOfDay);
                stats.put("daily", daily != null ? daily : 0L);

                // 本周
                LocalDateTime startOfWeek = startOfDay.minusDays(now.getDayOfWeek().getValue() - 1);
                Long weekly = auditLogRepository.sumTotalTokensAfter(startOfWeek);
                stats.put("weekly", weekly != null ? weekly : 0L);

                // 本月
                LocalDateTime startOfMonth = startOfDay.withDayOfMonth(1);
                Long monthly = auditLogRepository.sumTotalTokensAfter(startOfMonth);
                stats.put("monthly", monthly != null ? monthly : 0L);

                // 总计
                Long total = auditLogRepository.sumTotalTokensAll();
                stats.put("total", total != null ? total : 0L);

                return stats;
        }

        @Override
        public List<Map<String, Object>> getTokenTrend(String period) {
                LocalDateTime end = LocalDateTime.now();
                LocalDateTime start;
                String formatPattern;
                ChronoUnit groupingUnit;

                if ("weekly".equals(period)) {
                        start = end.minusWeeks(12); // Past 12 weeks
                        formatPattern = "yyyy-MM-dd"; // 使用周一开始的日期
                        groupingUnit = ChronoUnit.WEEKS;
                } else if ("monthly".equals(period)) {
                        start = end.minusMonths(12); // Past 12 months
                        formatPattern = "yyyy-MM";
                        groupingUnit = ChronoUnit.MONTHS;
                } else {
                        // Default to daily
                        start = end.minusDays(30); // Past 30 days
                        formatPattern = "yyyy-MM-dd";
                        groupingUnit = ChronoUnit.DAYS;
                }

                // 获取时间范围内的所有日志
                List<AuditLog> logs = auditLogRepository.findByCreatedAtBetween(start, end);

                // 在内存中分组统计
                Map<String, Long> groupedData = new TreeMap<>(); // 使用 TreeMap 保持顺序
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatPattern);

                // 初始化所有时间点为 0，防止只有数据的日期才显示
                LocalDateTime current = start;
                while (!current.isAfter(end)) {
                        String key = current.format(formatter);
                        groupedData.put(key, 0L);

                        if (groupingUnit == ChronoUnit.DAYS)
                                current = current.plusDays(1);
                        else if (groupingUnit == ChronoUnit.WEEKS)
                                current = current.plusWeeks(1);
                        else if (groupingUnit == ChronoUnit.MONTHS)
                                current = current.plusMonths(1);
                }

                // 填充实际数据
                for (AuditLog log : logs) {
                        if (log.getTotalTokens() != null) {
                                String key = log.getCreatedAt().format(formatter);
                                // 对于 weekly，我们需要找到该周的 key。简单起见，如果 format 是 yyyy-MM-dd，我们不做复杂周对齐，
                                // 而是依靠 TreeMap 的 containsKey (如果初始化正确)。
                                // 这里的简单实现：仅仅依靠 formatter。对于 daily 和 monthly 没问题。
                                // 对于 weekly，formatter pattern 需要能区分周。

                                // 修正：更简单的聚合逻辑
                                if (groupedData.containsKey(key)) {
                                        groupedData.put(key, groupedData.get(key) + log.getTotalTokens());
                                }
                        }
                }

                // 转换为 List
                List<Map<String, Object>> result = new ArrayList<>();
                for (Map.Entry<String, Long> entry : groupedData.entrySet()) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("date", entry.getKey());
                        item.put("tokens", entry.getValue());
                        result.add(item);
                }

                return result;
        }

        @Override
        public Page<AuditLog> getTokenHistory(int page, int size, Long startDate, Long endDate) {
                Pageable pageable = PageRequest.of(page, size);
                LocalDateTime start;
                LocalDateTime end;

                if (startDate != null && endDate != null) {
                        start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startDate), ZoneId.systemDefault());
                        end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endDate), ZoneId.systemDefault());
                } else {
                        // 默认最近 30 天
                        end = LocalDateTime.now();
                        start = end.minusDays(30);
                }

                return auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end, pageable);
        }

        @Override
        public Map<String, Object> getLatencyStats() {
                Map<String, Object> stats = new HashMap<>();
                LocalDateTime now = LocalDateTime.now();

                // Today Avg
                LocalDateTime startOfDay = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
                Double daily = auditLogRepository.avgResponseTimeAfter(startOfDay);
                stats.put("daily", daily != null ? Math.round(daily) : 0L);

                // Week Avg
                LocalDateTime startOfWeek = startOfDay.minusDays(now.getDayOfWeek().getValue() - 1);
                Double weekly = auditLogRepository.avgResponseTimeAfter(startOfWeek);
                stats.put("weekly", weekly != null ? Math.round(weekly) : 0L);

                // Month Avg
                LocalDateTime startOfMonth = startOfDay.withDayOfMonth(1);
                Double monthly = auditLogRepository.avgResponseTimeAfter(startOfMonth);
                stats.put("monthly", monthly != null ? Math.round(monthly) : 0L);

                // Max
                Long max = auditLogRepository.maxResponseTimeAll();
                stats.put("max", max != null ? max : 0L);

                return stats;
        }

        @Override
        public List<Map<String, Object>> getLatencyTrend(String period) {
                LocalDateTime end = LocalDateTime.now();
                LocalDateTime start;
                String formatPattern;
                ChronoUnit groupingUnit;

                if ("weekly".equals(period)) {
                        start = end.minusWeeks(12);
                        formatPattern = "yyyy-MM-dd";
                        groupingUnit = ChronoUnit.WEEKS;
                } else if ("monthly".equals(period)) {
                        start = end.minusMonths(12);
                        formatPattern = "yyyy-MM";
                        groupingUnit = ChronoUnit.MONTHS;
                } else {
                        start = end.minusDays(30);
                        formatPattern = "yyyy-MM-dd";
                        groupingUnit = ChronoUnit.DAYS;
                }

                List<AuditLog> logs = auditLogRepository.findByCreatedAtBetween(start, end);

                Map<String, Long> sumMap = new TreeMap<>();
                Map<String, Integer> countMap = new HashMap<>();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatPattern);

                // Initialize
                LocalDateTime current = start;
                while (!current.isAfter(end)) {
                        String key = current.format(formatter);
                        sumMap.put(key, 0L);
                        countMap.put(key, 0);
                        if (groupingUnit == ChronoUnit.DAYS)
                                current = current.plusDays(1);
                        else if (groupingUnit == ChronoUnit.WEEKS)
                                current = current.plusWeeks(1);
                        else if (groupingUnit == ChronoUnit.MONTHS)
                                current = current.plusMonths(1);
                }

                // Aggregate
                for (AuditLog log : logs) {
                        if (log.getResponseTime() != null) {
                                String key = log.getCreatedAt().format(formatter);
                                if (sumMap.containsKey(key)) {
                                        sumMap.put(key, sumMap.get(key) + log.getResponseTime());
                                        countMap.put(key, countMap.get(key) + 1);
                                }
                        }
                }

                // Result
                List<Map<String, Object>> result = new ArrayList<>();
                for (Map.Entry<String, Long> entry : sumMap.entrySet()) {
                        String key = entry.getKey();
                        Integer count = countMap.get(key);
                        Map<String, Object> item = new HashMap<>();
                        item.put("date", key);
                        item.put("latency", count > 0 ? entry.getValue() / count : 0);
                        result.add(item);
                }

                return result;
        }

        @Override
        public Page<AuditLog> getLatencyHistory(int page, int size, Long startDate, Long endDate) {
                return getTokenHistory(page, size, startDate, endDate);
        }
}
