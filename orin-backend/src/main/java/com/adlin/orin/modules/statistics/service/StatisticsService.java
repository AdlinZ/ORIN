package com.adlin.orin.modules.statistics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 统计分析服务
 * 提供多维度统计分析功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 获取用户活跃度统计
     */
    public Map<String, Object> getUserActivityStats(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new HashMap<>();
        
        // 每日活跃用户
        String dailyActiveSql = """
            SELECT DATE(created_at) as date, COUNT(DISTINCT created_by) as count
            FROM audit_log
            WHERE created_at BETWEEN ? AND ?
            GROUP BY DATE(created_at)
            ORDER BY date
            """;
        
        List<Map<String, Object>> dailyActive = jdbcTemplate.query(dailyActiveSql, 
            (rs, rowNum) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("date", rs.getString("date"));
                row.put("count", rs.getInt("count"));
                return row;
            },
            startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        stats.put("dailyActiveUsers", dailyActive);
        
        // 总活跃用户
        String totalActiveSql = """
            SELECT COUNT(DISTINCT created_by) as count
            FROM audit_log
            WHERE created_at BETWEEN ? AND ?
            """;
        
        Integer totalActive = jdbcTemplate.queryForObject(totalActiveSql, Integer.class,
            startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        stats.put("totalActiveUsers", totalActive != null ? totalActive : 0);
        
        return stats;
    }

    /**
     * 获取智能体调用统计
     */
    public Map<String, Object> getAgentCallStats(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new HashMap<>();
        
        // 每日调用量
        String dailyCallsSql = """
            SELECT DATE(created_at) as date, COUNT(*) as count
            FROM audit_log
            WHERE operation_type LIKE '%AGENT%' AND created_at BETWEEN ? AND ?
            GROUP BY DATE(created_at)
            ORDER BY date
            """;
        
        List<Map<String, Object>> dailyCalls = jdbcTemplate.query(dailyCallsSql,
            (rs, rowNum) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("date", rs.getString("date"));
                row.put("count", rs.getInt("count"));
                return row;
            },
            startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        stats.put("dailyCalls", dailyCalls);
        
        // 总调用量
        String totalCallsSql = """
            SELECT COUNT(*) as count
            FROM audit_log
            WHERE operation_type LIKE '%AGENT%' AND created_at BETWEEN ? AND ?
            """;
        
        Integer totalCalls = jdbcTemplate.queryForObject(totalCallsSql, Integer.class,
            startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        stats.put("totalCalls", totalCalls != null ? totalCalls : 0);
        
        // 按智能体统计
        String agentStatsSql = """
            SELECT operation_type, COUNT(*) as count
            FROM audit_log
            WHERE operation_type LIKE '%AGENT%' AND created_at BETWEEN ? AND ?
            GROUP BY operation_type
            ORDER BY count DESC
            LIMIT 10
            """;
        
        List<Map<String, Object>> agentStats = jdbcTemplate.query(agentStatsSql,
            (rs, rowNum) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("agent", rs.getString("operation_type"));
                row.put("count", rs.getInt("count"));
                return row;
            },
            startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        stats.put("topAgents", agentStats);
        
        return stats;
    }

    /**
     * 获取知识库使用统计
     */
    public Map<String, Object> getKnowledgeStats(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new HashMap<>();
        
        // 知识库检索次数
        String searchCountSql = """
            SELECT COUNT(*) as count
            FROM audit_log
            WHERE operation_type = 'KNOWLEDGE_SEARCH' AND created_at BETWEEN ? AND ?
            """;
        
        Integer searchCount = jdbcTemplate.queryForObject(searchCountSql, Integer.class,
            startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        stats.put("searchCount", searchCount != null ? searchCount : 0);
        
        // 文档上传数量
        String uploadCountSql = """
            SELECT COUNT(*) as count
            FROM audit_log
            WHERE operation_type = 'DOCUMENT_UPLOAD' AND created_at BETWEEN ? AND ?
            """;
        
        Integer uploadCount = jdbcTemplate.queryForObject(uploadCountSql, Integer.class,
            startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        stats.put("uploadCount", uploadCount != null ? uploadCount : 0);
        
        return stats;
    }

    /**
     * 获取 Token 消耗统计
     */
    public Map<String, Object> getTokenStats(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new HashMap<>();
        
        // 每日 Token 消耗
        String dailyTokensSql = """
            SELECT DATE(created_at) as date, 
                   SUM(total_tokens) as total,
                   SUM(prompt_tokens) as prompt,
                   SUM(completion_tokens) as completion
            FROM audit_log
            WHERE total_tokens > 0 AND created_at BETWEEN ? AND ?
            GROUP BY DATE(created_at)
            ORDER BY date
            """;
        
        List<Map<String, Object>> dailyTokens = jdbcTemplate.query(dailyTokensSql,
            (rs, rowNum) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("date", rs.getString("date"));
                row.put("total", rs.getLong("total"));
                row.put("prompt", rs.getLong("prompt"));
                row.put("completion", rs.getLong("completion"));
                return row;
            },
            startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        stats.put("dailyTokens", dailyTokens);
        
        // 总 Token 消耗
        String totalTokensSql = """
            SELECT SUM(total_tokens) as total,
                   SUM(prompt_tokens) as prompt,
                   SUM(completion_tokens) as completion
            FROM audit_log
            WHERE total_tokens > 0 AND created_at BETWEEN ? AND ?
            """;
        
        Map<String, Object> totalTokens = jdbcTemplate.queryForMap(totalTokensSql,
            startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        stats.put("totalTokens", totalTokens);
        
        return stats;
    }

    /**
     * 获取任务执行统计
     */
    public Map<String, Object> getTaskStats(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new HashMap<>();
        
        // 按状态统计
        String statusStatsSql = """
            SELECT status, COUNT(*) as count
            FROM task_queue
            WHERE created_at BETWEEN ? AND ?
            GROUP BY status
            """;
        
        List<Map<String, Object>> statusStats = jdbcTemplate.query(statusStatsSql,
            (rs, rowNum) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("status", rs.getString("status"));
                row.put("count", rs.getInt("count"));
                return row;
            },
            startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        stats.put("byStatus", statusStats);
        
        // 按优先级统计
        String priorityStatsSql = """
            SELECT priority, COUNT(*) as count
            FROM task_queue
            WHERE created_at BETWEEN ? AND ?
            GROUP BY priority
            ORDER BY priority DESC
            """;
        
        List<Map<String, Object>> priorityStats = jdbcTemplate.query(priorityStatsSql,
            (rs, rowNum) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("priority", rs.getInt("priority"));
                row.put("count", rs.getInt("count"));
                return row;
            },
            startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        stats.put("byPriority", priorityStats);
        
        return stats;
    }

    /**
     * 获取综合统计概览
     */
    public Map<String, Object> getOverviewStats() {
        Map<String, Object> overview = new HashMap<>();
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        LocalDateTime weekAgo = today.minusDays(7);
        LocalDateTime monthAgo = today.minusDays(30);
        
        // 今日统计
        overview.put("today", getDailyStats(today, now));
        
        // 本周统计
        overview.put("week", getDailyStats(weekAgo, now));
        
        // 本月统计
        overview.put("month", getDailyStats(monthAgo, now));
        
        return overview;
    }

    private Map<String, Object> getDailyStats(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> stats = new HashMap<>();
        
        // 用户活跃数
        String activeUsersSql = "SELECT COUNT(DISTINCT created_by) FROM audit_log WHERE created_at BETWEEN ? AND ?";
        Integer activeUsers = jdbcTemplate.queryForObject(activeUsersSql, Integer.class, start, end);
        stats.put("activeUsers", activeUsers != null ? activeUsers : 0);
        
        // API 调用次数
        String apiCallsSql = "SELECT COUNT(*) FROM audit_log WHERE created_at BETWEEN ? AND ?";
        Integer apiCalls = jdbcTemplate.queryForObject(apiCallsSql, Integer.class, start, end);
        stats.put("apiCalls", apiCalls != null ? apiCalls : 0);
        
        // Token 消耗
        String tokensSql = "SELECT COALESCE(SUM(total_tokens), 0) FROM audit_log WHERE created_at BETWEEN ? AND ?";
        Long tokens = jdbcTemplate.queryForObject(tokensSql, Long.class, start, end);
        stats.put("tokens", tokens != null ? tokens : 0);
        
        // 任务数
        String tasksSql = "SELECT COUNT(*) FROM task_queue WHERE created_at BETWEEN ? AND ?";
        Integer tasks = jdbcTemplate.queryForObject(tasksSql, Integer.class, start, end);
        stats.put("tasks", tasks != null ? tasks : 0);
        
        return stats;
    }
}
