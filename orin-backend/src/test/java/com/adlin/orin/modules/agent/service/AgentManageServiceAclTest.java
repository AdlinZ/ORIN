package com.adlin.orin.modules.agent.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ResourceNotFoundException;
import com.adlin.orin.modules.agent.dto.AgentOnboardRequest;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.agent.repository.AgentJobRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import com.adlin.orin.modules.agent.service.impl.AgentManageServiceImpl;
import com.adlin.orin.modules.agent.service.provider.MultiModalProvider;
import com.adlin.orin.modules.apikey.repository.ExternalProviderKeyRepository;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.audit.service.AuditLogService;
import com.adlin.orin.modules.conversation.service.ConversationLogService;
import com.adlin.orin.modules.knowledge.service.meta.MetaKnowledgeService;
import com.adlin.orin.modules.model.repository.ModelMetadataRepository;
import com.adlin.orin.modules.model.service.MinimaxIntegrationService;
import com.adlin.orin.modules.model.service.ModelConfigService;
import com.adlin.orin.modules.model.service.OllamaIntegrationService;
import com.adlin.orin.modules.model.service.SiliconFlowIntegrationService;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import com.adlin.orin.modules.multimodal.service.MultimodalFileService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 资源级 ACL 第 2 刀 (Agent) 行为测试。
 *
 * 覆盖:
 * - getAllAgents: admin / operator / 普通用户 读路径
 * - updateAgent / updateAgentConfig: 非 owner 抛 FORBIDDEN
 * - deleteAgent: 非 owner 抛 FORBIDDEN, 缺 agent 抛 RESOURCE_NOT_FOUND
 * - chat: 非 owner 抛 FORBIDDEN
 *
 * onboardAgent 不在本测试范围(已有 setOwnerUserId 逻辑,见 L297)。
 *
 * 运行方式: mvn test -Dtest=AgentManageServiceAclTest
 */
class AgentManageServiceAclTest {

    private static final Long USER_ID = 100L;
    private static final Long OTHER_USER_ID = 200L;

    // 仅 mock 真正会被调用的依赖
    private final AgentMetadataRepository metadataRepository = mock(AgentMetadataRepository.class);
    private final AgentAccessProfileRepository accessProfileRepository = mock(AgentAccessProfileRepository.class);
    private final AgentHealthStatusRepository healthStatusRepository = mock(AgentHealthStatusRepository.class);
    private final AgentOwnershipResolver ownershipResolver = mock(AgentOwnershipResolver.class);
    private final MetaKnowledgeService metaKnowledgeService = mock(MetaKnowledgeService.class);

