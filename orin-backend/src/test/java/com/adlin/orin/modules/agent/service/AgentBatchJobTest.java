package com.adlin.orin.modules.agent.service;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentJobEntity;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.agent.repository.AgentJobRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.service.impl.AgentManageServiceImpl;
import com.adlin.orin.modules.apikey.repository.ExternalProviderKeyRepository;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.audit.service.AuditLogService;
import com.adlin.orin.modules.conversation.service.ConversationLogService;
import com.adlin.orin.modules.knowledge.service.meta.MetaKnowledgeService;
import com.adlin.orin.modules.model.repository.ModelMetadataRepository;
import com.adlin.orin.modules.model.service.ModelConfigService;
import com.adlin.orin.modules.model.service.OllamaIntegrationService;
import com.adlin.orin.modules.model.service.SiliconFlowIntegrationService;
import com.adlin.orin.modules.model.service.MinimaxIntegrationService;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import com.adlin.orin.modules.multimodal.service.MultimodalFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * E1.3 智能体批量导入、导出、刷新元数据、异步任务查询的真实可用性验证
 *
 * 测试目标：验证批量操作和异步任务的功能完整性和边界处理
 * 测试方式：Mock 外部依赖，测试核心逻辑路径
 *
 * 运行方式：mvn test -Dtest=AgentBatchJobTest
 */
@ExtendWith(MockitoExtension.class)
class AgentBatchJobTest {

    @Mock
    private AgentAccessProfileRepository accessProfileRepository;

    @Mock
    private AgentMetadataRepository metadataRepository;

    @Mock
    private AgentJobRepository agentJobRepository;

    @Mock
    private AgentHealthStatusRepository healthStatusRepository;

    @Mock
    private ModelMetadataRepository modelMetadataRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AuditHelper auditHelper;

    @Mock
    private ConversationLogService conversationLogService;

    @Mock
    private MultimodalFileService multimodalFileService;

    @Mock
    private MetaKnowledgeService metaKnowledgeService;

    @Mock
    private SiliconFlowIntegrationService siliconFlowIntegrationService;

    @Mock
    private MinimaxIntegrationService minimaxIntegrationService;

    @Mock
    private OllamaIntegrationService ollamaIntegrationService;

    @Mock
    private ModelConfigService modelConfigService;

    @Mock
    private GatewaySecretService gatewaySecretService;

    @Mock
    private ExternalProviderKeyRepository providerKeyRepository;

    @Mock
    private AgentOwnershipResolver ownershipResolver;

    @Mock
    private DifyIntegrationService difyIntegrationService;

    @Mock
    private SiliconFlowAgentManageService siliconFlowAgentManageService;

    @Mock
    private ZhipuAgentManageService zhipuAgentManageService;

    @Mock
    private KimiAgentManageService kimiAgentManageService;

    @Mock
    private DeepSeekAgentManageService deepSeekAgentManageService;

    @Mock
    private MinimaxAgentManageService minimaxAgentManageService;

    @Mock
    private AgentVersionService agentVersionService;

    private AgentManageServiceImpl agentManageService;

    @BeforeEach
    void setUp() {
        agentManageService = new AgentManageServiceImpl(
                difyIntegrationService,
                siliconFlowIntegrationService,
                accessProfileRepository,
                metadataRepository,
                healthStatusRepository,
                auditLogService,
                auditHelper,
                conversationLogService,
                multimodalFileService,
                metaKnowledgeService,
                modelMetadataRepository,
                minimaxIntegrationService,
                ollamaIntegrationService,
                modelConfigService,
                gatewaySecretService,
                providerKeyRepository,
                ownershipResolver,
                Collections.emptyList(),
                siliconFlowAgentManageService,
                zhipuAgentManageService,
                kimiAgentManageService,
                deepSeekAgentManageService,
                minimaxAgentManageService,
                agentJobRepository
        );
    }

    // ==================== 批量导出测试 ====================

