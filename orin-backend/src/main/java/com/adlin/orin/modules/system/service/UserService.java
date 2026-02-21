package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.model.repository.ModelMetadataRepository;
import com.adlin.orin.modules.system.dto.UserDashboardResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final SysUserRepository userRepository;
    private final RoleService roleService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ModelMetadataRepository modelMetadataRepository;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public UserService(SysUserRepository userRepository, RoleService roleService,
            KnowledgeBaseRepository knowledgeBaseRepository,
            ModelMetadataRepository modelMetadataRepository,
            AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.modelMetadataRepository = modelMetadataRepository;
        this.auditLogRepository = auditLogRepository;
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

        // 1. Stats
        long kbCount = knowledgeBaseRepository.count();
        long modelCount = modelMetadataRepository.count();
        Long totalTokens = auditLogRepository.sumTokensByUserIdAndCreatedAtAfter(user.getUserId().toString(),
                LocalDateTime.now().minusYears(100));

        List<UserDashboardResponse.StatCard> stats = new ArrayList<>();
        stats.add(UserDashboardResponse.StatCard.builder()
                .label("知识库")
                .value(String.valueOf(kbCount))
                .trend(5.0)
                .icon("Collection")
                .color("linear-gradient(135deg, #667eea 0%, #764ba2 100%)")
                .build());
        stats.add(UserDashboardResponse.StatCard.builder()
                .label("已训模型")
                .value(String.valueOf(modelCount))
                .trend(2.0)
                .icon("DataAnalysis")
                .color("linear-gradient(135deg, #f093fb 0%, #f5576c 100%)")
                .build());
        stats.add(UserDashboardResponse.StatCard.builder()
                .label("Token 消耗")
                .value(totalTokens != null ? (totalTokens / 1000) + "k" : "0")
                .trend(12.5)
                .icon("ChatDotRound")
                .color("linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)")
                .build());
        stats.add(UserDashboardResponse.StatCard.builder()
                .label("活跃天数")
                .value("12")
                .trend(0.0)
                .icon("Calendar")
                .color("linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)")
                .build());

        // 2. Activity Data (Last 7 Days Mock logic structure mapped to real days if
        // possible, using random for visual until full grouping)
        List<UserDashboardResponse.ActivityData> activityData = new ArrayList<>();
        String[] days = { "周一", "周二", "周三", "周四", "周五", "周六", "周日" };
        for (int i = 0; i < 7; i++) {
            activityData.add(UserDashboardResponse.ActivityData.builder()
                    .label(days[i])
                    .value((int) (Math.random() * 60) + 20)
                    .count((int) (Math.random() * 20) + 5)
                    .build());
        }

        // 3. Activity Logs (Top 5 recent audit logs)
        List<UserDashboardResponse.ActivityLog> activityLogs = new ArrayList<>();
        List<AuditLog> recentLogs = auditLogRepository.findByUserIdOrderByCreatedAtDesc(user.getUserId().toString(),
                org.springframework.data.domain.PageRequest.of(0, 5)).getContent();

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");
        if (recentLogs.isEmpty()) {
            activityLogs.add(UserDashboardResponse.ActivityLog.builder()
                    .action("系统初始化")
                    .detail("欢迎加入 ORIN 系统")
                    .time("刚刚")
                    .type("success")
                    .build());
        } else {
            for (AuditLog log : recentLogs) {
                boolean isSuccess = Boolean.TRUE.equals(log.getSuccess());
                String actionName = "API 调用";
                if (log.getEndpoint().contains("models"))
                    actionName = "模型调用";
                else if (log.getEndpoint().contains("knowledge"))
                    actionName = "知识库检索";
                else if (log.getEndpoint().contains("chat"))
                    actionName = "会话执行";

                long hoursAgo = ChronoUnit.HOURS.between(log.getCreatedAt(), LocalDateTime.now());
                String timeStr = hoursAgo < 24 ? hoursAgo + "小时前" : log.getCreatedAt().format(timeFormatter);

                activityLogs.add(UserDashboardResponse.ActivityLog.builder()
                        .action(actionName)
                        .detail(isSuccess ? "执行成功 (" + log.getProviderId() + ")" : "执行失败: " + log.getErrorMessage())
                        .time(timeStr)
                        .type(isSuccess ? "success" : "danger")
                        .build());
            }
        }

        // 4. Skills
        // Simplified fallback for now
        List<String> skills = Arrays.asList("AI/ML", "Agent Builder", "Knowledge Graph", "Prompt Engineering");

        return UserDashboardResponse.builder()
                .stats(stats)
                .activityData(activityData)
                .activityLogs(activityLogs)
                .skills(skills)
                .build();
    }
}
