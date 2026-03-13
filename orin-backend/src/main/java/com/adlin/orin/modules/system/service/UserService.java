package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.system.dto.UserDashboardResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final SysUserRepository userRepository;
    private final RoleService roleService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AuditLogRepository auditLogRepository;
    private final AgentMetadataRepository agentMetadataRepository;

    @Autowired
    public UserService(SysUserRepository userRepository, RoleService roleService,
            KnowledgeBaseRepository knowledgeBaseRepository,
            AuditLogRepository auditLogRepository,
            AgentMetadataRepository agentMetadataRepository) {
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.auditLogRepository = auditLogRepository;
        this.agentMetadataRepository = agentMetadataRepository;
    }

    public Optional<SysUser> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<SysUser> getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    @Transactional
    public SysUser updateUser(SysUser user) {
        return userRepository.save(user);
    }

    @Transactional
    public void updateAvatar(Long userId, String avatarUrl) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setAvatar(avatarUrl);
            userRepository.save(user);
        });
    }

    public UserDashboardResponse getUserDashboard(String username) {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 1. Stats - Get real data
        long kbCount = knowledgeBaseRepository.count();
        long agentCount = agentMetadataRepository.count();
        Long totalTokens = auditLogRepository.sumTokensByUserIdAndCreatedAtAfter(user.getUserId().toString(),
                LocalDateTime.now().minusYears(100));

        // Calculate active days from audit logs
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<AuditLog> recentAuditLogs = auditLogRepository.findByUserIdOrderByCreatedAtDesc(
                user.getUserId().toString(),
                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();

        Set<LocalDate> activeDates = new HashSet<>();
        for (AuditLog auditLog : recentAuditLogs) {
            if (auditLog.getCreatedAt() != null) {
                activeDates.add(auditLog.getCreatedAt().toLocalDate());
            }
        }
        int activeDays = activeDates.size();

        // Calculate trends (compare last 30 days with previous 30 days)
        LocalDateTime sixtyDaysAgo = LocalDateTime.now().minusDays(60);
        LocalDateTime thirtyOneDaysAgo = LocalDateTime.now().minusDays(31);
        List<AuditLog> olderLogs = auditLogRepository.findBusinessLogsByCreatedAtBetween(
                sixtyDaysAgo, thirtyOneDaysAgo);

        double tokenTrend = 0.0;
        if (totalTokens != null && totalTokens > 0) {
            long oldTokens = olderLogs.stream()
                    .filter(auditLog -> auditLog.getTotalTokens() != null)
                    .mapToLong(AuditLog::getTotalTokens)
                    .sum();
            if (oldTokens > 0) {
                tokenTrend = ((double) (totalTokens - oldTokens) / oldTokens) * 100;
            }
        }

        List<UserDashboardResponse.StatCard> stats = new ArrayList<>();
        stats.add(UserDashboardResponse.StatCard.builder()
                .label("知识库")
                .value(String.valueOf(kbCount))
                .trend(0.0)
                .icon("Collection")
                .color("linear-gradient(135deg, #667eea 0%, #764ba2 100%)")
                .build());
        stats.add(UserDashboardResponse.StatCard.builder()
                .label("AI 智能体")
                .value(String.valueOf(agentCount))
                .trend(0.0)
                .icon("DataAnalysis")
                .color("linear-gradient(135deg, #f093fb 0%, #f5576c 100%)")
                .build());
        stats.add(UserDashboardResponse.StatCard.builder()
                .label("Token 消耗")
                .value(totalTokens != null ? formatTokenCount(totalTokens) : "0")
                .trend(tokenTrend)
                .icon("ChatDotRound")
                .color("linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)")
                .build());
        stats.add(UserDashboardResponse.StatCard.builder()
                .label("活跃天数")
                .value(String.valueOf(activeDays))
                .trend(0.0)
                .icon("Calendar")
                .color("linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)")
                .build());

        // 2. Activity Data - Real data for last 7 days
        List<UserDashboardResponse.ActivityData> activityData = new ArrayList<>();
        LocalDate today = LocalDate.now();
        String[] dayNames = { "周一", "周二", "周三", "周四", "周五", "周六", "周日" };

        // Get audit logs from last 7 days
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<AuditLog> weekLogs = recentAuditLogs.stream()
                .filter(auditLog -> auditLog.getCreatedAt() != null && auditLog.getCreatedAt().isAfter(sevenDaysAgo))
                .collect(Collectors.toList());

        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            int dayOfWeek = day.getDayOfWeek().getValue(); // 1=Monday, 7=Sunday

            // Count logs for this day
            final LocalDate targetDate = day;
            long dayCount = weekLogs.stream()
                    .filter(auditLog -> auditLog.getCreatedAt().toLocalDate().equals(targetDate))
                    .count();

            // Calculate max value for percentage
            int maxCount = 1;
            int finalI = i;
            final LocalDate targetDay = day;
            int currentCount = (int) weekLogs.stream()
                    .filter(auditLog -> auditLog.getCreatedAt().toLocalDate().equals(targetDay))
                    .count();

            activityData.add(UserDashboardResponse.ActivityData.builder()
                    .label(dayNames[dayOfWeek - 1])
                    .value(Math.max(20, currentCount * 10))
                    .count(currentCount)
                    .build());
        }

        // 3. Activity Logs (Top 5 recent audit logs)
        List<UserDashboardResponse.ActivityLog> activityLogs = new ArrayList<>();

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");
        if (recentAuditLogs.isEmpty()) {
            activityLogs.add(UserDashboardResponse.ActivityLog.builder()
                    .action("系统初始化")
                    .detail("欢迎加入 ORIN 系统")
                    .time("刚刚")
                    .type("success")
                    .build());
        } else {
            List<AuditLog> top5Logs = recentAuditLogs.stream().limit(5).collect(Collectors.toList());
            for (AuditLog auditLog : top5Logs) {
                boolean isSuccess = Boolean.TRUE.equals(auditLog.getSuccess());
                String actionName = "API 调用";
                if (auditLog.getEndpoint() != null) {
                    if (auditLog.getEndpoint().contains("models"))
                        actionName = "模型调用";
                    else if (auditLog.getEndpoint().contains("knowledge"))
                        actionName = "知识库检索";
                    else if (auditLog.getEndpoint().contains("chat"))
                        actionName = "会话执行";
                }

                long hoursAgo = ChronoUnit.HOURS.between(auditLog.getCreatedAt(), LocalDateTime.now());
                String timeStr;
                if (hoursAgo < 1) {
                    timeStr = "刚刚";
                } else if (hoursAgo < 24) {
                    timeStr = hoursAgo + "小时前";
                } else {
                    timeStr = auditLog.getCreatedAt().format(timeFormatter);
                }

                String detail = isSuccess ? "执行成功" : "执行失败";
                if (auditLog.getProviderId() != null) {
                    detail = isSuccess ? "执行成功 (" + auditLog.getProviderId() + ")" : "执行失败: " + (auditLog.getErrorMessage() != null ? auditLog.getErrorMessage() : "未知错误");
                }

                activityLogs.add(UserDashboardResponse.ActivityLog.builder()
                        .action(actionName)
                        .detail(detail)
                        .time(timeStr)
                        .type(isSuccess ? "success" : "danger")
                        .build());
            }
        }

        // 4. Skills - Based on actual agent types
        List<String> skills = new ArrayList<>();
        if (agentCount > 0) {
            skills.add("AI Agent");
        }
        if (kbCount > 0) {
            skills.add("知识管理");
        }
        if (totalTokens != null && totalTokens > 100000) {
            skills.add("大模型应用");
        }
        if (skills.isEmpty()) {
            skills = Arrays.asList("AI/ML", "Agent Builder");
        }

        return UserDashboardResponse.builder()
                .stats(stats)
                .activityData(activityData)
                .activityLogs(activityLogs)
                .skills(skills)
                .build();
    }

    private String formatTokenCount(long tokens) {
        if (tokens >= 1_000_000) {
            return String.format("%.1fM", tokens / 1_000_000.0);
        } else if (tokens >= 1_000) {
            return String.format("%.1fK", tokens / 1_000.0);
        } else {
            return String.valueOf(tokens);
        }
    }
}