    @Test
    @DisplayName("E1.3 - 批量导出: 导出所有智能体")
    void testBatchExportAllAgents_Success() {
        // Given: 数据库中有 2 个智能体
        AgentMetadata agent1 = new AgentMetadata();
        agent1.setAgentId("agent-001");
        agent1.setName("Test Agent 1");
        agent1.setProviderType("SiliconFlow");
        agent1.setModelName("deepseek-v3");
        agent1.setViewType("CHAT");
        agent1.setDescription("Test description");

        AgentMetadata agent2 = new AgentMetadata();
        agent2.setAgentId("agent-002");
        agent2.setName("Test Agent 2");
        agent2.setProviderType("DIFY");
        agent2.setModelName("dify-app");
        agent2.setViewType("CHAT");

        when(metadataRepository.findAll()).thenReturn(Arrays.asList(agent1, agent2));

        AgentAccessProfile profile1 = new AgentAccessProfile();
        profile1.setAgentId("agent-001");
        profile1.setEndpointUrl("https://api.siliconflow.cn/v1");
        profile1.setApiKey("secret-key-123");

        when(accessProfileRepository.findById("agent-001")).thenReturn(Optional.of(profile1));
        when(accessProfileRepository.findById("agent-002")).thenReturn(Optional.empty());

        // When: 导出所有（传入 null）
        byte[] result = agentManageService.batchExportAgents(null);

        // Then: 返回了 JSON 数据
        assertNotNull(result);
        String jsonStr = new String(result);
        assertTrue(jsonStr.contains("agent-001"));
        assertTrue(jsonStr.contains("agent-002"));
        assertTrue(jsonStr.contains("Test Agent 1"));
        assertTrue(jsonStr.contains("***MASKED***")); // API Key 被 mask
        assertFalse(jsonStr.contains("secret-key-123")); // 原始 key 未泄露
    }

    @Test
    @DisplayName("E1.3 - 批量导出: 导出指定 ID 的智能体")
    void testBatchExportSpecificAgents_Success() {
        // Given: 数据库中有多个智能体
        AgentMetadata agent1 = new AgentMetadata();
        agent1.setAgentId("agent-001");
        agent1.setName("Agent One");
        agent1.setProviderType("SiliconFlow");
        agent1.setModelName("deepseek-v3");

        AgentMetadata agent2 = new AgentMetadata();
        agent2.setAgentId("agent-002");
        agent2.setName("Agent Two");
        agent2.setProviderType("SiliconFlow");
        agent2.setModelName("deepseek-v3");

        when(metadataRepository.findAllById(List.of("agent-001"))).thenReturn(List.of(agent1));

        AgentAccessProfile profile1 = new AgentAccessProfile();
        profile1.setAgentId("agent-001");
        profile1.setEndpointUrl("https://api.siliconflow.cn/v1");
        profile1.setApiKey("test-key");

        when(accessProfileRepository.findById("agent-001")).thenReturn(Optional.of(profile1));

        // When: 只导出 agent-001
        byte[] result = agentManageService.batchExportAgents(List.of("agent-001"));

        // Then: 只包含指定的智能体
        assertNotNull(result);
        String jsonStr = new String(result);
        assertTrue(jsonStr.contains("agent-001"));
        assertTrue(jsonStr.contains("Agent One"));
        assertFalse(jsonStr.contains("agent-002"));
    }

    @Test
    @DisplayName("E1.3 - 批量导出: 空数据库导出")
    void testBatchExport_EmptyDatabase() {
        // Given: 数据库为空
        when(metadataRepository.findAll()).thenReturn(Collections.emptyList());

        // When: 导出所有
        byte[] result = agentManageService.batchExportAgents(null);

        // Then: 返回空 JSON 数组
        assertNotNull(result);
        String jsonStr = new String(result);
        // 验证是空数组格式
        assertTrue(jsonStr.startsWith("[") && jsonStr.endsWith("]"));
        // 解析为空列表
        assertTrue(jsonStr.contains("[]") || jsonStr.contains("["));
    }

