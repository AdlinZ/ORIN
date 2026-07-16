package com.adlin.orin.modules.dashboard.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardSummaryServiceTest {

    private AgentMetadataRepository agentMetadataRepository;
    private KnowledgeBaseRepository knowledgeBaseRepository;
    private WorkflowDefinitionRepository workflowDefinitionRepository;
    private CollaborationPackageRepository collaborationPackageRepository;
    private TaskRepository taskRepository;
    private WorkflowTraceRepository workflowTraceRepository;
    private AuditLogRepository auditLogRepository;
    private SysUserRepository sysUserRepository;
    private ApiKeyRepository apiKeyRepository;
    private AlertHistoryRepository alertHistoryRepository;
    private RestTemplate restTemplate;
    private DashboardSummaryService service;

    @BeforeEach
    void setUp() {
        agentMetadataRepository = mock(AgentMetadataRepository.class);
        knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        workflowDefinitionRepository = mock(WorkflowDefinitionRepository.class);
        collaborationPackageRepository = mock(CollaborationPackageRepository.class);
        taskRepository = mock(TaskRepository.class);
        workflowTraceRepository = mock(WorkflowTraceRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        sysUserRepository = mock(SysUserRepository.class);
        apiKeyRepository = mock(ApiKeyRepository.class);
        alertHistoryRepository = mock(AlertHistoryRepository.class);
        restTemplate = mock(RestTemplate.class);

        service = new DashboardSummaryService(
                agentMetadataRepository,
                knowledgeBaseRepository,
                workflowDefinitionRepository,
                collaborationPackageRepository,
                taskRepository,
                workflowTraceRepository,
                auditLogRepository,
                sysUserRepository,
                apiKeyRepository,
                alertHistoryRepository,
                restTemplate
        );
        ReflectionTestUtils.setField(service, "aiEngineUrl", "http://ai-engine.local");
    }

    @Test
    void buildsAdminSummaryAndSanitizesRecentActivity() {
        when(agentMetadataRepository.count()).thenReturn(3L);
        when(knowledgeBaseRepository.count()).thenReturn(4L);
        when(workflowDefinitionRepository.count()).thenReturn(5L);
        when(collaborationPackageRepository.count()).thenReturn(6L);
        when(workflowTraceRepository.count()).thenReturn(7L);
        when(taskRepository.countByStatus()).thenReturn(List.of(
                new Object[]{TaskStatus.QUEUED, 2L},
                new Object[]{TaskStatus.RUNNING, 1L},
                new Object[]{TaskStatus.FAILED, 3L}
        ));
        when(restTemplate.getForEntity(eq("http://ai-engine.local/health"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "ok", "service", "orin-ai-engine")));
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(auditLog())));
        when(auditLogRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(agentMetadataRepository.findAll()).thenReturn(List.of());
        when(sysUserRepository.count()).thenReturn(12L);
        when(apiKeyRepository.count()).thenReturn(5L);
        when(alertHistoryRepository.countByStatus("TRIGGERED")).thenReturn(2L);
        when(alertHistoryRepository.countByStatus("RESOLVED")).thenReturn(8L);

        Map<String, Object> summary = service.getSummary(new UsernamePasswordAuthenticationToken(
                "11",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));

        assertThat(summary.get("defaultHome")).isEqualTo("/dashboard/runtime/overview");
        assertTrue(((List<?>) summary.get("roles")).contains("ROLE_ADMIN"));

        Map<?, ?> metrics = (Map<?, ?>) summary.get("metrics");
        assertThat(metrics.get("agents")).isEqualTo(3L);
        assertThat(metrics.get("openTasks")).isEqualTo(3L);
        assertThat(metrics.get("failedTasks")).isEqualTo(3L);

        Map<?, ?> adminStats = (Map<?, ?>) summary.get("adminStats");
        assertThat(adminStats.get("totalUsers")).isEqualTo(12L);
        assertThat(adminStats.get("totalApiKeys")).isEqualTo(5L);
        assertThat(adminStats.get("activeAlerts")).isEqualTo(2L);
        assertThat(adminStats.get("resolvedAlerts")).isEqualTo(8L);

        List<?> topAlerts = (List<?>) summary.get("topAlertEvents");
        assertThat(topAlerts).isNotNull();

        Map<?, ?> trends = (Map<?, ?>) summary.get("trends");
        assertThat(trends).isNotNull();
        assertThat((List<?>) trends.get("requestCount")).hasSize(14);
        assertThat((List<?>) trends.get("tokenUsage")).hasSize(14);
        assertThat(trends.get("range")).isNotNull();

        List<?> agentTypes = (List<?>) summary.get("agentTypes");
        assertThat(agentTypes).isNotNull();

        Map<?, ?> systemHealth = (Map<?, ?>) summary.get("systemHealth");
        Map<?, ?> aiEngine = (Map<?, ?>) systemHealth.get("aiEngine");
        assertThat(aiEngine.get("status")).isEqualTo("UP");

        List<?> activity = (List<?>) summary.get("recentActivity");
        Map<?, ?> row = (Map<?, ?>) activity.get(0);
        assertTrue(row.keySet().containsAll(List.of("id", "endpoint", "success", "traceId", "createdAt")));
        assertFalse(row.containsKey("requestParams"));
        assertFalse(row.containsKey("responseContent"));
    }

    @Test
    void aggregatesTrendsAndAgentTypesAcrossModes() {
        when(agentMetadataRepository.count()).thenReturn(2L);
        when(knowledgeBaseRepository.count()).thenReturn(0L);
        when(workflowDefinitionRepository.count()).thenReturn(0L);
        when(collaborationPackageRepository.count()).thenReturn(0L);
        when(workflowTraceRepository.count()).thenReturn(0L);
        when(taskRepository.countByStatus()).thenReturn(List.of());
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        LocalDateTime now = LocalDateTime.now();
        AuditLog today = AuditLog.builder()
                .id("audit-today")
                .endpoint("/v1/chat")
                .method("POST")
                .success(true)
                .statusCode(200)
                .providerId("OPENAI")
                .totalTokens(150)
                .createdAt(now)
                .build();
        when(auditLogRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(today));
        when(agentMetadataRepository.findAll()).thenReturn(List.of(
                AgentMetadata.builder().agentId("a1").name("a1").mode("agent").syncTime(now).build(),
                AgentMetadata.builder().agentId("a2").name("a2").mode("chat").syncTime(now).build(),
                AgentMetadata.builder().agentId("a3").name("a3").mode("AGENT").syncTime(now).build(),
                AgentMetadata.builder().agentId("a4").name("a4").mode(null).syncTime(now).build()
        ));

        Map<String, Object> summary = service.getSummary(null);

        Map<?, ?> trends = (Map<?, ?>) summary.get("trends");
        List<Map<String, Object>> requestCount = (List<Map<String, Object>>) trends.get("requestCount");
        Map<String, Object> lastPoint = requestCount.get(requestCount.size() - 1);
        assertThat(lastPoint.get("value")).isEqualTo(1L);

        List<Map<String, Object>> tokenUsage = (List<Map<String, Object>>) trends.get("tokenUsage");
        Map<String, Object> lastTokenPoint = tokenUsage.get(tokenUsage.size() - 1);
        assertThat(lastTokenPoint.get("value")).isEqualTo(150L);

        List<Map<String, Object>> agentTypes = (List<Map<String, Object>>) summary.get("agentTypes");
        Map<String, Long> typeCounts = new java.util.HashMap<>();
        for (Map<String, Object> entry : agentTypes) {
            typeCounts.merge((String) entry.get("key"), ((Number) entry.get("count")).longValue(),
                    (a, b) -> a + b);
        }
        assertThat(typeCounts).containsEntry("agent", 2L);
        assertThat(typeCounts).containsEntry("chat", 1L);
        assertThat(typeCounts).containsEntry("other", 1L);
    }

    @Test
    void fallsBackToUserHomeAndDownHealthWhenAiEngineUnavailable() {
        when(taskRepository.countByStatus()).thenReturn(List.of());
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(restTemplate.getForEntity(eq("http://ai-engine.local/health"), eq(Map.class)))
                .thenThrow(new IllegalStateException("offline"));

        Map<String, Object> summary = service.getSummary(null);

        assertThat(summary.get("defaultHome")).isEqualTo("/chat");
        assertTrue(((List<?>) summary.get("roles")).contains("ROLE_USER"));
        Map<?, ?> aiEngine = (Map<?, ?>) ((Map<?, ?>) summary.get("systemHealth")).get("aiEngine");
        assertThat(aiEngine.get("status")).isEqualTo("DOWN");
        assertThat(aiEngine.get("reachable")).isEqualTo(false);
    }

    private AuditLog auditLog() {
        return AuditLog.builder()
                .id("audit-1")
                .endpoint("/v1/chat/completions")
                .method("POST")
                .success(true)
                .statusCode(200)
                .providerType("OPENAI")
                .traceId("trace-1")
                .requestParams("{\"apiKey\":\"secret\"}")
                .responseContent("{\"token\":\"secret\"}")
                .createdAt(LocalDateTime.parse("2026-05-21T10:00:00"))
                .build();
    }
}
