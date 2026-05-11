package com.adlin.orin.modules.agent.service;

import com.adlin.orin.modules.agent.dto.AgentOnboardRequest;
import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.agent.repository.AgentJobRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.service.impl.AgentManageServiceImpl;
import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * E1.1 智能体接入到聊天成功的端到端 smoke 测试
 *
 * 测试目标：验证智能体从接入(onboard)到聊天(chat)的完整路径
 * 测试方式：Mock 外部 provider 调用，不依赖真实 AI 服务
 *
 * 运行方式：mvn test -Dtest=AgentSmokeTest
 */
@ExtendWith(MockitoExtension.class)
class AgentSmokeTest {

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
        // Build service with mocked dependencies
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
                Collections.emptyList(), // providers list
                siliconFlowAgentManageService,
                zhipuAgentManageService,
                kimiAgentManageService,
                deepSeekAgentManageService,
                minimaxAgentManageService,
                agentJobRepository
        );
    }

    @Test
    @DisplayName("E1.1 - Smoke Test: 接入 SiliconFlow 智能体并验证元数据保存成功")
    void testOnboardSiliconFlowAgent_Success() {
        // Given: SiliconFlow 连接测试返回成功
        when(siliconFlowIntegrationService.testConnection(anyString(), anyString())).thenReturn(true);
        when(siliconFlowIntegrationService.resolveSiliconFlowViewType(anyString(), anyString(), anyString()))
                .thenReturn("CHAT");
        when(modelMetadataRepository.findByModelId(anyString())).thenReturn(Optional.empty());
        when(ownershipResolver.resolveFromCurrentRequest()).thenReturn(42L);

        // When: 接入一个 SiliconFlow 智能体
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("https://api.siliconflow.cn/v1");
        request.setApiKey("test-api-key");
        request.setModel("deepseek-ai/DeepSeek-V3");
        request.setProviderType("SiliconFlow");

        AgentMetadata result = agentManageService.onboardAgent(request);

        // Then: 验证返回了正确的元数据
        assertNotNull(result);
        assertNotNull(result.getAgentId());
        assertEquals("SiliconFlow Model", result.getName());
        assertEquals("SiliconFlow", result.getProviderType());
        assertEquals("deepseek-ai/DeepSeek-V3", result.getModelName());

        // Verify: 验证数据保存到了仓库
        verify(accessProfileRepository).save(any(AgentAccessProfile.class));
        verify(metadataRepository).save(argThat(metadata -> Long.valueOf(42L).equals(metadata.getOwnerUserId())));
        verify(healthStatusRepository).save(any());
    }

    @Test
    @DisplayName("MCP 暴露开关: owner 或管理员通过后允许保存")
    void testUpdateMcpExposure_Authorized() {
        AgentMetadata metadata = new AgentMetadata();
        metadata.setAgentId("agent-1");
        metadata.setOwnerUserId(42L);
        when(metadataRepository.findById("agent-1")).thenReturn(Optional.of(metadata));
        when(accessProfileRepository.findById("agent-1")).thenReturn(Optional.empty());

        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setMcpExposed(true);
        agentManageService.updateAgent("agent-1", request);

        verify(ownershipResolver).assertCanManageMcpExposure(metadata);
        verify(metadataRepository).save(argThat(AgentMetadata::isMcpExposed));
    }

    @Test
    @DisplayName("MCP 暴露开关: 非 owner 且非管理员禁止保存")
    void testUpdateMcpExposure_Forbidden() {
        AgentMetadata metadata = new AgentMetadata();
        metadata.setAgentId("agent-1");
        metadata.setOwnerUserId(42L);
        when(metadataRepository.findById("agent-1")).thenReturn(Optional.of(metadata));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "forbidden"))
                .when(ownershipResolver).assertCanManageMcpExposure(metadata);

        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setMcpExposed(true);
        assertThrows(BusinessException.class, () -> agentManageService.updateAgent("agent-1", request));

        verify(metadataRepository, never()).save(any(AgentMetadata.class));
    }

    @Test
    @DisplayName("E1.1 - Smoke Test: SiliconFlow 连接失败时抛出异常")
    void testOnboardSiliconFlowAgent_ConnectionFailed() {
        // Given: SiliconFlow 连接测试返回失败
        when(siliconFlowIntegrationService.testConnection(anyString(), anyString())).thenReturn(false);

        // When & Then: 接入应该抛出异常
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("https://api.siliconflow.cn/v1");
        request.setApiKey("invalid-key");
        request.setModel("deepseek-ai/DeepSeek-V3");
        request.setProviderType("SiliconFlow");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        assertTrue(exception.getMessage().contains("Failed to connect to SiliconFlow"));
    }

    @Test
    @DisplayName("E1.1 - Smoke Test: 接入 Dify 智能体成功")
    void testOnboardDifyAgent_Success() {
        // Given: Dify 连接测试成功，并返回 app meta
        when(difyIntegrationService.testConnection(anyString(), anyString())).thenReturn(true);
        when(difyIntegrationService.fetchAppMeta(anyString(), anyString()))
                .thenReturn(Optional.of(Map.of("tool_name", "My Dify App")));
        when(difyIntegrationService.fetchAppParameters(anyString(), anyString()))
                .thenReturn(Optional.of(Map.of("temperature", 0.7)));

        // When: 接入 Dify 智能体
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("https://dify.example.com/v1");
        request.setApiKey("dify-api-key");
        request.setModel("dify-app");
        request.setProviderType("Dify");

        AgentMetadata result = agentManageService.onboardAgent(request);

        // Then: 验证返回结果
        assertNotNull(result);
        assertEquals("My Dify App", result.getName());
        assertEquals("DIFY", result.getProviderType());
    }

    @Test
    @DisplayName("E1.1 - Smoke Test: 获取已接入智能体列表")
    void testGetAllAgents_ReturnsOnboardedAgents() {
        // Given: 数据库中已有智能体
        AgentMetadata agent1 = new AgentMetadata();
        agent1.setAgentId("agent-001");
        agent1.setName("Test Agent 1");

        AgentMetadata agent2 = new AgentMetadata();
        agent2.setAgentId("agent-002");
        agent2.setName("Test Agent 2");

        when(metadataRepository.findAll()).thenReturn(Arrays.asList(agent1, agent2));

        // When: 获取所有智能体
        List<AgentMetadata> agents = agentManageService.getAllAgents();

        // Then: 返回列表包含所有智能体
        assertEquals(2, agents.size());
        assertEquals("agent-001", agents.get(0).getAgentId());
        assertEquals("agent-002", agents.get(1).getAgentId());
    }

    @Test
    @DisplayName("E1.1 - Smoke Test: 获取不存在的智能体抛出异常")
    void testGetAgentMetadata_NotFound() {
        // Given: 智能体不存在
        when(metadataRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        // When & Then: 应该抛出异常
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.getAgentMetadata("non-existent-id"));

        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("E1.1 - Smoke Test: SiliconFlow Chat 返回模拟响应")
    void testChatWithSiliconFlowAgent_Success() {
        // Given: 预先接入一个 SiliconFlow 智能体
        String agentId = "test-silicon-agent";

        AgentAccessProfile profile = new AgentAccessProfile();
        profile.setAgentId(agentId);
        profile.setEndpointUrl("https://api.siliconflow.cn/v1");
        profile.setApiKey("test-key");
        profile.setConnectionStatus("ACTIVE");

        AgentMetadata metadata = new AgentMetadata();
        metadata.setAgentId(agentId);
        metadata.setName("Test Silicon Agent");
        metadata.setProviderType("SiliconFlow");
        metadata.setModelName("deepseek-ai/DeepSeek-V3");
        metadata.setViewType("CHAT");
        metadata.setSystemPrompt("You are a helpful assistant.");

        when(accessProfileRepository.findById(agentId)).thenReturn(Optional.of(profile));
        when(metadataRepository.findById(agentId)).thenReturn(Optional.of(metadata));
        when(metaKnowledgeService.assembleSystemPrompt(agentId)).thenReturn("");

        // Mock provider chat response for the current SiliconFlow integration path.
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "SUCCESS");
        mockResponse.put("content", "Hello! How can I help you?");
        mockResponse.put("conversation_id", "test-conversation-id");

        when(siliconFlowIntegrationService.sendMessageWithFullParams(
                anyString(), anyString(), anyString(), anyList(), anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenReturn(Optional.of(mockResponse));

        // When: 发送聊天消息
        Optional<Object> result = agentManageService.chat(agentId, "Hi", (String) null);

        // Then: 返回了模拟响应
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) result.get();
        assertNotNull(responseMap.get("conversation_id"));
    }

    @Test
    @DisplayName("E1.1 - Smoke Test: 向不存在的智能体发送消息抛出异常")
    void testChatWithNonExistentAgent_ThrowsException() {
        // Given: 智能体不存在 - 只 stub accessProfileRepository
        when(accessProfileRepository.findById("non-existent")).thenReturn(Optional.empty());

        // When & Then: 向不存在的智能体发消息会抛出 RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.chat("non-existent", "Hi", (String) null));

        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("E1.1 - Smoke Test: 删除智能体")
    void testDeleteAgent_Success() {
        // Given: 智能体存在
        String agentId = "agent-to-delete";

        // When: 删除智能体
        agentManageService.deleteAgent(agentId);

        // Then: 验证删除调用
        verify(accessProfileRepository).deleteById(agentId);
        verify(metadataRepository).deleteById(agentId);
        verify(healthStatusRepository).deleteById(agentId);
    }

    @Test
    @DisplayName("E1.1 - Smoke Test: 更新智能体配置")
    void testUpdateAgent_Success() {
        // Given: 智能体存在
        String agentId = "agent-to-update";
        AgentMetadata existingMetadata = new AgentMetadata();
        existingMetadata.setAgentId(agentId);
        existingMetadata.setName("Old Name");
        existingMetadata.setProviderType("SiliconFlow");
        existingMetadata.setModelName("old-model");

        AgentAccessProfile existingProfile = new AgentAccessProfile();
        existingProfile.setAgentId(agentId);
        existingProfile.setEndpointUrl("https://old-endpoint.com");

        when(metadataRepository.findById(agentId)).thenReturn(Optional.of(existingMetadata));
        when(accessProfileRepository.findById(agentId)).thenReturn(Optional.of(existingProfile));
        when(metadataRepository.save(any())).thenReturn(existingMetadata);

        // When: 更新智能体
        AgentOnboardRequest updateRequest = new AgentOnboardRequest();
        updateRequest.setName("New Name");
        updateRequest.setModel("new-model");

        agentManageService.updateAgent(agentId, updateRequest);

        // Then: 验证更新调用
        verify(metadataRepository).save(any(AgentMetadata.class));
    }

    @Test
    @DisplayName("E1.1 - Smoke Test: 批量导出智能体配置")
    void testBatchExportAgents_Success() {
        // Given: 有一个智能体
        AgentMetadata agent = new AgentMetadata();
        agent.setAgentId("export-agent");
        agent.setName("Export Test Agent");
        agent.setProviderType("SiliconFlow");
        agent.setModelName("test-model");

        AgentAccessProfile profile = new AgentAccessProfile();
        profile.setAgentId("export-agent");
        profile.setEndpointUrl("https://api.test.com");

        when(metadataRepository.findAll()).thenReturn(List.of(agent));
        when(accessProfileRepository.findById("export-agent")).thenReturn(Optional.of(profile));

        // When: 批量导出
        byte[] exported = agentManageService.batchExportAgents(null);

        // Then: 验证导出了 JSON 数据
        assertNotNull(exported);
        String jsonStr = new String(exported);
        assertTrue(jsonStr.contains("export-agent"));
        assertTrue(jsonStr.contains("Export Test Agent"));
        // 验证 API Key 被 mask 了
        assertTrue(jsonStr.contains("***MASKED***"));
    }

    @Test
    @DisplayName("E1.1 - Smoke Test: Provider 类型自动识别 - Ollama")
    void testProviderAutoDetection_Ollama() {
        // Given: URL 包含 ollama 关键字，连接测试返回成功
        when(ollamaIntegrationService.testConnection(anyString(), anyString(), anyString())).thenReturn(true);
        when(modelMetadataRepository.findByModelId(anyString())).thenReturn(Optional.empty());

        // When: 通过 URL 自动识别 provider 接入
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("http://localhost:11434");  // Ollama 默认端口
        request.setApiKey("local-key");
        request.setModel("llama3");

        AgentMetadata result = agentManageService.onboardAgent(request);

        // Then: 自动识别为 Ollama provider
        assertNotNull(result);
        assertEquals("Ollama", result.getProviderType());
        assertTrue(result.getName().contains("Ollama"));
    }
}