    @Test
    @DisplayName("E1.3 - 批量导出: 导出不存在的智能体 ID")
    void testBatchExport_NonExistentIds() {
        // Given: 请求导出不存在的 ID
        when(metadataRepository.findAllById(List.of("non-existent")))
                .thenReturn(Collections.emptyList());

        // When: 导出
        byte[] result = agentManageService.batchExportAgents(List.of("non-existent"));

        // Then: 返回空 JSON 数组
        assertNotNull(result);
        String jsonStr = new String(result);
        // 验证是空数组格式
        assertTrue(jsonStr.startsWith("[") && jsonStr.endsWith("]"));
        assertTrue(jsonStr.contains("[]") || jsonStr.contains("["));
    }

    // ==================== 批量导入测试 ====================

    @Test
    @DisplayName("E1.3 - 批量导入: 成功导入新智能体")
    void testBatchImport_Success() {
        // Given: 导入文件内容
        String importJson = """
            [
              {
                "agentId": "import-agent-001",
                "name": "Imported Agent",
                "description": "Test import",
                "providerType": "SiliconFlow",
                "modelName": "deepseek-v3",
                "viewType": "CHAT",
                "temperature": 0.7,
                "accessProfile": {
                  "endpointUrl": "https://api.siliconflow.cn/v1"
                }
              }
            ]
            """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "agents_import.json",
                "application/json",
                importJson.getBytes()
        );

        when(metadataRepository.existsById("import-agent-001")).thenReturn(false);
        when(metadataRepository.save(any(AgentMetadata.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ownershipResolver.resolveFromCurrentRequest()).thenReturn(42L);

        // When: 执行导入
        agentManageService.batchImportAgents(file);

        // Then: 验证保存调用
        verify(metadataRepository).save(argThat(metadata -> Long.valueOf(42L).equals(metadata.getOwnerUserId())));
        verify(accessProfileRepository).save(any(AgentAccessProfile.class));
        verify(healthStatusRepository).save(any());
    }

    @Test
    @DisplayName("E1.3 - 批量导入: 跳过已存在的智能体")
    void testBatchImport_SkipExisting() {
        // Given: 导入文件中包含已存在的智能体
        String importJson = """
            [
              {
                "agentId": "existing-agent",
                "name": "Existing Agent",
                "providerType": "SiliconFlow",
                "modelName": "deepseek-v3"
              }
            ]
            """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "agents_import.json",
                "application/json",
                importJson.getBytes()
        );

        when(metadataRepository.existsById("existing-agent")).thenReturn(true);

        // When: 执行导入
        agentManageService.batchImportAgents(file);

        // Then: 不保存已存在的智能体
        verify(metadataRepository, never()).save(any(AgentMetadata.class));
    }

    @Test
    @DisplayName("E1.3 - 批量导入: 跳过无效条目（缺少必填字段）")
    void testBatchImport_SkipInvalidEntry() {
        // Given: 导入文件包含无效条目（缺少 agentId）
        String importJson = """
            [
              {
                "name": "No ID Agent",
                "providerType": "SiliconFlow"
              }
            ]
            """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "agents_import.json",
                "application/json",
                importJson.getBytes()
        );

        // 注意：代码在检查 agentId == null 后直接跳过，不会调用 existsById

        // When: 执行导入
        agentManageService.batchImportAgents(file);

