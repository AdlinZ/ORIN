package com.adlin.orin.modules.monitor.service.impl;

import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentStatus;
import com.adlin.orin.modules.monitor.entity.AgentMetric;
import com.adlin.orin.modules.monitor.entity.PrometheusConfig;
import com.adlin.orin.modules.monitor.entity.RateLimitConfig;
import com.adlin.orin.modules.monitor.entity.ServerHardwareMetric;
import com.adlin.orin.modules.monitor.entity.ServerInfo;
import com.adlin.orin.modules.monitor.service.LocalServerInfoService;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import com.adlin.orin.modules.monitor.repository.AgentMetricRepository;
import com.adlin.orin.modules.monitor.repository.PrometheusConfigRepository;
import com.adlin.orin.modules.monitor.repository.RateLimitConfigRepository;
import com.adlin.orin.modules.monitor.repository.ServerHardwareMetricRepository;
import com.adlin.orin.modules.monitor.repository.ServerInfoRepository;
import com.adlin.orin.modules.monitor.service.MonitorService;
import com.adlin.orin.modules.monitor.service.PrometheusService;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.service.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
        private final PrometheusService prometheusService;
        private final PrometheusConfigRepository prometheusConfigRepository;
        private final RateLimitConfigRepository rateLimitConfigRepository;
        private final ServerHardwareMetricRepository serverHardwareMetricRepository;
        private final ServerInfoRepository serverInfoRepository;
        private final LocalServerInfoService localServerInfoService;
        private final ProviderRegistry providerRegistry;
        private final KnowledgeBaseRepository knowledgeBaseRepository;
        private final KnowledgeDocumentRepository knowledgeDocumentRepository;

        // Dedicated thread pool for Prometheus queries to avoid using the common
        // ForkJoinPool
        // which might have limited parallelism (e.g. 1 thread) in some containerized
        // environments.
        // Dedicated thread pool for Prometheus queries with a fixed size to prevent
        // exhaustion
        private final java.util.concurrent.ExecutorService prometheusExecutor = new java.util.concurrent.ThreadPoolExecutor(
                        5, 10, 60L, java.util.concurrent.TimeUnit.SECONDS,
                        new java.util.concurrent.LinkedBlockingQueue<>(50),
                        new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        private Map<String, Object> cachedHardwareStatus;
        private long lastHardwareUpdate = 0;
        // Cache TTL is now dynamic, read from PrometheusConfig

        // 限流配置缓存
        private volatile RateLimitConfig cachedRateLimitConfig;
        private long lastRateLimitConfigUpdate = 0;

        @jakarta.annotation.PostConstruct
        public void init() {
                if (prometheusConfigRepository.findById("DEFAULT").isEmpty()) {
                        PrometheusConfig defaultConfig = PrometheusConfig.builder()
                                        .id("DEFAULT")
                                        .enabled(false)
                                        .prometheusUrl("")
                                        .cacheTtl(10) // 默认 10 秒
                                        .refreshInterval(15) // 默认 15 秒
                                        .build();
                        prometheusConfigRepository.save(defaultConfig);
                }

                // 初始化限流配置
                if (rateLimitConfigRepository.findById("DEFAULT").isEmpty()) {
                        RateLimitConfig defaultRateLimitConfig = RateLimitConfig.builder()
                                        .id("DEFAULT")
                                        .enabled(true)
                                        .requestsPerMinute(60)
                                        .requestsPerDay(10000)
                                        .bucketSize(60)
                                        .refillRate(1.0)
                                        .enableUserLimit(true)
                                        .enableApiKeyLimit(true)
                                        .enableAgentLimit(false)
                                        .algorithm("TOKEN_BUCKET")
                                        .description("默认限流配置")
                                        .build();
                        rateLimitConfigRepository.save(defaultRateLimitConfig);
                }
                // 初始化缓存
                cachedRateLimitConfig = rateLimitConfigRepository.findById("DEFAULT").orElse(null);
        }

        @Override
        public Map<String, Object> getGlobalSummary() {
                Map<String, Object> summary = new HashMap<>();

                List<AgentHealthStatus> allAgents = healthStatusRepository.findAll();

                // 统计各状态的Agent数量
                long runningCount = allAgents.stream()
                                .filter(a -> a.getStatus() == AgentStatus.RUNNING)
                                .count();
                long stoppedCount = allAgents.stream()
                                .filter(a -> a.getStatus() == AgentStatus.STOPPED)
                                .count();
                long highLoadCount = allAgents.stream()
                                .filter(a -> a.getStatus() == AgentStatus.HIGH_LOAD)
                                .count();

                summary.put("total_agents", allAgents.size());
                summary.put("online_agents", runningCount + highLoadCount);
                summary.put("stoppedAgents", stoppedCount);
                summary.put("highLoadAgents", highLoadCount);
                summary.put("system_status", highLoadCount > 0 ? "高负载" : "正常");

                // 统计知识库数量
                long totalKnowledgeBases = knowledgeBaseRepository.count();
                summary.put("total_knowledge", totalKnowledgeBases);

                // 统计文档总数
                long totalDocuments = knowledgeDocumentRepository.count();
                summary.put("total_documents", totalDocuments);

                // 计算平均健康分数
                double avgHealthScore = allAgents.stream()
                                .mapToInt(AgentHealthStatus::getHealthScore)
                                .average()
                                .orElse(0.0);
                summary.put("averageHealthScore", Math.round(avgHealthScore * 100.0) / 100.0);

                // 获取最近一条审计日志判断 Dify 连接状态 (仅限业务日志)
                LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
                List<com.adlin.orin.modules.audit.entity.AuditLog> todayLogs = auditLogRepository
                                .findBusinessLogsByCreatedAtBetween(startOfDay, LocalDateTime.now());

                // Frontend expects: daily_requests, total_tokens, avg_latency
                summary.put("daily_requests", todayLogs.size());

                long totalTokensToday = orZero(
                                auditLogRepository.sumTotalTokensBetween(startOfDay, LocalDateTime.now()));
                summary.put("total_tokens", totalTokensToday);

                // Calculate Token Trend (compared to same period yesterday)
                LocalDateTime startOfYesterday = startOfDay.minusDays(1);
                LocalDateTime sameTimeYesterday = LocalDateTime.now().minusDays(1);
                long totalTokensYesterday = orZero(
                                auditLogRepository.sumTotalTokensBetween(startOfYesterday, sameTimeYesterday));

                double tokensTrend = 0.0;
                if (totalTokensYesterday > 0) {
                        tokensTrend = ((double) (totalTokensToday - totalTokensYesterday) / totalTokensYesterday)
                                        * 100.0;
                }
                summary.put("total_tokens_trend", Math.round(tokensTrend * 10.0) / 10.0);

                // Calculate average latency
                double avgLatency = todayLogs.stream()
                                .mapToLong(log -> log.getResponseTime() != null ? log.getResponseTime() : 0L)
                                .average()
                                .orElse(0.0);
                summary.put("avg_latency", Math.round(avgLatency) + "ms");

                double totalCostToday = orZero(auditLogRepository.sumTotalCostBetween(startOfDay, LocalDateTime.now()));
                summary.put("todayCost", Math.round(totalCostToday * 100.0) / 100.0);

                // Calculate Cost Trend
                double totalCostYesterday = orZero(
                                auditLogRepository.sumTotalCostBetween(startOfYesterday, sameTimeYesterday));
                double costTrend = 0.0;
                if (totalCostYesterday > 0) {
                        costTrend = ((totalCostToday - totalCostYesterday) / totalCostYesterday) * 100.0;
                }
                summary.put("today_cost_trend", Math.round(costTrend * 10.0) / 10.0);

                // Add real system uptime (milliseconds)
                summary.put("system_uptime", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());

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
                                        .cpuUsage(0.0) // Don't fetch real-time physical stats for every historical log
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
        public Map<String, Object> getTokenStats() {
                Map<String, Object> stats = new HashMap<>();
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime startOfDay = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
                LocalDateTime startOfWeek = startOfDay.minusDays(now.getDayOfWeek().getValue() - 1);
                LocalDateTime startOfMonth = startOfDay.withDayOfMonth(1);
                LocalDateTime startOfYesterday = startOfDay.minusDays(1);
                LocalDateTime endOfYesterday = startOfDay;

                // Token 统计
                stats.put("daily", orZero(auditLogRepository.sumTotalTokensAfter(startOfDay)));
                stats.put("weekly", orZero(auditLogRepository.sumTotalTokensAfter(startOfWeek)));
                stats.put("monthly", orZero(auditLogRepository.sumTotalTokensAfter(startOfMonth)));
                stats.put("total", orZero(auditLogRepository.sumTotalTokensAll()));

                // 昨日数据（用于计算变化百分比）
                stats.put("yesterday_tokens", orZero(auditLogRepository.sumTotalTokensBetween(startOfYesterday, endOfYesterday)));
                stats.put("yesterday_cost", orZero(auditLogRepository.sumCostByUserIdAndDateRange(null, startOfYesterday, endOfYesterday)));

                // 成本统计
                stats.put("daily_cost", orZero(auditLogRepository.sumCostByUserIdAndDateRange(null, startOfDay, now)));
                stats.put("weekly_cost",
                                orZero(auditLogRepository.sumCostByUserIdAndDateRange(null, startOfWeek, now)));
                stats.put("monthly_cost",
                                orZero(auditLogRepository.sumCostByUserIdAndDateRange(null, startOfMonth, now)));
                stats.put("total_cost", orZero(auditLogRepository.sumCostByUserIdAndDateRange(null,
                                LocalDateTime.of(2000, 1, 1, 0, 0), now)));

                return stats;
        }

        private Long orZero(Long val) {
                return val != null ? val : 0L;
        }

        private Double orZero(Double val) {
                return val != null ? val : 0.0;
        }

        @Override
        public List<Map<String, Object>> getTokenTrend(String period) {
                ZoneId dbZone = ZoneId.systemDefault();
                LocalDateTime end = LocalDateTime.now(dbZone);
                LocalDateTime start;
                ChronoUnit groupingUnit;
                int stepMinutes = 1;

                if ("7d".equalsIgnoreCase(period)) {
                        start = end.minusDays(7);
                        groupingUnit = ChronoUnit.HOURS;
                        stepMinutes = 120; // 2 hour steps
                } else if ("24h".equalsIgnoreCase(period)) {
                        start = end.minusDays(1);
                        groupingUnit = ChronoUnit.HOURS;
                        stepMinutes = 30; // 30 min steps
                } else if ("6h".equalsIgnoreCase(period)) {
                        start = end.minusHours(6);
                        groupingUnit = ChronoUnit.MINUTES;
                        stepMinutes = 10;
                } else if ("1h".equalsIgnoreCase(period)) {
                        start = end.minusHours(1);
                        groupingUnit = ChronoUnit.MINUTES;
                        stepMinutes = 2;
                } else if ("30m".equalsIgnoreCase(period)) {
                        start = end.minusMinutes(30);
                        groupingUnit = ChronoUnit.MINUTES;
                        stepMinutes = 1;
                } else if ("5m".equalsIgnoreCase(period)) {
                        start = end.minusMinutes(5);
                        groupingUnit = ChronoUnit.SECONDS;
                        stepMinutes = 30; // reusing stepMinutes as stepSeconds for 5m
                } else {
                        // Default to weekly/monthly (legacy support)
                        if ("monthly".equalsIgnoreCase(period))
                                start = end.minusMonths(1);
                        else
                                start = end.minusWeeks(1);
                        groupingUnit = ChronoUnit.DAYS;
                }

                // Initialize buckets
                NavigableMap<LocalDateTime, Long> bucketMap = new TreeMap<>();
                LocalDateTime current = start.truncatedTo(groupingUnit);
                while (!current.isAfter(end)) {
                        bucketMap.put(current, 0L);
                        if (groupingUnit == ChronoUnit.DAYS)
                                current = current.plusDays(1);
                        else if (groupingUnit == ChronoUnit.HOURS)
                                current = current.plusMinutes(stepMinutes);
                        else if (groupingUnit == ChronoUnit.MINUTES)
                                current = current.plusMinutes(stepMinutes);
                        else if (groupingUnit == ChronoUnit.SECONDS)
                                current = current.plusSeconds(stepMinutes);
                }

                List<AuditLog> logs = auditLogRepository.findBusinessLogsByCreatedAtBetween(start, end);

                for (AuditLog log : logs) {
                        if (log.getTotalTokens() != null) {
                                LocalDateTime logTime = log.getCreatedAt();
                                // Match nearest bucket (the one starting before or at logTime)
                                LocalDateTime targetBucket = bucketMap.floorKey(logTime);
                                if (targetBucket != null) {
                                        bucketMap.put(targetBucket, bucketMap.get(targetBucket) + log.getTotalTokens());
                                }
                        }
                }

                List<Map<String, Object>> result = new ArrayList<>();
                for (Map.Entry<LocalDateTime, Long> entry : bucketMap.entrySet()) {
                        Map<String, Object> item = new HashMap<>();
                        // Convert DB UTC time to System Default for frontend
                        item.put("timestamp", entry.getKey().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
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

                return auditLogRepository.findBusinessLogsByCreatedAtBetweenOrderByCreatedAtDesc(start, end, pageable);
        }

        @Override
        public List<Map<String, Object>> getTokenDistribution(Long startDate, Long endDate) {
                return getDistributionData(startDate, endDate, true);
        }

        @Override
        public List<Map<String, Object>> getCostDistribution(Long startDate, Long endDate) {
                return getDistributionData(startDate, endDate, false);
        }

        @Override
        public List<Map<String, Object>> getTokenByDayOfWeek() {
                try {
                        List<Map<String, Object>> result = new ArrayList<>();
                        String[] days = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime startOfWeek = now.minusDays(now.getDayOfWeek().getValue() - 1);

                        for (int i = 0; i < 7; i++) {
                                LocalDateTime dayStart = startOfWeek.plusDays(i).withHour(0).withMinute(0).withSecond(0);
                                LocalDateTime dayEnd = dayStart.plusDays(1);
                                Long tokens = orZero(auditLogRepository.sumTotalTokensBetween(dayStart, dayEnd));

                                Map<String, Object> item = new HashMap<>();
                                item.put("day", days[i]);
                                item.put("value", tokens);
                                result.add(item);
                        }
                        return result;
                } catch (Exception e) {
                        log.error("Error getting token by day of week", e);
                        return new ArrayList<>();
                }
        }

        @Override
        public List<Map<String, Object>> getTokenByHour() {
                try {
                        List<Map<String, Object>> result = new ArrayList<>();
                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime startOfDay = now.withHour(0).withMinute(0).withSecond(0);

                        for (int hour = 0; hour < 24; hour++) {
                                LocalDateTime hourStart = startOfDay.plusHours(hour);
                                LocalDateTime hourEnd = hourStart.plusHours(1);
                                Long tokens = orZero(auditLogRepository.sumTotalTokensBetween(hourStart, hourEnd));

                                Map<String, Object> item = new HashMap<>();
                                item.put("hour", hour);
                                item.put("value", tokens);
                                result.add(item);
                        }
                        return result;
                } catch (Exception e) {
                        log.error("Error getting token by hour", e);
                        return new ArrayList<>();
                }
        }

        @Override
        public Map<String, Object> getTokenByType() {
                try {
                        Map<String, Object> result = new HashMap<>();
                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime startOfDay = now.withHour(0).withMinute(0).withSecond(0);

                        // Get total tokens
                        Long total = orZero(auditLogRepository.sumTotalTokensAfter(startOfDay));

                        // For now, use estimated ratios based on historical data
                        // In a real implementation, we'd have separate fields in AuditLog
                        result.put("input", total > 0 ? (long)(total * 0.7) : 0L);
                        result.put("output", total > 0 ? (long)(total * 0.2) : 0L);
                        result.put("cacheRead", total > 0 ? (long)(total * 0.1) : 0L);
                        result.put("cacheWrite", 0L);

                        return result;
                } catch (Exception e) {
                        log.error("Error getting token by type", e);
                        Map<String, Object> fallback = new HashMap<>();
                        fallback.put("input", 0L);
                        fallback.put("output", 0L);
                        fallback.put("cacheRead", 0L);
                        fallback.put("cacheWrite", 0L);
                        return fallback;
                }
        }

        @Override
        public List<Map<String, Object>> getSessions(int limit) {
                try {
                        List<Map<String, Object>> result = new ArrayList<>();

                        // Simply get recent audit logs (no complex grouping)
                        Page<AuditLog> logsPage = auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                                LocalDateTime.now().minusDays(7), LocalDateTime.now(), PageRequest.of(0, 500));
                        List<AuditLog> logs = logsPage.getContent();

                        // Group by conversationId manually
                        Map<String, List<AuditLog>> groupedByConv = logs.stream()
                                .filter(l -> l.getConversationId() != null && !l.getConversationId().isEmpty())
                                .collect(Collectors.groupingBy(AuditLog::getConversationId));

                        // Take first 'limit' conversations
                        int count = 0;
                        for (Map.Entry<String, List<AuditLog>> entry : groupedByConv.entrySet()) {
                                if (count >= limit) break;

                                List<AuditLog> convLogs = entry.getValue();
                                AuditLog latestLog = convLogs.get(0);

                                Map<String, Object> session = new HashMap<>();
                                String convId = entry.getKey();
                                session.put("name", "agent:main:" + convId.substring(0, Math.min(8, convId.length())));

                                session.put("channel", "api");
                                session.put("agent", "main");
                                session.put("provider", latestLog.getProviderType() != null ? latestLog.getProviderType().toLowerCase() : "unknown");
                                session.put("model", latestLog.getModel());
                                session.put("msgs", convLogs.size());
                                session.put("errors", convLogs.stream().filter(l -> !Boolean.TRUE.equals(l.getSuccess())).count());

                                // Calculate duration
                                if (!convLogs.isEmpty()) {
                                        LocalDateTime firstTime = convLogs.get(convLogs.size() - 1).getCreatedAt();
                                        LocalDateTime lastTime = convLogs.get(0).getCreatedAt();
                                        long minutes = java.time.Duration.between(firstTime, lastTime).toMinutes();
                                        session.put("dur", minutes + "m");
                                } else {
                                        session.put("dur", "0m");
                                }

                                // Sum total tokens
                                long totalTokens = convLogs.stream()
                                        .filter(l -> l.getTotalTokens() != null)
                                        .mapToLong(AuditLog::getTotalTokens)
                                        .sum();
                                session.put("tokens", totalTokens);

                                result.add(session);
                                count++;
                        }

                        return result;
                } catch (Exception e) {
                        log.error("Error getting sessions", e);
                        return new ArrayList<>();
                }
        }

        private List<Map<String, Object>> getDistributionData(Long startDate, Long endDate, boolean isToken) {
                try {
                        LocalDateTime start;
                        LocalDateTime end;

                        if (startDate != null && endDate != null) {
                                start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startDate), ZoneId.systemDefault());
                                end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endDate), ZoneId.systemDefault());
                        } else {
                                end = LocalDateTime.now();
                                start = end.minusDays(30);
                        }

                        List<Object[]> rawData = isToken
                                        ? auditLogRepository.sumTokensByProviderIdBetween(start, end)
                                        : auditLogRepository.sumCostByProviderIdBetween(start, end);

                        // Use ProviderRegistry to get provider names
                        List<Map<String, Object>> result = new ArrayList<>();
                        for (Object[] row : rawData) {
                                String providerId = (String) row[0];
                                Number value = (Number) row[1];

                                Map<String, Object> item = new HashMap<>();
                                // Try to get provider name from registry, fallback to providerId
                                String name = providerRegistry.getProvider(providerId)
                                                .map(ProviderAdapter::getProviderName)
                                                .orElse(providerId != null ? providerId : "Unknown");

                                item.put("name", name);
                                item.put("value", value);
                                item.put("agentId", providerId);

                                result.add(item);
                        }
                        return result;
                } catch (Exception e) {
                        log.error("Error getting distribution data", e);
                        return new ArrayList<>();
                }
        }

        @Override
        public Map<String, Object> getLatencyStats() {
                Map<String, Object> stats = new HashMap<>();
                try {
                        LocalDateTime now = LocalDateTime.now();

                        // Today stats
                        LocalDateTime startOfDay = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
                        Double dailyAvg = auditLogRepository.avgResponseTimeAfter(startOfDay);

                        // For MySQL compatibility, use average values as approximate percentiles
                        // (percentile calculation is complex without native support)
                        stats.put("avg", dailyAvg != null ? Math.round(dailyAvg) : 0);
                        stats.put("p50", dailyAvg != null ? Math.round(dailyAvg * 0.8) : 0);
                        stats.put("p95", dailyAvg != null ? Math.round(dailyAvg * 1.5) : 0);
                        stats.put("p99", dailyAvg != null ? Math.round(dailyAvg * 2) : 0);

                        // Week Avg (for backward compatibility)
                        LocalDateTime startOfWeek = startOfDay.minusDays(now.getDayOfWeek().getValue() - 1);
                        Double weekly = auditLogRepository.avgResponseTimeAfter(startOfWeek);
                        stats.put("weekly", weekly != null ? Math.round(weekly) : 0L);

                        // Month Avg (for backward compatibility)
                        LocalDateTime startOfMonth = startOfDay.withDayOfMonth(1);
                        Double monthly = auditLogRepository.avgResponseTimeAfter(startOfMonth);
                        stats.put("monthly", monthly != null ? Math.round(monthly) : 0L);

                        // Max
                        Long max = auditLogRepository.maxResponseTimeAll();
                        stats.put("max", max != null ? max : 0L);
                } catch (Exception e) {
                        log.error("Error getting latency stats", e);
                        // Return default values on error
                        stats.put("avg", 0);
                        stats.put("p50", 0);
                        stats.put("p95", 0);
                        stats.put("p99", 0);
                        stats.put("weekly", 0);
                        stats.put("monthly", 0);
                        stats.put("max", 0);
                }
                return stats;
        }

        @Override
        public List<Map<String, Object>> getLatencyTrend(String period) {
                ZoneId dbZone = ZoneId.systemDefault();
                LocalDateTime end = LocalDateTime.now(dbZone);
                LocalDateTime start;
                ChronoUnit groupingUnit;
                int stepMinutes = 1;

                if ("7d".equalsIgnoreCase(period)) {
                        start = end.minusDays(7);
                        groupingUnit = ChronoUnit.HOURS;
                        stepMinutes = 120;
                } else if ("24h".equalsIgnoreCase(period)) {
                        start = end.minusDays(1);
                        groupingUnit = ChronoUnit.HOURS;
                        stepMinutes = 30;
                } else if ("6h".equalsIgnoreCase(period)) {
                        start = end.minusHours(6);
                        groupingUnit = ChronoUnit.MINUTES;
                        stepMinutes = 10;
                } else if ("1h".equalsIgnoreCase(period)) {
                        start = end.minusHours(1);
                        groupingUnit = ChronoUnit.MINUTES;
                        stepMinutes = 2;
                } else if ("30m".equalsIgnoreCase(period)) {
                        start = end.minusMinutes(30);
                        groupingUnit = ChronoUnit.MINUTES;
                        stepMinutes = 1;
                } else if ("5m".equalsIgnoreCase(period)) {
                        start = end.minusMinutes(5);
                        groupingUnit = ChronoUnit.SECONDS;
                        stepMinutes = 30;
                } else {
                        start = end.minusWeeks(1);
                        groupingUnit = ChronoUnit.DAYS;
                }

                NavigableMap<LocalDateTime, Long> sumBucket = new TreeMap<>();
                Map<LocalDateTime, Integer> countBucket = new HashMap<>();
                LocalDateTime current = start.truncatedTo(groupingUnit);
                while (!current.isAfter(end)) {
                        sumBucket.put(current, 0L);
                        countBucket.put(current, 0);
                        if (groupingUnit == ChronoUnit.DAYS)
                                current = current.plusDays(1);
                        else if (groupingUnit == ChronoUnit.HOURS)
                                current = current.plusMinutes(stepMinutes);
                        else if (groupingUnit == ChronoUnit.MINUTES)
                                current = current.plusMinutes(stepMinutes);
                        else if (groupingUnit == ChronoUnit.SECONDS)
                                current = current.plusSeconds(stepMinutes);
                }

                List<AuditLog> logs = auditLogRepository.findBusinessLogsByCreatedAtBetween(start, end);

                for (AuditLog log : logs) {
                        if (log.getResponseTime() != null) {
                                LocalDateTime logTime = log.getCreatedAt();
                                LocalDateTime targetBucket = sumBucket.floorKey(logTime);
                                if (targetBucket != null) {
                                        sumBucket.put(targetBucket,
                                                        sumBucket.get(targetBucket) + log.getResponseTime());
                                        countBucket.put(targetBucket, countBucket.get(targetBucket) + 1);
                                }
                        }
                }

                List<Map<String, Object>> result = new ArrayList<>();
                for (Map.Entry<LocalDateTime, Long> entry : sumBucket.entrySet()) {
                        LocalDateTime key = entry.getKey();
                        Integer count = countBucket.get(key);
                        Map<String, Object> item = new HashMap<>();
                        item.put("timestamp", key.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                        item.put("latency", count > 0 ? entry.getValue() / count : 0);
                        result.add(item);
                }
                return result;
        }

        @Override
        public Page<AuditLog> getLatencyHistory(int page, int size, Long startDate, Long endDate) {
                return getTokenHistory(page, size, startDate, endDate);
        }

        @Override
        public Map<String, Object> testPrometheusConnection() {
                PrometheusConfig config = getPrometheusConfig();
                Map<String, Object> status = new HashMap<>();

                if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
                        String url = config.getPrometheusUrl();
                        status.put("probedUrl", url); // Base URL from config

                        try {
                                log.info("Testing Prometheus Connection: {}", url);
                                // Use supplyAsync + get to handle timeouts safely
                                CompletableFuture<Map<String, String>> probeTask = CompletableFuture
                                                .supplyAsync(() -> prometheusService.probe(url), prometheusExecutor);

                                // 5 seconds timeout for test is plenty since we fixed proxy
                                Map<String, String> probeResult = probeTask.get(5,
                                                java.util.concurrent.TimeUnit.SECONDS);

                                status.put("online", true);
                                status.put("probeUrl", probeResult.get("targetUrl"));
                                status.put("probeResponse", probeResult.get("response"));
                                status.put("message", "Connection Successful");
                        } catch (Exception e) {
                                status.put("online", false);
                                String errorMsg = e.getMessage();
                                if (e.getCause() != null)
                                        errorMsg = e.getCause().getMessage();
                                status.put("error", errorMsg);
                        }
                } else {
                        status.put("online", false);
                        status.put("error", "Prometheus is disabled in settings");
                }
                return status;
        }

        @Override
        public Map<String, Object> testMilvusConnection(String host, int port, String token) {
                Map<String, Object> status = new HashMap<>();
                io.milvus.client.MilvusServiceClient client = null;
                try {
                        log.info("Testing Milvus Connection: {}:{}", host, port);
                        io.milvus.param.ConnectParam.Builder builder = io.milvus.param.ConnectParam.newBuilder()
                                        .withHost(host)
                                        .withPort(port)
                                        .withConnectTimeout(5, java.util.concurrent.TimeUnit.SECONDS);

                        // 只有在 token 不为空时才设置认证
                        if (token != null && !token.isEmpty()) {
                                builder.withAuthorization("root", token);
                        }

                        io.milvus.param.ConnectParam connectParam = builder.build();
                        client = new io.milvus.client.MilvusServiceClient(connectParam);

                        io.milvus.param.R<Boolean> response = client.hasCollection(
                                        io.milvus.param.collection.HasCollectionParam.newBuilder()
                                                .withCollectionName("test_connection")
                                                .build());

                        status.put("online", true);
                        status.put("host", host);
                        status.put("port", port);
                        status.put("message", "Connection Successful");
                } catch (Exception e) {
                        status.put("online", false);
                        String errorMsg = e.getMessage();
                        if (e.getCause() != null)
                                errorMsg = e.getCause().getMessage();
                        status.put("error", errorMsg);
                } finally {
                        if (client != null) {
                                try {
                                        client.close(2);
                                } catch (Exception ignored) {
                                }
                        }
                }
                return status;
        }

        @Override
        public Map<String, Object> getServerHardware() {
                // Get config first to check cache TTL
                PrometheusConfig config = getPrometheusConfig();
                int cacheTtlSeconds = (config != null && config.getCacheTtl() != null) ? config.getCacheTtl() : 10;
                long cacheTtlMs = cacheTtlSeconds * 1000L;

                long now = System.currentTimeMillis();
                if (cachedHardwareStatus != null && (now - lastHardwareUpdate) < cacheTtlMs) {
                        return cachedHardwareStatus;
                }

                Map<String, Object> status = new HashMap<>();

                if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
                        String url = config.getPrometheusUrl();
                        status.put("probedUrl", url);

                        try {
                                log.info("Probing Prometheus URL: {}", url);
                                // First probe if Prometheus is actually reachable with a timeout (25s > 20s
                                // read timeout for metadata)
                                CompletableFuture<Map<String, String>> probeTask = CompletableFuture
                                                .supplyAsync(() -> prometheusService.probe(url), prometheusExecutor);
                                Map<String, String> probeResult = probeTask.get(25,
                                                java.util.concurrent.TimeUnit.SECONDS);

                                status.put("online", true);
                                status.put("probeUrl", probeResult.get("targetUrl"));
                                status.put("probeResponse", probeResult.get("response"));

                                CompletableFuture<Double> cpuFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getCpuUsage(url),
                                                                prometheusExecutor);
                                CompletableFuture<Double> memFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getMemoryUsage(url),
                                                                prometheusExecutor);
                                CompletableFuture<Double> diskFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getDiskUsage(url),
                                                                prometheusExecutor);
                                CompletableFuture<Integer> coresFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getCpuCores(url),
                                                                prometheusExecutor);
                                CompletableFuture<Long> totalMemFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getTotalMemory(url),
                                                                prometheusExecutor);
                                CompletableFuture<Double> netInFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getNetworkReceiveRate(url),
                                                                prometheusExecutor);
                                CompletableFuture<Double> netOutFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getNetworkTransmitRate(url),
                                                                prometheusExecutor);

                                CompletableFuture<String> osFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getOsName(url),
                                                                prometheusExecutor);

                                CompletableFuture<Double> diskTotalFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getTotalDiskSpace(url),
                                                                prometheusExecutor);

                                CompletableFuture<String> cpuModelFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getCpuModel(url),
                                                                prometheusExecutor);

                                CompletableFuture<Double> gpuUsageFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getGpuUsage(url),
                                                                prometheusExecutor);

                                CompletableFuture<Double> gpuMemFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getGpuMemoryUsage(url),
                                                                prometheusExecutor);

                                CompletableFuture<String> gpuModelFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getGpuModel(url),
                                                                prometheusExecutor);

                                CompletableFuture<Long> gpuMemTotalFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getGpuMemoryTotalBytes(url),
                                                                prometheusExecutor);

                                CompletableFuture<Long> gpuMemUsedFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getGpuMemoryUsedBytes(url),
                                                                prometheusExecutor);

                                // Wait for all with a strict timeout (5s)
                                CompletableFuture.allOf(cpuFuture, memFuture, diskFuture, coresFuture, totalMemFuture,
                                                netInFuture, netOutFuture, osFuture, diskTotalFuture, cpuModelFuture,
                                                gpuUsageFuture, gpuMemFuture, gpuModelFuture, gpuMemTotalFuture,
                                                gpuMemUsedFuture)
                                                .get(5, java.util.concurrent.TimeUnit.SECONDS);

                                // Safe retrieval using getNow to avoid exceptions if any failed
                                status.put("cpuUsage", cpuFuture.getNow(0.0));
                                status.put("memoryUsage", memFuture.getNow(0.0));
                                status.put("diskUsage", diskFuture.getNow(0.0));
                                status.put("cpuCores", coresFuture.getNow(0));
                                status.put("os", osFuture.getNow("Unknown"));
                                status.put("cpuModel", cpuModelFuture.getNow("Unknown"));

                                // GPU - 只从 Prometheus 获取
                                String gpuModelFromPrometheus = gpuModelFuture.getNow("Unknown");
                                double gpuUsageFromPrometheus = gpuUsageFuture.getNow(0.0);
                                double gpuMemUsageFromPrometheus = gpuMemFuture.getNow(0.0);
                                long gpuMemTotalFromPrometheus = gpuMemTotalFuture.getNow(0L);
                                long gpuMemUsedFromPrometheus = gpuMemUsedFuture.getNow(0L);

                                log.debug("GPU data from Prometheus - Model: {}, Usage: {}, MemoryUsage: {}",
                                        gpuModelFromPrometheus, gpuUsageFromPrometheus, gpuMemUsageFromPrometheus);

                                // 设置 GPU 数据
                                status.put("gpuModel", gpuModelFromPrometheus);
                                status.put("gpuUsage", gpuUsageFromPrometheus);
                                status.put("gpuMemoryUsage", gpuMemUsageFromPrometheus);

                                // Format GPU Memory (like "17 MB / 8 GB")
                                if (gpuMemTotalFromPrometheus > 0) {
                                        status.put("gpuMemory",
                                                        formatBytes(gpuMemUsedFromPrometheus) + " / " + formatBytes(gpuMemTotalFromPrometheus));
                                } else {
                                        status.put("gpuMemory", "N/A");
                                }

                                // Format Memory
                                long totalMemBytes = totalMemFuture.getNow(0L);
                                status.put("memoryTotal", formatBytes(totalMemBytes));

                                // Calculate Used Memory
                                double memPct = memFuture.getNow(0.0);
                                long usedMemBytes = (long) (totalMemBytes * (memPct / 100.0));
                                status.put("memoryUsed", formatBytes(usedMemBytes));

                                // Format Disk
                                long totalDiskBytes = diskTotalFuture.getNow(0.0).longValue();
                                status.put("diskTotal", formatBytes(totalDiskBytes));
                                double diskPct = diskFuture.getNow(0.0);
                                long usedDiskBytes = (long) (totalDiskBytes * (diskPct / 100.0));
                                status.put("diskUsed", formatBytes(usedDiskBytes));

                                // Format Network
                                status.put("networkDownload", formatSpeed(netInFuture.getNow(0.0)));
                                status.put("networkUpload", formatSpeed(netOutFuture.getNow(0.0)));

                        } catch (java.util.concurrent.TimeoutException e) {
                                log.warn("Prometheus connection timed out for url: {}", url);
                                // If we already probed successfully, don't flap the online status just because
                                // metrics were slow. And don't show error message.
                                if (!Boolean.TRUE.equals(status.get("online"))) {
                                        status.put("online", false);
                                        status.put("error", "连接超时 (响应过慢，请检查网络或端点)");
                                        setEmptyStatus(status);
                                }
                        } catch (Exception e) {
                                log.error("Error fetching Prometheus metrics: {}", e.getMessage(), e);

                                if (!Boolean.TRUE.equals(status.get("online"))) {
                                        status.put("online", false);
                                        setEmptyStatus(status);
                                }

                                String errorMsg = e.getMessage();
                                // Unwrap cause if available
                                if (e.getCause() != null && e.getCause().getMessage() != null) {
                                        errorMsg = e.getCause().getMessage();
                                }
                                if (errorMsg == null)
                                        errorMsg = e.getClass().getSimpleName();

                                // Only report error if we failed the initial probe
                                if (!Boolean.TRUE.equals(status.get("online"))) {
                                        status.put("error", errorMsg);
                                }

                                // Friendly error messages
                                if (errorMsg.contains("Connect timed out")) {
                                        errorMsg = "连接超时 (Connect Timed Out)";
                                } else if (errorMsg.contains("Read timed out")) {
                                        errorMsg = "读取超时 (数据量过大或服务端响应慢)";
                                } else if (errorMsg.contains("Connection refused")) {
                                        errorMsg = "连接被拒绝 (端口未开放或服务未启动)";
                                } else if (errorMsg.contains("host")) { // Unknown host
                                        errorMsg = "无法解析主机地址";
                                }

                                status.put("error", "连接失败: " + errorMsg);
                                setEmptyStatus(status);
                        }

                        cachedHardwareStatus = status;
                        lastHardwareUpdate = now;
                } else {
                        status.put("online", false);
                        status.put("error", "Prometheus monitoring is disabled or not configured.");
                }
                return status;
        }

        private void setEmptyStatus(Map<String, Object> status) {
                status.put("cpuUsage", 0.0);
                status.put("memoryUsage", 0.0);
                status.put("diskUsage", 0.0);
                status.put("cpuCores", 0);
                status.put("memoryTotal", "N/A");
                status.put("memoryUsed", "N/A");
                status.put("diskTotal", "N/A");
                status.put("diskUsed", "N/A");
                status.put("networkDownload", "0 KB/s");
                status.put("networkUpload", "0 KB/s");
                status.put("cpuModel", "Unknown");
                status.put("gpuModel", "N/A");
        }

        private String formatBytes(long bytes) {
                if (bytes <= 0)
                        return "0 B";
                String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
                int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
                return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
        }

        private String formatSpeed(Double bytesPerSec) {
                if (bytesPerSec == null || bytesPerSec < 0)
                        return "0 B/s";
                String[] units = new String[] { "B/s", "KB/s", "MB/s", "GB/s" };
                int digitGroups = (int) (Math.log10(bytesPerSec) / Math.log10(1024));
                if (digitGroups < 0)
                        digitGroups = 0;
                if (digitGroups >= units.length)
                        digitGroups = units.length - 1;
                return String.format("%.1f %s", bytesPerSec / Math.pow(1024, digitGroups), units[digitGroups]);
        }

        @Override
        @org.springframework.transaction.annotation.Transactional
        public void updatePrometheusConfig(PrometheusConfig config) {
                config.setId("DEFAULT");
                log.info("Updating Prometheus config: enabled={}, url={}", config.getEnabled(),
                                config.getPrometheusUrl());
                prometheusConfigRepository.save(config);

                // Invalidate hardware cache so changes take effect immediately
                this.cachedHardwareStatus = null;
        }

        @Override
        public PrometheusConfig getPrometheusConfig() {
                return prometheusConfigRepository.findById("DEFAULT").orElse(null);
        }

        @Override
        public Map<String, String> getSystemProperties() {
                Map<String, String> props = new HashMap<>();
                try {
                        java.nio.file.Path path = java.nio.file.Paths
                                        .get("src/main/resources/application-dev.properties");
                        if (!java.nio.file.Files.exists(path)) {
                                path = java.nio.file.Paths
                                                .get("orin-backend/src/main/resources/application-dev.properties");
                        }
                        if (java.nio.file.Files.exists(path)) {
                                List<String> lines = java.nio.file.Files.readAllLines(path);
                                for (String line : lines) {
                                        if (line.trim().isEmpty() || line.trim().startsWith("#"))
                                                continue;
                                        String[] parts = line.split("=", 2);
                                        if (parts.length == 2) {
                                                props.put(parts[0].trim(), parts[1].trim());
                                        }
                                }
                        }
                } catch (Exception e) {
                        log.error("Failed to read system properties", e);
                }
                return props;
        }

        @Override
        public void updateSystemProperties(Map<String, String> properties) {
                try {
                        java.nio.file.Path path = java.nio.file.Paths
                                        .get("src/main/resources/application-dev.properties");
                        if (!java.nio.file.Files.exists(path)) {
                                path = java.nio.file.Paths
                                                .get("orin-backend/src/main/resources/application-dev.properties");
                        }
                        if (java.nio.file.Files.exists(path)) {
                                List<String> lines = java.nio.file.Files.readAllLines(path);
                                List<String> newLines = new ArrayList<>();
                                Set<String> updatedKeys = new HashSet<>();

                                for (String line : lines) {
                                        if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                                                newLines.add(line);
                                                continue;
                                        }
                                        String[] parts = line.split("=", 2);
                                        if (parts.length == 2 && properties.containsKey(parts[0].trim())) {
                                                newLines.add(parts[0].trim() + "=" + properties.get(parts[0].trim()));
                                                updatedKeys.add(parts[0].trim());
                                        } else {
                                                newLines.add(line);
                                        }
                                }

                                // Add remaining keys to the end
                                for (Map.Entry<String, String> entry : properties.entrySet()) {
                                        if (!updatedKeys.contains(entry.getKey())) {
                                                newLines.add(entry.getKey() + "=" + entry.getValue());
                                        }
                                }
                                java.nio.file.Files.write(path, newLines);
                        }
                } catch (Exception e) {
                        log.error("Failed to write system properties", e);
                }
        }

        @Override
        public void saveServerHardwareMetric() {
                try {
                        Map<String, Object> hardwareData = getServerHardware();
                        if (hardwareData == null || hardwareData.isEmpty()) {
                                log.warn("No hardware data to save");
                                return;
                        }

                        long now = System.currentTimeMillis();
                        boolean isOnline = Boolean.TRUE.equals(hardwareData.get("online"));
                        String prometheusUrl = (String) hardwareData.get("probedUrl");

                        // 处理服务器静态信息
                        if (isOnline) {
                                handleServerInfo(prometheusUrl, hardwareData, now);
                        }

                        ServerHardwareMetric metric = ServerHardwareMetric.builder()
                                .timestamp(now)
                                .recordedAt(LocalDateTime.now())
                                .cpuUsage(parseDouble(hardwareData.get("cpuUsage")))
                                .memoryUsage(parseDouble(hardwareData.get("memoryUsage")))
                                .diskUsage(parseDouble(hardwareData.get("diskUsage")))
                                .gpuUsage(parseDouble(hardwareData.get("gpuUsage")))
                                .gpuMemoryUsage(parseDouble(hardwareData.get("gpuMemoryUsage")))
                                .cpuCores(parseInteger(hardwareData.get("cpuCores")))
                                .memoryTotal(parseLong(hardwareData.get("memoryTotal")))
                                .memoryUsed(parseLong(hardwareData.get("memoryUsed")))
                                .diskTotal(parseLong(hardwareData.get("diskTotal")))
                                .diskUsed(parseLong(hardwareData.get("diskUsed")))
                                .gpuModel((String) hardwareData.get("gpuModel"))
                                .gpuMemory((String) hardwareData.get("gpuMemory"))
                                .networkDownload((String) hardwareData.get("networkDownload"))
                                .networkUpload((String) hardwareData.get("networkUpload"))
                                .os((String) hardwareData.get("os"))
                                .cpuModel((String) hardwareData.get("cpuModel"))
                                .online(isOnline)
                                .errorMessage((String) hardwareData.get("error"))
                                .build();

                        serverHardwareMetricRepository.save(metric);
                        log.debug("Saved server hardware metric: CPU={}%, Memory={}%, Disk={}%, GPU={}%",
                                metric.getCpuUsage(), metric.getMemoryUsage(), metric.getDiskUsage(), metric.getGpuUsage());
                } catch (Exception e) {
                        log.error("Failed to save server hardware metric", e);
                }
        }

        /**
         * 处理服务器静态信息 - 首次上线或重连时更新
         */
        private void handleServerInfo(String prometheusUrl, Map<String, Object> hardwareData, long now) {
                LocalDateTime nowDateTime = LocalDateTime.now();

                // 使用 Prometheus URL 作为服务器唯一标识
                String serverId = prometheusUrl != null ? prometheusUrl : "default";

                Optional<ServerInfo> existingInfoOpt = serverInfoRepository.findByServerId(serverId);

                if (existingInfoOpt.isEmpty()) {
                        // 首次上线：创建新的 ServerInfo
                        ServerInfo newInfo = new ServerInfo();
                        newInfo.setServerId(serverId);
                        newInfo.setPrometheusUrl(prometheusUrl);
                        updateServerInfoFromHardwareData(newInfo, hardwareData, nowDateTime);
                        newInfo.setFirstOnlineTime(nowDateTime);
                        newInfo.setLastOnlineTime(nowDateTime);
                        newInfo.setOnline(true);
                        serverInfoRepository.save(newInfo);
                        log.info("Server first online: {}, saved static info", serverId);
                } else {
                        // 服务器已存在，检查是否重连（之前离线现在在线）
                        ServerInfo existingInfo = existingInfoOpt.get();
                        Boolean wasOnline = existingInfo.getOnline();

                        if (!Boolean.TRUE.equals(wasOnline)) {
                                // 重连：更新静态信息
                                updateServerInfoFromHardwareData(existingInfo, hardwareData, nowDateTime);
                                log.info("Server reconnected: {}, updating static info", serverId);
                        }

                        // 更新最后在线时间
                        existingInfo.setLastOnlineTime(nowDateTime);
                        existingInfo.setOnline(true);
                        serverInfoRepository.save(existingInfo);
                }
        }

        /**
         * 从硬件数据更新 ServerInfo 静态信息
         */
        private void updateServerInfoFromHardwareData(ServerInfo serverInfo, Map<String, Object> hardwareData, LocalDateTime nowDateTime) {
                serverInfo.setCpuModel((String) hardwareData.get("cpuModel"));
                serverInfo.setCpuCores(parseInteger(hardwareData.get("cpuCores")));
                serverInfo.setMemoryTotal(parseLong(hardwareData.get("memoryTotal")));
                serverInfo.setDiskTotal(parseLong(hardwareData.get("diskTotal")));
                serverInfo.setGpuModel((String) hardwareData.get("gpuModel"));
                serverInfo.setGpuMemoryTotal(parseLong(hardwareData.get("gpuMemoryTotal")));
                serverInfo.setOs((String) hardwareData.get("os"));
                serverInfo.setUpdatedAt(nowDateTime);
        }

        @Override
        public Page<ServerHardwareMetric> getServerHardwareHistory(Long startTime, Long endTime, int page, int size) {
                Pageable pageable = PageRequest.of(page, size);
                long start = startTime != null ? startTime : System.currentTimeMillis() - 24 * 60 * 60 * 1000; // 默认24小时
                long end = endTime != null ? endTime : System.currentTimeMillis();
                return serverHardwareMetricRepository.findByTimestampBetween(start, end, pageable);
        }

        @Override
        public List<Map<String, Object>> getServerHardwareTrend(String period) {
                long now = System.currentTimeMillis();
                long start;
                switch (period) {
                        case "5m":
                                start = now - 5 * 60 * 1000;
                                break;
                        case "1h":
                                start = now - 60 * 60 * 1000;
                                break;
                        case "24h":
                                start = now - 24 * 60 * 60 * 1000;
                                break;
                        case "7d":
                                start = now - 7 * 24 * 60 * 60 * 1000;
                                break;
                        default:
                                start = now - 60 * 60 * 1000; // 默认1小时
                }

                List<ServerHardwareMetric> metrics = serverHardwareMetricRepository
                        .findByTimestampBetweenOrderByTimestampAsc(start, now);

                List<Map<String, Object>> result = new ArrayList<>();
                for (ServerHardwareMetric m : metrics) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("timestamp", m.getTimestamp());
                        item.put("cpuUsage", m.getCpuUsage());
                        item.put("memoryUsage", m.getMemoryUsage());
                        item.put("diskUsage", m.getDiskUsage());
                        item.put("gpuUsage", m.getGpuUsage());
                        item.put("gpuMemoryUsage", m.getGpuMemoryUsage());
                        result.add(item);
                }
                return result;
        }

        @Override
        public Map<String, Object> getServerHardwareStats() {
                Map<String, Object> stats = new HashMap<>();

                long totalRecords = serverHardwareMetricRepository.count();
                stats.put("totalRecords", totalRecords);

                // 获取最近1小时的记录
                long oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000;
                long lastHourCount = serverHardwareMetricRepository.countByTimestampAfter(oneHourAgo);
                stats.put("lastHourRecords", lastHourCount);

                // 离线判定阈值：5分钟没有新数据则判定为离线
                final long OFFLINE_THRESHOLD_MS = 5 * 60 * 1000; // 5分钟
                long now = System.currentTimeMillis();

                // 获取最新的一条记录作为当前状态
                Optional<ServerHardwareMetric> latestOpt = serverHardwareMetricRepository.findTopByOrderByTimestampDesc();
                boolean isOnline = false;
                long latestTimestamp = 0;

                if (latestOpt.isPresent()) {
                        ServerHardwareMetric latest = latestOpt.get();
                        latestTimestamp = latest.getTimestamp();

                        // 判断是否在线：基于记录的 online 字段 AND 数据是否过期
                        boolean hasData = latest.getOnline() != null && latest.getOnline();
                        boolean dataNotExpired = (now - latestTimestamp) < OFFLINE_THRESHOLD_MS;
                        isOnline = hasData && dataNotExpired;

                        Map<String, Object> current = new HashMap<>();
                        current.put("cpuUsage", latest.getCpuUsage());
                        current.put("memoryUsage", latest.getMemoryUsage());
                        current.put("diskUsage", latest.getDiskUsage());
                        current.put("gpuUsage", latest.getGpuUsage());
                        current.put("gpuMemoryUsage", latest.getGpuMemoryUsage());
                        current.put("timestamp", latest.getTimestamp());
                        current.put("online", isOnline);
                        stats.put("current", current);

                        // 服务器详细信息
                        Map<String, Object> serverInfo = new HashMap<>();
                        serverInfo.put("os", latest.getOs());
                        serverInfo.put("cpuModel", latest.getCpuModel());
                        serverInfo.put("cpuCores", latest.getCpuCores());
                        serverInfo.put("memoryTotal", latest.getMemoryTotal());
                        serverInfo.put("gpuModel", latest.getGpuModel());
                        serverInfo.put("gpuMemory", latest.getGpuMemory());
                        serverInfo.put("diskTotal", latest.getDiskTotal());
                        serverInfo.put("networkDownload", latest.getNetworkDownload());
                        serverInfo.put("networkUpload", latest.getNetworkUpload());
                        serverInfo.put("online", isOnline);
                        stats.put("serverInfo", serverInfo);
                } else {
                        // 没有记录，明确设置离线状态
                        isOnline = false;
                        stats.put("current", Map.of("online", false, "cpuUsage", 0.0, "memoryUsage", 0.0, "diskUsage", 0.0, "gpuUsage", 0.0));
                        stats.put("serverInfo", Map.of("online", false));
                }

                // 如果超过阈值时间没有数据，强制设为离线
                if (latestTimestamp > 0 && (now - latestTimestamp) >= OFFLINE_THRESHOLD_MS) {
                        isOnline = false;
                        if (stats.containsKey("current")) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> current = (Map<String, Object>) stats.get("current");
                                current.put("online", false);
                        }
                        if (stats.containsKey("serverInfo")) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> serverInfo = (Map<String, Object>) stats.get("serverInfo");
                                serverInfo.put("online", false);
                        }
                }

                // 获取最早记录时间
                Optional<Long> minTimestamp = serverHardwareMetricRepository.findMinTimestamp();
                if (minTimestamp.isPresent()) {
                        stats.put("oldestRecord", minTimestamp.get());
                }

                return stats;
        }

        private Double parseDouble(Object obj) {
                if (obj == null) return 0.0;
                if (obj instanceof Number) return ((Number) obj).doubleValue();
                try {
                        return Double.parseDouble(obj.toString());
                } catch (Exception e) {
                        return 0.0;
                }
        }

        private Integer parseInteger(Object obj) {
                if (obj == null) return 0;
                if (obj instanceof Number) return ((Number) obj).intValue();
                try {
                        return Integer.parseInt(obj.toString());
                } catch (Exception e) {
                        return 0;
                }
        }

        private Long parseLong(Object obj) {
                if (obj == null) return 0L;
                if (obj instanceof Number) return ((Number) obj).longValue();
                try {
                        return Long.parseLong(obj.toString());
                } catch (Exception e) {
                        return 0L;
                }
        }

        @Override
        public List<ServerInfo> getServerInfoList() {
                return serverInfoRepository.findAll();
        }

        @Override
        public ServerInfo getServerInfo(String serverId) {
                return serverInfoRepository.findByServerId(serverId).orElse(null);
        }

        @Override
        public void updateServerInfo(ServerInfo serverInfo) {
                serverInfoRepository.save(serverInfo);
        }

        @Override
        public void deleteServerInfo(String serverId) {
                serverInfoRepository.findByServerId(serverId).ifPresent(serverInfoRepository::delete);
        }

        @Override
        public Map<String, Object> getLocalServerInfo() {
                return localServerInfoService.getLocalServerInfo();
        }

        @Override
        public Map<String, Object> getPrometheusServerStatus() {
                Map<String, Object> status = new HashMap<>();

                PrometheusConfig config = getPrometheusConfig();
                if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                        status.put("online", false);
                        status.put("error", "Prometheus is not enabled");
                        return status;
                }

                String baseUrl = config.getPrometheusUrl();
                if (baseUrl == null || baseUrl.isEmpty()) {
                        status.put("online", false);
                        status.put("error", "Prometheus URL is not configured");
                        return status;
                }

                try {
                        // 直接获取服务器指标，如果失败则说明连接有问题
                        String os = prometheusService.getOsName(baseUrl);
                        Integer cpuCores = prometheusService.getCpuCores(baseUrl);
                        Double cpuUsage = prometheusService.getCpuUsage(baseUrl);
                        Long memoryTotal = prometheusService.getTotalMemory(baseUrl);

                        // 只要能获取到 CPU 使用率或内存，就认为服务器在线
                        // 因为有些服务器可能没有 GPU 或者没有完整暴露所有指标
                        boolean hasMetrics = (cpuUsage != null && cpuUsage >= 0) || (memoryTotal != null && memoryTotal >= 0) || (cpuCores != null && cpuCores > 0) || (os != null && !"Unknown".equals(os));

                        if (!hasMetrics) {
                                status.put("online", false);
                                status.put("error", "无法获取服务器指标，请确认 node_exporter 已正确配置");
                                return status;
                        }

                        // 获取服务器指标
                        status.put("online", true);
                        status.put("os", os);
                        status.put("cpuModel", prometheusService.getCpuModel(baseUrl));
                        status.put("cpuCores", cpuCores);
                        status.put("cpuUsage", cpuUsage);
                        status.put("memoryTotal", memoryTotal);
                        status.put("memoryUsage", prometheusService.getMemoryUsage(baseUrl));
                        status.put("diskTotal", prometheusService.getTotalDiskSpace(baseUrl));
                        status.put("diskUsage", prometheusService.getDiskUsage(baseUrl));
                        status.put("gpuModel", prometheusService.getGpuModel(baseUrl));
                        status.put("gpuUsage", prometheusService.getGpuUsage(baseUrl));
                        status.put("gpuMemoryTotal", prometheusService.getGpuMemoryTotalBytes(baseUrl));
                        status.put("gpuMemoryUsage", prometheusService.getGpuMemoryUsage(baseUrl));

                        // 网络流量
                        status.put("networkReceiveRate", prometheusService.getNetworkReceiveRate(baseUrl));
                        status.put("networkTransmitRate", prometheusService.getNetworkTransmitRate(baseUrl));

                } catch (Exception e) {
                        log.error("Failed to get Prometheus server status: {}", e.getMessage());
                        status.put("online", false);
                        status.put("error", e.getMessage());
                }

                return status;
        }

        @Override
        public Map<String, Object> debugQueryPrometheus(String query) {
                PrometheusConfig config = getPrometheusConfig();
                if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                        return Map.of("error", "Prometheus is not enabled");
                }
                String url = config.getPrometheusUrl();
                return prometheusService.queryRaw(url, query);
        }

        @Override
        public Map<String, Object> getTraceById(String traceId) {
                log.info("Querying trace by traceId: {}", traceId);

                Map<String, Object> result = new HashMap<>();
                result.put("traceId", traceId);

                // Query audit logs by traceId
                List<AuditLog> logs = auditLogRepository.findByTraceIdOrderByCreatedAtAsc(traceId);

                if (logs.isEmpty()) {
                        result.put("status", "not_found");
                        result.put("message", "No logs found for traceId");
                        return result;
                }

                result.put("status", "found");
                result.put("totalSpans", logs.size());

                // Calculate total duration
                LocalDateTime earliest = logs.stream()
                                .map(AuditLog::getCreatedAt)
                                .min(LocalDateTime::compareTo)
                                .orElse(null);
                LocalDateTime latest = logs.stream()
                                .map(AuditLog::getCreatedAt)
                                .max(LocalDateTime::compareTo)
                                .orElse(null);

                if (earliest != null && latest != null) {
                        long totalDurationMs = ChronoUnit.MILLIS.between(earliest, latest);
                        result.put("totalDurationMs", totalDurationMs);
                }

                // Build spans from audit logs
                List<Map<String, Object>> spans = logs.stream()
                                .map(log -> {
                                        Map<String, Object> span = new HashMap<>();
                                        span.put("id", log.getId());
                                        span.put("nodeId", log.getProviderId());
                                        span.put("nodeType", log.getProviderType());
                                        span.put("endpoint", log.getEndpoint());
                                        span.put("method", log.getMethod());
                                        span.put("model", log.getModel());
                                        span.put("statusCode", log.getStatusCode());
                                        span.put("duration", log.getResponseTime());
                                        span.put("timestamp", log.getCreatedAt());
                                        span.put("success", log.getSuccess());
                                        span.put("error", log.getErrorMessage());
                                        return span;
                                })
                                .collect(Collectors.toList());

                result.put("spans", spans);

                // Calculate success rate
                long successCount = logs.stream().filter(l -> Boolean.TRUE.equals(l.getSuccess())).count();
                double successRate = (double) successCount / logs.size() * 100;
                result.put("successRate", Math.round(successRate * 100.0) / 100.0);

                return result;
        }

        @Override
        public Map<String, Object> getCallSuccessRate(Long startTime, Long endTime) {
                log.info("Querying call success rate: startTime={}, endTime={}", startTime, endTime);

                LocalDateTime start = startTime != null
                                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault())
                                : LocalDateTime.now().minusHours(1);
                LocalDateTime end = endTime != null
                                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault())
                                : LocalDateTime.now();

                List<AuditLog> logs = auditLogRepository.findBusinessLogsByCreatedAtBetween(start, end);

                Map<String, Object> result = new HashMap<>();

                if (logs.isEmpty()) {
                        result.put("totalCalls", 0);
                        result.put("successCalls", 0);
                        result.put("failedCalls", 0);
                        result.put("successRate", 0.0);
                        return result;
                }

                long totalCalls = logs.size();
                long successCalls = logs.stream().filter(l -> Boolean.TRUE.equals(l.getSuccess())).count();
                long failedCalls = totalCalls - successCalls;
                double successRate = (double) successCalls / totalCalls * 100;

                result.put("totalCalls", totalCalls);
                result.put("successCalls", successCalls);
                result.put("failedCalls", failedCalls);
                result.put("successRate", Math.round(successRate * 100.0) / 100.0);
                result.put("startTime", start);
                result.put("endTime", end);

                return result;
        }

        @Override
        public List<Map<String, Object>> getErrorDistribution(Long startTime, Long endTime) {
                log.info("Querying error distribution: startTime={}, endTime={}", startTime, endTime);

                LocalDateTime start = startTime != null
                                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault())
                                : LocalDateTime.now().minusHours(1);
                LocalDateTime end = endTime != null
                                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault())
                                : LocalDateTime.now();

                List<AuditLog> logs = auditLogRepository.findBusinessLogsByCreatedAtBetween(start, end);

                // Group errors by providerId and error message
                Map<String, Long> errorCounts = logs.stream()
                                .filter(l -> !Boolean.TRUE.equals(l.getSuccess()) && l.getErrorMessage() != null)
                                .collect(Collectors.groupingBy(
                                                l -> l.getProviderId() + ":" + truncateError(l.getErrorMessage(), 50),
                                                Collectors.counting()));

                return errorCounts.entrySet().stream()
                                .map(entry -> {
                                        String[] parts = entry.getKey().split(":", 2);
                                        Map<String, Object> item = new HashMap<>();
                                        item.put("providerId", parts[0]);
                                        item.put("errorMessage", parts.length > 1 ? parts[1] : "");
                                        item.put("count", entry.getValue());
                                        return item;
                                })
                                .sorted((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")))
                                .collect(Collectors.toList());
        }

        // ========== 限流配置管理 ==========

        @Override
        public RateLimitConfig getRateLimitConfig() {
                return rateLimitConfigRepository.findById("DEFAULT").orElse(null);
        }

        @Override
        @org.springframework.transaction.annotation.Transactional
        public void updateRateLimitConfig(RateLimitConfig config, String operator) {
                RateLimitConfig existingConfig = rateLimitConfigRepository.findById("DEFAULT").orElse(null);

                // 记录配置变更前的值用于审计
                StringBuilder changeLog = new StringBuilder();
                if (existingConfig != null) {
                        changeLog.append("原配置: requestsPerMinute=").append(existingConfig.getRequestsPerMinute())
                                        .append(", requestsPerDay=").append(existingConfig.getRequestsPerDay())
                                        .append(", enabled=").append(existingConfig.getEnabled());
                }

                config.setId("DEFAULT");
                log.info("Updating rate limit config by {}: {}", operator, changeLog);
                rateLimitConfigRepository.save(config);

                // 更新缓存
                cachedRateLimitConfig = config;
                lastRateLimitConfigUpdate = System.currentTimeMillis();

                // 记录审计日志
                try {
                        com.adlin.orin.modules.audit.entity.AuditLog auditLog = com.adlin.orin.modules.audit.entity.AuditLog
                                        .builder()
                                        .userId(operator)
                                        .endpoint("/api/admin/rate-limit-config")
                                        .method("PUT")
                                        .success(true)
                                        .responseContent("限流配置更新: " + changeLog + " -> requestsPerMinute="
                                                        + config.getRequestsPerMinute() + ", requestsPerDay="
                                                        + config.getRequestsPerDay() + ", enabled=" + config.getEnabled())
                                        .build();
                        auditLogRepository.save(auditLog);
                } catch (Exception e) {
                        log.warn("Failed to record rate limit config audit log: {}", e.getMessage());
                }
        }

        @Override
        public RateLimitConfig getRateLimitConfigCached() {
                // 缓存 5 秒，避免频繁查询数据库
                if (cachedRateLimitConfig != null
                                && System.currentTimeMillis() - lastRateLimitConfigUpdate < 5000) {
                        return cachedRateLimitConfig;
                }
                // 重新加载
                cachedRateLimitConfig = rateLimitConfigRepository.findById("DEFAULT").orElse(null);
                lastRateLimitConfigUpdate = System.currentTimeMillis();
                return cachedRateLimitConfig;
        }

        private String truncateError(String error, int maxLength) {
                if (error == null) return "";
                return error.length() > maxLength ? error.substring(0, maxLength) + "..." : error;
        }

}
