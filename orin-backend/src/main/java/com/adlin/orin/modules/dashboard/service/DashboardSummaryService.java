package com.adlin.orin.modules.dashboard.service;

import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.alert.repository.AlertHistoryRepository;
import com.adlin.orin.modules.apikey.repository.ApiKeyRepository;
import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskStatus;
import com.adlin.orin.modules.task.repository.TaskRepository;
import com.adlin.orin.modules.trace.repository.WorkflowTraceRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardSummaryService {

    private static final List<String> ADMIN_ROLES = List.of("ROLE_ADMIN", "ROLE_PLATFORM_ADMIN", "ROLE_SUPER_ADMIN", "ADMIN");
    private static final List<String> OPERATOR_ROLES = List.of("ROLE_OPERATOR");

    private final AgentMetadataRepository agentMetadataRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final CollaborationPackageRepository collaborationPackageRepository;
    private final TaskRepository taskRepository;
    private final WorkflowTraceRepository workflowTraceRepository;
    private final AuditLogRepository auditLogRepository;
    private final SysUserRepository sysUserRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final AlertHistoryRepository alertHistoryRepository;
    private final RestTemplate restTemplate;

    @Value("${orin.ai-engine.url:http://127.0.0.1:8000}")
    private String aiEngineUrl;

    public Map<String, Object> getSummary(Authentication authentication) {
        List<String> roles = extractRoles(authentication);
        boolean isAdmin = hasAnyRole(roles, ADMIN_ROLES);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("roles", roles);
        summary.put("defaultHome", resolveDefaultHome(roles));
        summary.put("systemHealth", systemHealth());
        summary.put("metrics", metrics());
        summary.put("recentActivity", recentActivity());
        summary.put("quickLinks", quickLinks(roles));
        if (isAdmin) {
            summary.put("adminStats", adminStats());
            summary.put("topAlertEvents", topAlertEvents());
        }
        summary.put("generatedAt", LocalDateTime.now().toString());
        return summary;
    }

    private List<String> extractRoles(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return List.of("ROLE_USER");
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(role -> role != null && !role.isBlank())
                .distinct()
                .toList();
        return roles.isEmpty() ? List.of("ROLE_USER") : roles;
    }

    private String resolveDefaultHome(List<String> roles) {
        if (hasAnyRole(roles, ADMIN_ROLES)) {
            return "/dashboard/runtime/overview";
        }
        if (hasAnyRole(roles, OPERATOR_ROLES)) {
            return "/dashboard/applications/agents";
        }
        return "/portal";
    }

    private Map<String, Object> systemHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("backend", Map.of("status", "UP"));
        health.put("aiEngine", aiEngineHealth());
        return health;
    }

    private Map<String, Object> aiEngineHealth() {
        String endpoint = aiEngineUrl.replaceAll("/+$", "") + "/health";
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(endpoint, Map.class);
            Map<?, ?> body = response.getBody() != null ? response.getBody() : Map.of();
            return Map.of(
                    "status", normalizeHealthStatus(body.get("status")),
                    "service", valueOrDefault(body.get("service"), "orin-ai-engine"),
                    "reachable", true
            );
        } catch (Exception e) {
            log.debug("AI Engine health probe failed: {}", e.getMessage());
            return Map.of(
                    "status", "DOWN",
                    "service", "orin-ai-engine",
                    "reachable", false
            );
        }
    }

    private Map<String, Object> metrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("agents", safeCount(agentMetadataRepository::count));
        metrics.put("knowledgeBases", safeCount(knowledgeBaseRepository::count));
        metrics.put("workflows", safeCount(workflowDefinitionRepository::count));
        metrics.put("collaborationPackages", safeCount(collaborationPackageRepository::count));
        metrics.put("traces", safeCount(workflowTraceRepository::count));

        Map<String, Long> tasks = taskStatusCounts();
        metrics.put("tasks", tasks);
        metrics.put("openTasks", tasks.getOrDefault("QUEUED", 0L)
                + tasks.getOrDefault("RUNNING", 0L)
                + tasks.getOrDefault("RETRYING", 0L));
        metrics.put("failedTasks", tasks.getOrDefault("FAILED", 0L) + tasks.getOrDefault("DEAD", 0L));
        return metrics;
    }

    private Map<String, Long> taskStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status.name(), 0L);
        }
        try {
            for (Object[] row : taskRepository.countByStatus()) {
                if (row.length < 2 || row[0] == null || row[1] == null) {
                    continue;
                }
                counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
            }
        } catch (Exception e) {
            log.debug("Task status count aggregation failed: {}", e.getMessage());
        }
        return counts;
    }

    private List<Map<String, Object>> recentActivity() {
        try {
            return auditLogRepository
                    .findAll(PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt")))
                    .getContent()
                    .stream()
                    .map(this::sanitizeAuditLog)
                    .toList();
        } catch (Exception e) {
            log.debug("Dashboard recent activity aggregation failed: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> adminStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", safeCount(sysUserRepository::count));
        stats.put("totalApiKeys", safeCount(apiKeyRepository::count));
        stats.put("activeAlerts", alertHistoryCount("TRIGGERED"));
        stats.put("resolvedAlerts", alertHistoryCount("RESOLVED"));
        return stats;
    }

    private long alertHistoryCount(String status) {
        try {
            return alertHistoryRepository.countByStatus(status);
        } catch (Exception e) {
            log.debug("Alert history count failed for status {}: {}", status, e.getMessage());
            return 0L;
        }
    }

    private List<Map<String, Object>> topAlertEvents() {
        try {
            return auditLogRepository
                    .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                    .getContent()
                    .stream()
                    .filter(log -> log.getSuccess() != null && !log.getSuccess())
                    .map(log -> Map.<String, Object>of(
                            "endpoint", log.getEndpoint() != null ? log.getEndpoint() : "",
                            "method", log.getMethod() != null ? log.getMethod() : "",
                            "statusCode", log.getStatusCode() != null ? log.getStatusCode() : 0,
                            "createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : ""
                    ))
                    .toList();
        } catch (Exception e) {
            log.debug("Top alert events aggregation failed: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> sanitizeAuditLog(AuditLog log) {
        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("id", log.getId());
        activity.put("endpoint", log.getEndpoint());
        activity.put("method", log.getMethod());
        activity.put("success", log.getSuccess());
        activity.put("statusCode", log.getStatusCode());
        activity.put("providerType", log.getProviderType());
        activity.put("traceId", log.getTraceId());
        activity.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
        return activity;
    }

    private List<Map<String, String>> quickLinks(List<String> roles) {
        if (hasAnyRole(roles, ADMIN_ROLES)) {
            return List.of(
                    link("运行监控", "/dashboard/runtime/overview"),
                    link("统一网关", "/dashboard/control/gateway"),
                    link("API Key 管理", "/dashboard/control/gateway?workspace=access"),
                    link("系统审计", "/dashboard/control/audit-logs")
            );
        }
        if (hasAnyRole(roles, OPERATOR_ROLES)) {
            return List.of(
                    link("智能体列表", "/dashboard/applications/agents"),
                    link("工作流中心", "/dashboard/applications/workflows"),
                    link("知识资产", "/dashboard/resources/assets"),
                    link("协作看板", "/dashboard/applications/collaboration/dashboard")
            );
        }
        return List.of(
                link("服务门户", "/portal"),
                link("我的 API Key", "/portal/api-keys")
        );
    }

    private Map<String, String> link(String title, String path) {
        return Map.of("title", title, "path", path);
    }

    private boolean hasAnyRole(List<String> roles, List<String> targetRoles) {
        return targetRoles.stream().anyMatch(roles::contains);
    }

    private long safeCount(Supplier<Long> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.debug("Dashboard count aggregation failed: {}", e.getMessage());
            return 0L;
        }
    }

    private String normalizeHealthStatus(Object rawStatus) {
        String status = valueOrDefault(rawStatus, "UNKNOWN").toUpperCase();
        return "OK".equals(status) ? "UP" : status;
    }

    private String valueOrDefault(Object value, String fallback) {
        String text = value != null ? String.valueOf(value) : "";
        return text.isBlank() ? fallback : text;
    }
}