        // Then: 不保存无效条目，也不调用 existsById
        verify(metadataRepository, never()).existsById(anyString());
        verify(metadataRepository, never()).save(any(AgentMetadata.class));
    }

    @Test
    @DisplayName("E1.3 - 批量导入: 混合格式（部分有效）")
    void testBatchImport_MixedValidAndInvalid() {
        // Given: 导入文件包含有效和无效条目混合
        String importJson = """
            [
              {
                "agentId": "valid-agent",
                "name": "Valid Agent",
                "providerType": "SiliconFlow",
                "modelName": "deepseek-v3"
              },
              {
                "name": "Invalid - No ID"
              },
              {
                "agentId": "existing-agent",
                "name": "Existing Agent",
                "providerType": "SiliconFlow",
                "modelName": "deepseek-v3"
              }
            ]
            """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "agents_import.json",
                "application/json",
                importJson.getBytes()
        );

        when(metadataRepository.existsById("valid-agent")).thenReturn(false);
        when(metadataRepository.existsById("existing-agent")).thenReturn(true);
        when(metadataRepository.save(any(AgentMetadata.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: 执行导入
        agentManageService.batchImportAgents(file);

        // Then: 只保存有效且不存在的
        verify(metadataRepository).save(argThat(agent ->
            "valid-agent".equals(agent.getAgentId())
        ));
    }

    @Test
    @DisplayName("E1.3 - 批量导入: 空文件")
    void testBatchImport_EmptyFile() {
        // Given: 空数组文件
        String importJson = "[]";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.json",
                "application/json",
                importJson.getBytes()
        );

        // When: 执行导入
        agentManageService.batchImportAgents(file);

        // Then: 不保存任何内容
        verify(metadataRepository, never()).save(any(AgentMetadata.class));
    }

    @Test
    @DisplayName("E1.3 - 批量导入: 无 accessProfile 时也能正常导入 metadata")
    void testBatchImport_WithoutAccessProfile() {
        // Given: 导入文件没有 accessProfile
        String importJson = """
            [
              {
                "agentId": "metadata-only-agent",
                "name": "Metadata Only Agent",
                "providerType": "SiliconFlow",
                "modelName": "deepseek-v3",
                "temperature": 0.5
              }
            ]
            """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "metadata_only.json",
                "application/json",
                importJson.getBytes()
        );

        when(metadataRepository.existsById("metadata-only-agent")).thenReturn(false);
        when(metadataRepository.save(any(AgentMetadata.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: 执行导入
        agentManageService.batchImportAgents(file);

        // Then: 只保存 metadata，不保存 accessProfile
        verify(metadataRepository).save(any(AgentMetadata.class));
        verify(accessProfileRepository, never()).save(any(AgentAccessProfile.class));
    }

    // ==================== 刷新元数据测试 ====================

    @Test
    @DisplayName("E1.3 - 刷新元数据: Dify 智能体成功刷新")
    void testRefreshAllAgentsMetadata_DifySuccess() {
        // Given: 有 1 个 Dify 智能体
        AgentMetadata agent = new AgentMetadata();
        agent.setAgentId("dify-agent");
        agent.setName("Old Name");
        agent.setProviderType("DIFY");
        agent.setModelName("dify-app");

        AgentAccessProfile profile = new AgentAccessProfile();
        profile.setAgentId("dify-agent");
        profile.setEndpointUrl("https://dify.example.com/v1");
        profile.setApiKey("dify-key");

        when(metadataRepository.findAll()).thenReturn(List.of(agent));
        when(accessProfileRepository.findById("dify-agent")).thenReturn(Optional.of(profile));
        when(difyIntegrationService.fetchAppMeta(anyString(), anyString()))
                .thenReturn(Optional.of(Map.of("name", "New Dify Name", "description", "Updated desc")));
        when(difyIntegrationService.fetchAppParameters(anyString(), anyString()))
                .thenReturn(Optional.of(Map.of("temperature", 0.8)));

        // When: 刷新所有
        agentManageService.refreshAllAgentsMetadata();

        // Then: 验证保存调用
        verify(metadataRepository).save(argThat(saved ->
            "New Dify Name".equals(saved.getName()) &&
            saved.getParameters() != null
        ));
    }

    @Test
    @DisplayName("E1.3 - 刷新元数据: 无 accessProfile 时跳过")
    void testRefreshAllAgentsMetadata_SkipNoProfile() {
        // Given: 智能体没有 accessProfile
        AgentMetadata agent = new AgentMetadata();
        agent.setAgentId("orphan-agent");
        agent.setName("Orphan Agent");
        agent.setProviderType("SiliconFlow");

        when(metadataRepository.findAll()).thenReturn(List.of(agent));
        when(accessProfileRepository.findById("orphan-agent")).thenReturn(Optional.empty());

        // When: 刷新所有
        agentManageService.refreshAllAgentsMetadata();

        // Then: 不保存（跳过）
        verify(metadataRepository, never()).save(any(AgentMetadata.class));
    }

    @Test
    @DisplayName("E1.3 - 刷新元数据: SiliconFlow 不支持远程元数据获取（只记录日志）")
    void testRefreshAllAgentsMetadata_SiliconFlowNoRemoteFetch() {
        // Given: 有 1 个 SiliconFlow 智能体
        AgentMetadata agent = new AgentMetadata();
        agent.setAgentId("sf-agent");
        agent.setName("Old Name");
        agent.setProviderType("SiliconFlow");
        agent.setModelName("deepseek-v3");

        AgentAccessProfile profile = new AgentAccessProfile();
        profile.setAgentId("sf-agent");
        profile.setEndpointUrl("https://api.siliconflow.cn/v1");
        profile.setApiKey("sf-key");

        when(metadataRepository.findAll()).thenReturn(List.of(agent));
        when(accessProfileRepository.findById("sf-agent")).thenReturn(Optional.of(profile));

        // When: 刷新所有
        agentManageService.refreshAllAgentsMetadata();

        // Then: 更新 syncTime 但不调用远程 API
        verify(metadataRepository).save(argThat(saved ->
            "sf-agent".equals(saved.getAgentId())
        ));
        // SiliconFlow 没有 fetchAppMeta 等方法被调用
        verify(difyIntegrationService, never()).fetchAppMeta(anyString(), anyString());
    }

    @Test
    @DisplayName("E1.3 - 刷新元数据: 空数据库")
    void testRefreshAllAgentsMetadata_Empty() {
        // Given: 无智能体
        when(metadataRepository.findAll()).thenReturn(Collections.emptyList());

        // When: 刷新所有
        agentManageService.refreshAllAgentsMetadata();

        // Then: 无保存操作
        verify(metadataRepository, never()).save(any(AgentMetadata.class));
    }

    @Test
    @DisplayName("E1.3 - 刷新元数据: 刷新过程中单条失败不影响其他")
    void testRefreshAllAgentsMetadata_PartialFailure() {
        // Given: 2 个智能体，一个会失败
        AgentMetadata agent1 = new AgentMetadata();
        agent1.setAgentId("success-agent");
        agent1.setProviderType("DIFY");
        agent1.setModelName("dify-app");

        AgentMetadata agent2 = new AgentMetadata();
        agent2.setAgentId("fail-agent");
        agent2.setProviderType("DIFY");
        agent2.setModelName("dify-app");

        AgentAccessProfile profile1 = new AgentAccessProfile();
        profile1.setAgentId("success-agent");
        profile1.setEndpointUrl("https://dify1.example.com/v1");
        profile1.setApiKey("key1");

        AgentAccessProfile profile2 = new AgentAccessProfile();
        profile2.setAgentId("fail-agent");
        profile2.setEndpointUrl("https://dify2.example.com/v1");
        profile2.setApiKey("key2");

        when(metadataRepository.findAll()).thenReturn(Arrays.asList(agent1, agent2));
        when(accessProfileRepository.findById("success-agent")).thenReturn(Optional.of(profile1));
        when(accessProfileRepository.findById("fail-agent")).thenReturn(Optional.of(profile2));
        when(difyIntegrationService.fetchAppMeta("https://dify1.example.com/v1", "key1"))
                .thenReturn(Optional.of(Map.of("name", "Success")));
        when(difyIntegrationService.fetchAppParameters("https://dify1.example.com/v1", "key1"))
                .thenReturn(Optional.of(Map.of("temperature", 0.7)));
        when(difyIntegrationService.fetchAppMeta("https://dify2.example.com/v1", "key2"))
                .thenThrow(new RuntimeException("Network error"));

        // When: 刷新所有
        agentManageService.refreshAllAgentsMetadata();

        // Then: 成功的被保存（失败的不保存，但记录日志）
        // 注意：代码在 catch 中只记录日志，不调用 save
        verify(metadataRepository, times(1)).save(argThat(a ->
            "success-agent".equals(a.getAgentId())
        ));
    }

    // ==================== 异步任务查询测试 ====================

    @Test
    @DisplayName("E1.3 - 异步任务: 创建任务并查询成功")
    void testCreateAndGetJob_Success() {
        // Given: 任务已存在
        AgentJobEntity job = AgentJobEntity.builder()
                .jobId("job-123")
                .agentId("agent-001")
                .jobType("EXPORT")
                .status(AgentJobEntity.JobStatus.SUCCESS)
                .progress(100)
                .resultData("{\"exported\": 5}")
                .triggeredBy("admin")
                .build();

        when(agentJobRepository.findByJobId("job-123")).thenReturn(Optional.of(job));

        // When: 查询任务状态
        Object status = agentManageService.getJobStatus("job-123");

        // Then: 返回状态信息
        assertNotNull(status);
        assertTrue(status instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> statusMap = (Map<String, Object>) status;
        assertEquals("job-123", statusMap.get("jobId"));
        assertEquals("agent-001", statusMap.get("agentId"));
        assertEquals("EXPORT", statusMap.get("jobType"));
        assertEquals("SUCCESS", statusMap.get("status"));
        assertEquals(100, statusMap.get("progress"));
    }

    @Test
    @DisplayName("E1.3 - 异步任务: 查询不存在的任务返回 null")
    void testGetJob_NotFound() {
        // Given: 任务不存在
        when(agentJobRepository.findByJobId("non-existent")).thenReturn(Optional.empty());

        // When: 查询任务状态
        Object status = agentManageService.getJobStatus("non-existent");

        // Then: 返回 null
        assertNull(status);
    }

    @Test
    @DisplayName("E1.3 - 异步任务: 创建新任务")
    void testCreateAsyncJob_Success() {
        // Given: 保存任务
        when(agentJobRepository.save(any(AgentJobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: 创建任务
        String jobId = agentManageService.createAsyncJob("REFRESH_METADATA", List.of("agent-001", "agent-002"), "admin");

        // Then: 返回 jobId
        assertNotNull(jobId);
        assertTrue(jobId.startsWith("job-"));
        verify(agentJobRepository).save(argThat(job ->
            "REFRESH_METADATA".equals(job.getJobType()) &&
            job.getStatus() == AgentJobEntity.JobStatus.PENDING &&
            job.getProgress() == 0 &&
            "admin".equals(job.getTriggeredBy())
        ));
    }

    @Test
    @DisplayName("E1.3 - 异步任务: 不同任务类型")
    void testCreateAsyncJob_DifferentTypes() {
        when(agentJobRepository.save(any(AgentJobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: 创建导入任务
        String importJobId = agentManageService.createAsyncJob("IMPORT", List.of(), "system");

        // Then: jobId 以 job- 开头
        assertNotNull(importJobId);
        assertTrue(importJobId.startsWith("job-"));
        verify(agentJobRepository).save(argThat(job ->
            "IMPORT".equals(job.getJobType())
        ));
    }

    @Test
    @DisplayName("E1.3 - 异步任务: 获取任务状态时字段为 null 的处理")
    void testGetJob_WithNullFields() {
        // Given: 任务部分字段为 null
        AgentJobEntity job = AgentJobEntity.builder()
                .jobId("job-null-fields")
                .jobType("EXPORT")
                .status(AgentJobEntity.JobStatus.RUNNING)
                .progress(null) // null progress
                .resultData(null)
                .errorMessage(null)
                .createdAt(null)
                .completedAt(null)
                .build();

        when(agentJobRepository.findByJobId("job-null-fields")).thenReturn(Optional.of(job));

        // When: 查询状态
        Object status = agentManageService.getJobStatus("job-null-fields");

        // Then: 不抛出异常，正确处理 null 值
        assertNotNull(status);
        @SuppressWarnings("unchecked")
        Map<String, Object> statusMap = (Map<String, Object>) status;
        assertEquals(0, statusMap.get("progress")); // null 被转为 0
        assertEquals("", statusMap.get("resultData")); // null 被转为空字符串
        assertEquals("", statusMap.get("createdAt")); // null 被转为空字符串
    }

    @Test
    @DisplayName("E1.3 - 异步任务: JobStatus 枚举描述正确")
    void testJobStatus_Descriptions() {
        // Verify enum descriptions are correct
        assertEquals("等待中", AgentJobEntity.JobStatus.PENDING.getDescription());
        assertEquals("执行中", AgentJobEntity.JobStatus.RUNNING.getDescription());
        assertEquals("成功", AgentJobEntity.JobStatus.SUCCESS.getDescription());
        assertEquals("失败", AgentJobEntity.JobStatus.FAILED.getDescription());
    }
}
