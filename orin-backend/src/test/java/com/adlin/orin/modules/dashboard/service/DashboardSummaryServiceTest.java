package com.adlin.orin.modules.dashboard.service;

import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.repository.GatewaySecretRepository;
import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
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
    private KnowledgeDocumentRepository knowledgeDocumentRepository;
    private WorkflowDefinitionRepository workflowDefinitionRepository;
    private CollaborationPackageRepository collaborationPackageRepository;
    private TaskRepository taskRepository;
    private WorkflowTraceRepository workflowTraceRepository;
    private AuditLogRepository auditLogRepository;
    private SysUserRepository sysUserRepository;
    private GatewaySecretRepository gatewaySecretRepository;
    private RestTemplate restTemplate;
    private DashboardSummaryService service;

    @BeforeEach
    void setUp() {
        agentMetadataRepository = mock(AgentMetadataRepository.class);
        knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        knowledgeDocumentRepository = mock(KnowledgeDocumentRepository.class);
        workflowDefinitionRepository = mock(WorkflowDefinitionRepository.class);
        collaborationPackageRepository = mock(CollaborationPackageRepository.class);
        taskRepository = mock(TaskRepository.class);
        workflowTraceRepository = mock(WorkflowTraceRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        sysUserRepository = mock(SysUserRepository.class);
        gatewaySecretRepository = mock(GatewaySecretRepository.class);
        restTemplate = mock(RestTemplate.class);

        service = new DashboardSummaryService(
                agentMetadataRepository,
                knowledgeBaseRepository,
                knowledgeDocumentRepository,
                workflowDefinitionRepository,
                collaborationPackageRepository,
                taskRepository,
                workflowTraceRepository,
                auditLogRepository,
                sysUserRepository,
                gatewaySecretRepository,
                restTemplate
        );
        ReflectionTestUtils.setField(service, "aiEngineUrl", "http://ai-engine.local");
    }

    @Test
    void buildsAdminSummaryAndSanitizesRecentActivity() {
        when(agentMetadataRepository.count()).thenReturn(3L);
        when(knowledgeBaseRepository.count()).thenReturn(4L);
        when(knowledgeDocumentRepository.findByVectorStatusIn(any())).thenReturn(List.of());
        when(workflowDefinitionRepository.count()).thenReturn(5L);
        when(collaborationPackageRepository.count()).thenReturn(6L);
        when(workflowTraceRepository.count()).thenReturn(7L);
        when(sysUserRepository.count()).thenReturn(12L);
        when(gatewaySecretRepository.countBySecretType(GatewaySecret.SecretType.CLIENT_ACCESS)).thenReturn(9L);
        when(auditLogRepository.countByCreatedAtAfter(any())).thenReturn(42L);
        when(taskRepository.countByStatus()).thenReturn(List.of(
                new Object[]{TaskStatus.QUEUED, 2L},
                new Object[]{TaskStatus.RUNNING, 1L},
                new Object[]{TaskStatus.FAILED, 3L}
        ));
        when(restTemplate.getForEntity(eq("http://ai-engine.local/health"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "ok", "service", "orin-ai-engine")));
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(auditLog())));

        Map<String, Object> summary = service.getSummary(new UsernamePasswordAuthenticationToken(
                "11",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));

        assertThat(summary.get("defaultHome")).isEqualTo("/dashboard/runtime/overview");
        assertTrue(((List<?>) summary.get("roles")).contains("ROLE_ADMIN"));

        Map<?, ?> metrics = (Map<?, ?>) summary.get("metrics");
        assertThat(metrics.get("agents")).isEqualTo(3L);
        assertThat(metrics.get("pendingDocuments")).isEqualTo(0L);
        assertThat(metrics.get("openTasks")).isEqualTo(3L);
        assertThat(metrics.get("failedTasks")).isEqualTo(3L);
        assertThat(metrics.get("scope")).isEqualTo("platform");
        assertThat(metrics.get("totalUsers")).isEqualTo(12L);
        assertThat(metrics.get("apiKeyCount")).isEqualTo(9L);
        assertThat(metrics.get("auditFrequency24h")).isEqualTo(42L);

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
    void fallsBackToUserHomeAndDownHealthWhenAiEngineUnavailable() {
        when(taskRepository.countByStatus()).thenReturn(List.of());
        when(knowledgeDocumentRepository.findByVectorStatusIn(any())).thenReturn(List.of());
        when(restTemplate.getForEntity(eq("http://ai-engine.local/health"), eq(Map.class)))
                .thenThrow(new IllegalStateException("offline"));

        Map<String, Object> summary = service.getSummary(null);

        assertThat(summary.get("defaultHome")).isEqualTo("/portal");
        assertTrue(((List<?>) summary.get("roles")).contains("ROLE_USER"));
        Map<?, ?> aiEngine = (Map<?, ?>) ((Map<?, ?>) summary.get("systemHealth")).get("aiEngine");
        assertThat(aiEngine.get("status")).isEqualTo("DOWN");
        assertThat(aiEngine.get("reachable")).isEqualTo(false);
        assertThat((List<?>) summary.get("recentActivity")).isEmpty();
    }

    @Test
    void routesOperatorToWorkbenchHomeWithOperatorQuickLinks() {
        when(taskRepository.countByStatus()).thenReturn(List.of());
        when(agentMetadataRepository.findByOwnerUserId(22L)).thenReturn(List.of());
        when(knowledgeBaseRepository.findByOwnerUserId(22L)).thenReturn(List.of());
        when(knowledgeDocumentRepository.findByVectorStatusIn(any())).thenReturn(List.of());
        when(gatewaySecretRepository.countBySecretTypeAndUserId(
                GatewaySecret.SecretType.CLIENT_ACCESS, "22")).thenReturn(2L);
        when(restTemplate.getForEntity(eq("http://ai-engine.local/health"), eq(Map.class)))
                .thenThrow(new IllegalStateException("offline"));

        Map<String, Object> summary = service.getSummary(new UsernamePasswordAuthenticationToken(
                "22",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))
        ));

        assertThat(summary.get("defaultHome")).isEqualTo("/dashboard/applications/home");
        Map<?, ?> metrics = (Map<?, ?>) summary.get("metrics");
        assertThat(metrics.get("scope")).isEqualTo("owned");
        assertThat(metrics.get("apiKeys")).isEqualTo(2L);
        assertThat((List<?>) summary.get("recentActivity")).isEmpty();
        List<?> quickLinks = (List<?>) summary.get("quickLinks");
        assertThat(quickLinks).isNotEmpty();
        assertThat(((Map<?, ?>) quickLinks.get(0)).get("path")).isEqualTo("/dashboard/applications/home");
    }

    @Test
    void userSummaryIncludesOwnedApiKeyCountAndPortalLinks() {
        when(taskRepository.countByStatus()).thenReturn(List.of());
        when(agentMetadataRepository.findByOwnerUserId(33L)).thenReturn(List.of());
        when(knowledgeBaseRepository.findByOwnerUserId(33L)).thenReturn(List.of());
        when(knowledgeDocumentRepository.findByVectorStatusIn(any())).thenReturn(List.of());
        when(gatewaySecretRepository.countBySecretTypeAndUserId(
                GatewaySecret.SecretType.CLIENT_ACCESS, "33")).thenReturn(1L);
        when(restTemplate.getForEntity(eq("http://ai-engine.local/health"), eq(Map.class)))
                .thenThrow(new IllegalStateException("offline"));

        Map<String, Object> summary = service.getSummary(new UsernamePasswordAuthenticationToken(
                "33",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));

        assertThat(summary.get("defaultHome")).isEqualTo("/portal");
        Map<?, ?> metrics = (Map<?, ?>) summary.get("metrics");
        assertThat(metrics.get("scope")).isEqualTo("owned");
        assertThat(metrics.get("apiKeys")).isEqualTo(1L);
        List<?> quickLinks = (List<?>) summary.get("quickLinks");
        assertThat(quickLinks.stream().anyMatch(link ->
                "/portal/api-keys".equals(((Map<?, ?>) link).get("path")))).isTrue();
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
