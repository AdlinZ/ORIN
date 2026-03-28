package com.adlin.orin.modules.agent.service;

import com.adlin.orin.modules.agent.dto.AgentOnboardRequest;
import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.agent.repository.AgentJobRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.service.impl.AgentManageServiceImpl;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * E1.2 智能体接入失败、鉴权失败、Provider 不可用三类失败用例
 *
 * 测试目标：验证智能体在各种异常场景下的错误处理和降级行为
 * 测试方式：Mock 外部 provider 调用，模拟不同类型的失败
 *
 * 运行方式：mvn test -Dtest=AgentFailureTest
 */
@ExtendWith(MockitoExtension.class)
class AgentFailureTest {

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
                Collections.emptyList(), // providers list
                siliconFlowAgentManageService,
                zhipuAgentManageService,
                kimiAgentManageService,
                deepSeekAgentManageService,
                minimaxAgentManageService,
                agentJobRepository
        );
    }

    // ==================== 1. 接入失败场景 (Access/Connection Failure) ====================

    @Test
    @DisplayName("E1.2 - 接入失败: SiliconFlow 连接超时")
    void testOnboardSiliconFlow_ConnectionTimeout() {
        // Given: SiliconFlow 连接超时（抛出异常）
        when(siliconFlowIntegrationService.testConnection(anyString(), anyString()))
                .thenThrow(new RuntimeException("Connection timed out: connect timed out"));

        // When & Then: 接入应该抛出 RuntimeException
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("https://api.siliconflow.cn/v1");
        request.setApiKey("test-api-key");
        request.setModel("deepseek-ai/DeepSeek-V3");
        request.setProviderType("SiliconFlow");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        assertTrue(exception.getMessage().contains("Failed to connect to SiliconFlow") ||
                exception.getMessage().contains("timed out") ||
                exception.getMessage().contains("Connection"));
    }

    @Test
    @DisplayName("E1.2 - 接入失败: SiliconFlow 服务不可用 (503)")
    void testOnboardSiliconFlow_ServiceUnavailable() {
        // Given: SiliconFlow 连接测试返回 false（可能是 503/500 等错误）
        when(siliconFlowIntegrationService.testConnection(anyString(), anyString()))
                .thenReturn(false);

        // When & Then: 接入应该抛出 RuntimeException
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("https://api.siliconflow.cn/v1");
        request.setApiKey("test-api-key");
        request.setModel("deepseek-ai/DeepSeek-V3");
        request.setProviderType("SiliconFlow");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        assertTrue(exception.getMessage().contains("Failed to connect to SiliconFlow"));
    }

    @Test
    @DisplayName("E1.2 - 接入失败: Dify 连接失败")
    void testOnboardDify_ConnectionFailed() {
        // Given: Dify 连接测试返回 false
        when(difyIntegrationService.testConnection(anyString(), anyString())).thenReturn(false);

        // When & Then: 接入应该抛出 RuntimeException
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("https://dify.example.com/v1");
        request.setApiKey("dify-api-key");
        request.setModel("dify-app");
        request.setProviderType("Dify");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        assertTrue(exception.getMessage().contains("Failed to connect to Dify"));
    }

    @Test
    @DisplayName("E1.2 - 接入失败: Ollama 服务未运行")
    void testOnboardOllama_NotRunning() {
        // Given: Ollama 连接测试失败（服务未运行）
        when(ollamaIntegrationService.testConnection(anyString(), anyString(), anyString()))
                .thenReturn(false);

        // When & Then: 接入应该抛出 RuntimeException
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("http://localhost:11434");
        request.setApiKey("local-key");
        request.setModel("llama3");
        request.setProviderType("Ollama");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        assertTrue(exception.getMessage().contains("Failed to connect to Ollama") ||
                exception.getMessage().contains("make sure Ollama is running"));
    }

    @Test
    @DisplayName("E1.2 - 接入失败: MiniMax 连接失败")
    void testOnboardMinimax_ConnectionFailed() {
        // Given: MiniMax 连接测试返回 false
        when(minimaxIntegrationService.testConnection(anyString(), anyString(), anyString()))
                .thenReturn(false);

        // When & Then: 接入应该抛出 RuntimeException
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("https://api.minimax.chat/v1");
        request.setApiKey("minimax-api-key");
        request.setModel("abab6.5g-chat");
        request.setProviderType("MiniMax");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        assertTrue(exception.getMessage().contains("Failed to connect to MiniMax"));
    }

    @Test
    @DisplayName("E1.2 - 接入失败: 不支持的 Provider 类型")
    void testOnboard_UnsupportedProvider() {
        // Given: 无法识别的 provider 类型（代码会 fallback 到 DIFY 并尝试连接）
        when(difyIntegrationService.testConnection(anyString(), anyString())).thenReturn(false);

        // When & Then: 接入应该抛出 RuntimeException
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("https://unknown-provider.com/v1");
        request.setApiKey("test-key");
        request.setModel("test-model");
        request.setProviderType("UnknownProvider");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        // 代码实际行为：UnknownProvider 会 fallback 到 DIFY，尝试连接后抛出 "Failed to connect to Dify agent"
        assertTrue(exception.getMessage().contains("Failed to connect to Dify") ||
                exception.getMessage().contains("Unsupported provider") ||
                exception.getMessage().contains("unable to identify"));
    }

    @Test
    @DisplayName("E1.2 - 接入失败: 网络不可达")
    void testOnboard_NetworkUnreachable() {
        // Given: 网络不可达（抛出 ConnectException）
        when(siliconFlowIntegrationService.testConnection(anyString(), anyString()))
                .thenThrow(new RuntimeException("Network unreachable"));

        // When & Then: 接入应该抛出 RuntimeException
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("https://api.siliconflow.cn/v1");
        request.setApiKey("test-api-key");
        request.setModel("deepseek-ai/DeepSeek-V3");
        request.setProviderType("SiliconFlow");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        assertNotNull(exception.getMessage());
    }

    // ==================== 2. 鉴权失败场景 (Authentication Failure) ====================

    @Test
    @DisplayName("E1.2 - 鉴权失败: SiliconFlow API Key 无效 (401)")
    void testOnboardSiliconFlow_InvalidApiKey() {
        // Given: SiliconFlow 连接测试返回 false（认证失败时 testConnection 返回 false）
        when(siliconFlowIntegrationService.testConnection(anyString(), anyString()))
                .thenReturn(false);

        // When & Then: 接入应该抛出 RuntimeException
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("https://api.siliconflow.cn/v1");
        request.setApiKey("invalid-key-12345");
        request.setModel("deepseek-ai/DeepSeek-V3");
        request.setProviderType("SiliconFlow");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        assertTrue(exception.getMessage().contains("Failed to connect to SiliconFlow"));
    }

    @Test
    @DisplayName("E1.2 - 鉴权失败: Dify API Key 格式错误")
    void testOnboardDify_InvalidApiKeyFormat() {
        // Given: Dify 连接失败（无效的 API Key）
        when(difyIntegrationService.testConnection(anyString(), anyString())).thenReturn(false);

        // When & Then: 接入应该抛出 RuntimeException
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("https://dify.example.com/v1");
        request.setApiKey("app-invalid-key-format");
        request.setModel("dify-app");
        request.setProviderType("Dify");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        assertTrue(exception.getMessage().contains("Failed to connect to Dify"));
    }

    @Test
    @DisplayName("E1.2 - 鉴权失败: MiniMax API Key 过期 (403)")
    void testOnboardMinimax_ExpiredApiKey() {
        // Given: MiniMax 连接测试返回 false（可能是 403/401 等错误）
        when(minimaxIntegrationService.testConnection(anyString(), anyString(), anyString()))
                .thenReturn(false);

        // When & Then: 接入应该抛出 RuntimeException
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("https://api.minimax.chat/v1");
        request.setApiKey("expired-minimax-key");
        request.setModel("abab6.5g-chat");
        request.setProviderType("MiniMax");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        assertTrue(exception.getMessage().contains("Failed to connect to MiniMax"));
    }

    @Test
    @DisplayName("E1.2 - 鉴权失败: Ollama API Key 错误")
    void testOnboardOllama_InvalidApiKey() {
        // Given: Ollama 连接测试失败（错误的 API Key）
        when(ollamaIntegrationService.testConnection(anyString(), anyString(), anyString()))
                .thenReturn(false);

        // When & Then: 接入应该抛出 RuntimeException
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("http://localhost:11434");
        request.setApiKey("wrong-key");
        request.setModel("llama3");
        request.setProviderType("Ollama");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        assertTrue(exception.getMessage().contains("Failed to connect to Ollama"));
    }

    @Test
    @DisplayName("E1.2 - 鉴权失败: SiliconFlow API Key 没有权限访问该模型")
    void testOnboardSiliconFlow_NoModelPermission() {
        // Given: API Key 存在但没有访问特定模型的权限（testConnection 返回 false）
        when(siliconFlowIntegrationService.testConnection(anyString(), anyString()))
                .thenReturn(false);

        // When & Then: 接入应该抛出 RuntimeException
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("https://api.siliconflow.cn/v1");
        request.setApiKey("valid-but-no-permission-key");
        request.setModel("restricted-model");
        request.setProviderType("SiliconFlow");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        assertTrue(exception.getMessage().contains("Failed to connect to SiliconFlow"));
    }

    // ==================== 3. Provider 不可用场景 (Provider Unavailable) ====================

    @Test
    @DisplayName("E1.2 - Provider 不可用: SiliconFlow 限流 (429)")
    void testChatSiliconFlow_RateLimited() {
        // Given: 已接入的 SiliconFlow 智能体
        String agentId = "rate-limited-agent";

        AgentAccessProfile profile = new AgentAccessProfile();
        profile.setAgentId(agentId);
        profile.setEndpointUrl("https://api.siliconflow.cn/v1");
        profile.setApiKey("test-key");
        profile.setConnectionStatus("ACTIVE");

        AgentMetadata metadata = new AgentMetadata();
        metadata.setAgentId(agentId);
        metadata.setName("Rate Limited Agent");
        metadata.setProviderType("SiliconFlow");
        metadata.setModelName("deepseek-ai/DeepSeek-V3");
        metadata.setViewType("CHAT");

        when(accessProfileRepository.findById(agentId)).thenReturn(Optional.of(profile));
        when(metadataRepository.findById(agentId)).thenReturn(Optional.of(metadata));
        when(metaKnowledgeService.assembleSystemPrompt(agentId)).thenReturn("");

        // Given: Chat 调用触发限流
        when(siliconFlowAgentManageService.chat(eq(agentId), eq("Hi"), (String) isNull()))
                .thenThrow(new HttpClientErrorException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Rate limit exceeded",
                        null,
                        "{\"error\":{\"message\":\"Rate limit exceeded. Please retry after 1 minute.\"}}".getBytes(),
                        null));

        // When: 发送聊天消息
        Optional<Object> result = agentManageService.chat(agentId, "Hi", (String) null);

        // Then: 返回了错误信息
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) result.get();
        assertTrue(responseMap.containsKey("status") && "ERROR".equals(responseMap.get("status")) ||
                responseMap.containsKey("error"));
    }

    @Test
    @DisplayName("E1.2 - Provider 不可用: SiliconFlow 内部错误 (500)")
    void testChatSiliconFlow_InternalServerError() {
        // Given: 已接入的 SiliconFlow 智能体
        String agentId = "server-error-agent";

        AgentAccessProfile profile = new AgentAccessProfile();
        profile.setAgentId(agentId);
        profile.setEndpointUrl("https://api.siliconflow.cn/v1");
        profile.setApiKey("test-key");
        profile.setConnectionStatus("ACTIVE");

        AgentMetadata metadata = new AgentMetadata();
        metadata.setAgentId(agentId);
        metadata.setName("Server Error Agent");
        metadata.setProviderType("SiliconFlow");
        metadata.setModelName("deepseek-ai/DeepSeek-V3");
        metadata.setViewType("CHAT");

        when(accessProfileRepository.findById(agentId)).thenReturn(Optional.of(profile));
        when(metadataRepository.findById(agentId)).thenReturn(Optional.of(metadata));
        when(metaKnowledgeService.assembleSystemPrompt(agentId)).thenReturn("");

        // Given: Chat 调用触发 500 错误
        when(siliconFlowAgentManageService.chat(eq(agentId), eq("Hi"), (String) isNull()))
                .thenThrow(new HttpServerErrorException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error",
                        null,
                        "{\"error\":{\"message\":\"Internal server error\"}}".getBytes(),
                        null));

        // When: 发送聊天消息
        Optional<Object> result = agentManageService.chat(agentId, "Hi", (String) null);

        // Then: 返回了错误信息
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) result.get();
        assertTrue(responseMap.containsKey("status") && "ERROR".equals(responseMap.get("status")) ||
                responseMap.containsKey("error"));
    }

    @Test
    @DisplayName("E1.2 - Provider 不可用: SiliconFlow 熔断器打开")
    void testChatSiliconFlow_CircuitBreakerOpen() {
        // Given: 已接入的 SiliconFlow 智能体
        String agentId = "circuit-breaker-agent";

        AgentAccessProfile profile = new AgentAccessProfile();
        profile.setAgentId(agentId);
        profile.setEndpointUrl("https://api.siliconflow.cn/v1");
        profile.setApiKey("test-key");
        profile.setConnectionStatus("ACTIVE");

        AgentMetadata metadata = new AgentMetadata();
        metadata.setAgentId(agentId);
        metadata.setName("Circuit Breaker Agent");
        metadata.setProviderType("SiliconFlow");
        metadata.setModelName("deepseek-ai/DeepSeek-V3");
        metadata.setViewType("CHAT");

        when(accessProfileRepository.findById(agentId)).thenReturn(Optional.of(profile));
        when(metadataRepository.findById(agentId)).thenReturn(Optional.of(metadata));
        when(metaKnowledgeService.assembleSystemPrompt(agentId)).thenReturn("");

        // Given: Chat 调用被熔断器拒绝
        when(siliconFlowAgentManageService.chat(eq(agentId), eq("Hi"), (String) isNull()))
                .thenThrow(new RuntimeException("CircuitBreaker is OPEN - SiliconFlow API is temporarily unavailable"));

        // When: 发送聊天消息
        Optional<Object> result = agentManageService.chat(agentId, "Hi", (String) null);

        // Then: 返回了错误信息
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) result.get();
        assertTrue(responseMap.containsKey("status") && "ERROR".equals(responseMap.get("status")) ||
                responseMap.containsKey("error"));
    }

    @Test
    @DisplayName("E1.2 - Provider 不可用: Dify 服务维护中 (503)")
    void testChatDify_ServiceMaintenance() {
        // Given: 已接入的 Dify 智能体
        String agentId = "maintenance-agent";

        AgentAccessProfile profile = new AgentAccessProfile();
        profile.setAgentId(agentId);
        profile.setEndpointUrl("https://dify.example.com/v1");
        profile.setApiKey("test-key");
        profile.setConnectionStatus("ACTIVE");

        AgentMetadata metadata = new AgentMetadata();
        metadata.setAgentId(agentId);
        metadata.setName("Maintenance Agent");
        metadata.setProviderType("DIFY");
        metadata.setModelName("dify-app");
        metadata.setViewType("CHAT");

        when(accessProfileRepository.findById(agentId)).thenReturn(Optional.of(profile));
        when(metadataRepository.findById(agentId)).thenReturn(Optional.of(metadata));
        when(metaKnowledgeService.assembleSystemPrompt(agentId)).thenReturn("");

        // Given: Dify 返回 503
        when(difyIntegrationService.sendMessage(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new HttpServerErrorException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Service Unavailable",
                        null,
                        "{\"message\":\"Service is under maintenance\"}".getBytes(),
                        null));

        // When: 发送聊天消息
        Optional<Object> result = agentManageService.chat(agentId, "Hi", (String) null);

        // Then: 返回了错误信息
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) result.get();
        assertTrue(responseMap.containsKey("status") && "ERROR".equals(responseMap.get("status")) ||
                responseMap.containsKey("error"));
    }

    @Test
    @DisplayName("E1.2 - Provider 不可用: Ollama 模型不存在")
    void testChatOllama_ModelNotFound() {
        // Given: 已接入的 Ollama 智能体
        String agentId = "ollama-model-agent";

        AgentAccessProfile profile = new AgentAccessProfile();
        profile.setAgentId(agentId);
        profile.setEndpointUrl("http://localhost:11434");
        profile.setApiKey("");
        profile.setConnectionStatus("ACTIVE");

        AgentMetadata metadata = new AgentMetadata();
        metadata.setAgentId(agentId);
        metadata.setName("Ollama Model Agent");
        metadata.setProviderType("Ollama");
        metadata.setModelName("non-existent-model");
        metadata.setViewType("CHAT");

        when(accessProfileRepository.findById(agentId)).thenReturn(Optional.of(profile));
        when(metadataRepository.findById(agentId)).thenReturn(Optional.of(metadata));
        when(metaKnowledgeService.assembleSystemPrompt(agentId)).thenReturn("");

        // Given: Ollama 返回模型不存在
        when(ollamaIntegrationService.sendMessageWithFullParams(
                anyString(), anyString(), anyString(), anyList(), anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new RuntimeException("model not found: model 'non-existent-model' not found"));

        // When: 发送聊天消息
        Optional<Object> result = agentManageService.chat(agentId, "Hi", (String) null);

        // Then: 返回了错误信息
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) result.get();
        assertTrue(responseMap.containsKey("status") && "ERROR".equals(responseMap.get("status")) ||
                responseMap.containsKey("error"));
    }

    @Test
    @DisplayName("E1.2 - Provider 不可用: Chat 返回空响应 (无响应降级)")
    void testChat_EmptyResponse_GracefulDegradation() {
        // Given: 已接入的 SiliconFlow 智能体
        String agentId = "empty-response-agent";

        AgentAccessProfile profile = new AgentAccessProfile();
        profile.setAgentId(agentId);
        profile.setEndpointUrl("https://api.siliconflow.cn/v1");
        profile.setApiKey("test-key");
        profile.setConnectionStatus("ACTIVE");

        AgentMetadata metadata = new AgentMetadata();
        metadata.setAgentId(agentId);
        metadata.setName("Empty Response Agent");
        metadata.setProviderType("SiliconFlow");
        metadata.setModelName("deepseek-ai/DeepSeek-V3");
        metadata.setViewType("CHAT");

        when(accessProfileRepository.findById(agentId)).thenReturn(Optional.of(profile));
        when(metadataRepository.findById(agentId)).thenReturn(Optional.of(metadata));
        when(metaKnowledgeService.assembleSystemPrompt(agentId)).thenReturn("");

        // Given: Chat 返回空 Optional
        when(siliconFlowAgentManageService.chat(eq(agentId), eq("Hi"), (String) isNull()))
                .thenReturn(Optional.empty());

        // When: 发送聊天消息
        Optional<Object> result = agentManageService.chat(agentId, "Hi", (String) null);

        // Then: 返回错误响应（不是成功）
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) result.get();
        assertTrue(responseMap.containsKey("status") && "ERROR".equals(responseMap.get("status")) ||
                responseMap.containsKey("error"));
    }

    // ==================== 4. 边界场景 (Boundary Cases) ====================

    @Test
    @DisplayName("E1.2 - 边界场景: 接入时 API Key 为空")
    void testOnboard_EmptyApiKey() {
        // Given: 空 API Key（SiliconFlow testConnection 返回 false 表示连接失败）
        when(siliconFlowIntegrationService.testConnection(anyString(), anyString()))
                .thenReturn(false);

        // When & Then: 接入应该抛出异常
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("https://api.siliconflow.cn/v1");
        request.setApiKey("");
        request.setModel("deepseek-ai/DeepSeek-V3");
        request.setProviderType("SiliconFlow");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        // 预期抛出连接失败异常
        assertTrue(exception.getMessage().contains("Failed to connect to SiliconFlow"));
    }

    @Test
    @DisplayName("E1.2 - 边界场景: 接入时 Endpoint URL 格式错误")
    void testOnboard_InvalidEndpointUrl() {
        // Given: 无效的 URL 格式
        when(siliconFlowIntegrationService.testConnection(anyString(), anyString()))
                .thenThrow(new RuntimeException("Invalid URL format"));

        // When & Then: 接入应该抛出 RuntimeException
        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setEndpointUrl("not-a-valid-url");
        request.setApiKey("test-key");
        request.setModel("deepseek-ai/DeepSeek-V3");
        request.setProviderType("SiliconFlow");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.onboardAgent(request));

        assertNotNull(exception.getMessage());
    }

    @Test
    @DisplayName("E1.2 - 边界场景: 智能体已删除但仍尝试聊天")
    void testChat_AgentAlreadyDeleted() {
        // Given: 智能体已被删除（profile 不存在）
        String agentId = "deleted-agent";
        when(accessProfileRepository.findById(agentId)).thenReturn(Optional.empty());

        // When & Then: 聊天应该抛出 RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.chat(agentId, "Hi", (String) null));

        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("E1.2 - 边界场景: 智能体 metadata 已删除但 profile 存在")
    void testChat_MetadataMissing() {
        // Given: 智能体 metadata 已被删除
        String agentId = "metadata-missing-agent";

        AgentAccessProfile profile = new AgentAccessProfile();
        profile.setAgentId(agentId);
        profile.setEndpointUrl("https://api.siliconflow.cn/v1");
        profile.setApiKey("test-key");
        profile.setConnectionStatus("ACTIVE");

        when(accessProfileRepository.findById(agentId)).thenReturn(Optional.of(profile));
        when(metadataRepository.findById(agentId)).thenReturn(Optional.empty());

        // When & Then: 聊天应该抛出 RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> agentManageService.chat(agentId, "Hi", (String) null));

        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("E1.2 - 边界场景: Chat 超时处理")
    void testChat_Timeout() {
        // Given: 已接入的 SiliconFlow 智能体
        String agentId = "timeout-agent";

        AgentAccessProfile profile = new AgentAccessProfile();
        profile.setAgentId(agentId);
        profile.setEndpointUrl("https://api.siliconflow.cn/v1");
        profile.setApiKey("test-key");
        profile.setConnectionStatus("ACTIVE");

        AgentMetadata metadata = new AgentMetadata();
        metadata.setAgentId(agentId);
        metadata.setName("Timeout Agent");
        metadata.setProviderType("SiliconFlow");
        metadata.setModelName("deepseek-ai/DeepSeek-V3");
        metadata.setViewType("CHAT");

        when(accessProfileRepository.findById(agentId)).thenReturn(Optional.of(profile));
        when(metadataRepository.findById(agentId)).thenReturn(Optional.of(metadata));
        when(metaKnowledgeService.assembleSystemPrompt(agentId)).thenReturn("");

        // Given: Chat 调用超时
        when(siliconFlowAgentManageService.chat(eq(agentId), eq("Hi"), (String) isNull()))
                .thenThrow(new RuntimeException("Read timed out after 60000 ms"));

        // When: 发送聊天消息
        Optional<Object> result = agentManageService.chat(agentId, "Hi", (String) null);

        // Then: 返回了错误信息
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) result.get();
        assertTrue(responseMap.containsKey("status") && "ERROR".equals(responseMap.get("status")) ||
                responseMap.containsKey("error"));
    }
}