    private AgentManageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentManageServiceImpl(
                mock(DifyIntegrationService.class),
                mock(SiliconFlowIntegrationService.class),
                accessProfileRepository,
                metadataRepository,
                healthStatusRepository,
                mock(AuditLogService.class),
                mock(AuditHelper.class),
                mock(ConversationLogService.class),
                mock(MultimodalFileService.class),
                metaKnowledgeService,
                mock(ModelMetadataRepository.class),
                mock(MinimaxIntegrationService.class),
                mock(OllamaIntegrationService.class),
                mock(ModelConfigService.class),
                mock(GatewaySecretService.class),
                mock(ExternalProviderKeyRepository.class),
                ownershipResolver,
                Collections.<MultiModalProvider>emptyList(),
                mock(SiliconFlowAgentManageService.class),
                mock(ZhipuAgentManageService.class),
                mock(KimiAgentManageService.class),
                mock(DeepSeekAgentManageService.class),
                mock(MinimaxAgentManageService.class),
                mock(AgentJobRepository.class)
        );
    }

    // ============================================================
    // getAllAgents 读路径
    // ============================================================

    @Test
    @DisplayName("ACL-read: 普通用户 getAll 仅按 owner 过滤")
    void getAll_regularUser_filtersByOwner() {
        when(ownershipResolver.isCurrentUserPrivileged()).thenReturn(false);
        when(ownershipResolver.resolveFromCurrentRequest()).thenReturn(USER_ID);
        when(metadataRepository.findByOwnerUserId(USER_ID))
                .thenReturn(List.of(metadata("agent-1", USER_ID)));

        List<AgentMetadata> result = service.getAllAgents();

        assertEquals(1, result.size());
        verify(metadataRepository).findByOwnerUserId(USER_ID);
        verify(metadataRepository, never()).findAll();
    }

    @Test
    @DisplayName("ACL-read: 管理员 getAll 走 findAll,不受 owner 限制")
    void getAll_admin_seesAll() {
        when(ownershipResolver.isCurrentUserPrivileged()).thenReturn(true);
        when(metadataRepository.findAll()).thenReturn(List.of(
                metadata("agent-1", USER_ID),
                metadata("agent-2", OTHER_USER_ID)));

        List<AgentMetadata> result = service.getAllAgents();

        assertEquals(2, result.size());
        verify(metadataRepository).findAll();
        verify(metadataRepository, never()).findByOwnerUserId(any());
    }

    @Test
    @DisplayName("ACL-read: 运维 getAll 走 findAll,豁免 owner 限制(与 KB 第 1 刀口径一致)")
    void getAll_operator_seesAll() {
        when(ownershipResolver.isCurrentUserPrivileged()).thenReturn(true);
        when(metadataRepository.findAll()).thenReturn(List.of(
                metadata("agent-1", USER_ID),
                metadata("agent-2", OTHER_USER_ID)));

        List<AgentMetadata> result = service.getAllAgents();

        assertEquals(2, result.size());
        verify(metadataRepository).findAll();
    }

    // ============================================================
    // updateAgent / updateAgentConfig 写路径
    // ============================================================

    @Test
    @DisplayName("ACL-update: 非 owner 调用 updateAgent 抛 FORBIDDEN,不进入字段更新")
    void update_nonOwner_throwsForbidden() {
        AgentMetadata existing = metadata("agent-1", OTHER_USER_ID);
        when(metadataRepository.findById("agent-1")).thenReturn(Optional.of(existing));
        doThrow(new BusinessException(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, "无权操作该智能体"))
                .when(ownershipResolver).assertCanManage(existing);

        AgentOnboardRequest request = new AgentOnboardRequest();
        request.setName("hacked");

        // updateAgent 内部用 ifPresent, 非 owner 抛异常且不保存
        assertThrows(BusinessException.class, () -> service.updateAgent("agent-1", request));

        verify(ownershipResolver).assertCanManage(existing);
        verify(metadataRepository, never()).save(any());
    }

    @Test
    @DisplayName("ACL-updateConfig: 非 owner 调用 updateAgentConfig 返回空 Optional,不进入更新")
    void updateConfig_nonOwner_throwsForbidden() {
        AgentMetadata existing = metadata("agent-1", OTHER_USER_ID);
        when(metadataRepository.findById("agent-1")).thenReturn(Optional.of(existing));
        doThrow(new BusinessException(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, "无权操作该智能体"))
                .when(ownershipResolver).assertCanManage(existing);

        AgentMetadata config = AgentMetadata.builder().name("hacked").build();

        assertThrows(BusinessException.class,
                () -> service.updateAgentConfig("agent-1", config));
        verify(metadataRepository, never()).save(any());
    }

    // ============================================================
    // deleteAgent 写路径
    // ============================================================

    @Test
    @DisplayName("ACL-delete: 非 owner 调用 delete 抛 FORBIDDEN,不进入删除流程")
    void delete_nonOwner_throwsForbidden() {
        AgentMetadata existing = metadata("agent-1", OTHER_USER_ID);
        when(metadataRepository.findById("agent-1")).thenReturn(Optional.of(existing));
        doThrow(new BusinessException(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, "无权操作该智能体"))
                .when(ownershipResolver).assertCanManage(existing);

        assertThrows(BusinessException.class, () -> service.deleteAgent("agent-1"));
        verify(accessProfileRepository, never()).deleteById(anyString());
        verify(metadataRepository, never()).deleteById(anyString());
        verify(healthStatusRepository, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("ACL-delete: agentId 不存在抛 RESOURCE_NOT_FOUND(先于 ACL 校验)")
    void delete_notFound_throwsResourceNotFound() {
        when(metadataRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteAgent("missing"));
        verify(ownershipResolver, never()).assertCanManage(any());
    }

    // ============================================================
    // chat 路径
    // ============================================================

    @Test
    @DisplayName("ACL-chat: 非 owner 调用 chat 抛 FORBIDDEN,不进入 chat 主体")
    void chat_nonOwner_throwsForbidden() {
        AgentMetadata existing = metadata("agent-1", OTHER_USER_ID);
        when(metadataRepository.findById("agent-1")).thenReturn(Optional.of(existing));
        // chat 主体先查 accessProfile, 也 stub 一下, 否则会先于 ACL 抛 "profile not found"
        when(accessProfileRepository.findById("agent-1"))
                .thenReturn(Optional.of(com.adlin.orin.modules.agent.entity.AgentAccessProfile.builder()
                        .agentId("agent-1").endpointUrl("http://x").apiKey("k").build()));
        doThrow(new BusinessException(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, "无权操作该智能体"))
                .when(ownershipResolver).assertCanManage(existing);

        assertThrows(BusinessException.class,
                () -> service.chat("agent-1", "hi", (String) null));
        verify(metaKnowledgeService, never()).assembleSystemPrompt(anyString());
    }

    // ============================================================
    // helpers
    // ============================================================

    private AgentMetadata metadata(String agentId, Long ownerUserId) {
        return AgentMetadata.builder()
                .agentId(agentId)
                .name("agent-" + agentId)
                .ownerUserId(ownerUserId)
                .build();
    }
}
