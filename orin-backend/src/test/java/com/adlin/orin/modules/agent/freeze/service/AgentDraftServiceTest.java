package com.adlin.orin.modules.agent.freeze.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.freeze.audit.AgentVersionAuditWriter;
import com.adlin.orin.modules.agent.freeze.dto.AgentDraftResponse;
import com.adlin.orin.modules.agent.freeze.dto.AgentDraftUpsertRequest;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.repository.AgentVersionRepository;
import com.adlin.orin.modules.agent.service.AgentOwnershipResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentDraftService 单测：覆盖 (a) 不允许用自选 id 创建草稿, (b) 真 UPDATE,
 * (c) createAgent 生成后端 id 与 owner, (d) getDraft 解析 active 指针, (e) not found。
 */
@ExtendWith(MockitoExtension.class)
class AgentDraftServiceTest {

    @Mock AgentMetadataRepository agentMetadataRepository;
    @Mock AgentVersionRepository agentVersionRepository;
    @Mock AgentVersionAuditWriter auditWriter;
    @Mock AgentOwnershipResolver ownershipResolver;
    AgentDraftService service;

    @BeforeEach
    void setup() {
        service = new AgentDraftService(agentMetadataRepository, agentVersionRepository,
                auditWriter, new ObjectMapper(), ownershipResolver);
    }

    private AgentMetadata meta(String activeId) {
        return AgentMetadata.builder()
                .agentId("ag_test").ownerUserId(1L).name("n").description("d").activeVersionId(activeId)
                .build();
    }

    @Test
    @DisplayName("createAgent: 生成 ag_<uuid> 形式的 agentId、写入 owner 并 INSERT")
    void createAgent_generatesIdAndInserts() {
        when(agentMetadataRepository.save(any(AgentMetadata.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ownershipResolver.resolveFromCurrentRequest()).thenReturn(1L);
        AgentDraftResponse resp = service.createAgent("my-agent", "demo", "user1");
        assertEquals("my-agent", resp.getName());
        assertEquals("demo", resp.getDescription());
        assertEquals(true, resp.getAgentId().startsWith("ag_"));
        verify(agentMetadataRepository).save(any(AgentMetadata.class));
        verify(ownershipResolver).resolveFromCurrentRequest();
        verify(auditWriter).logAgentCreated("user1", resp.getAgentId(), "my-agent");
    }

    @Test
    @DisplayName("upsertDraft: 草稿不存在 → AGENT_NOT_FOUND，不能用自选 id 绕过 owner 初始化")
    void upsert_missingAgent_rejected() {
        when(agentMetadataRepository.findById("ag_new")).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upsertDraft("ag_new", AgentDraftUpsertRequest.builder()
                        .name("brand-new").build(), null, "user1"));
        assertEquals(ErrorCode.AGENT_NOT_FOUND, ex.getErrorCode());
        verify(agentMetadataRepository, never()).save(any());
    }

    @Test
    @DisplayName("upsertDraft: 草稿存在 + active_version_id 已设 → UPDATE；F02 R3 不再阻断编辑")
    void upsert_afterFreeze_stillUpdates() {
        when(agentMetadataRepository.findById("ag_test"))
                .thenReturn(Optional.of(meta("ver_active_existing")));
        when(agentMetadataRepository.save(any(AgentMetadata.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentDraftResponse resp = service.upsertDraft("ag_test", AgentDraftUpsertRequest.builder()
                .name("renamed-after-freeze").build(), null, "user1");

        assertEquals("renamed-after-freeze", resp.getName());
        // 关键：这里我们不复 throw AGENT_VERSION_FROZEN（旧的 v0 行为错误地抛错）；
        // 应当让 save 正常被调用。
        verify(agentMetadataRepository).save(any(AgentMetadata.class));
        verify(ownershipResolver).assertCanManage(any(AgentMetadata.class));
    }

    @Test
    @DisplayName("getDraft: active 指针解析为版本号与 digest")
    void getDraft_resolvesActiveVersion() {
        when(agentMetadataRepository.findById("ag_test")).thenReturn(Optional.of(meta("ver_active")));
        when(agentVersionRepository.findById("ver_active")).thenReturn(Optional.of(
                com.adlin.orin.modules.agent.entity.AgentVersion.builder()
                        .id("ver_active").agentId("ag_test").versionNumber(3)
                        .contentDigest("abc123")
                        .status(com.adlin.orin.modules.agent.entity.AgentVersion.Status.FROZEN)
                        .build()));
        AgentDraftResponse resp = service.getDraft("ag_test");
        assertEquals(Integer.valueOf(3), resp.getActiveVersionNumber());
        assertEquals("abc123", resp.getActiveVersionDigest());
        assertEquals("FROZEN", resp.getActiveVersionStatus());
    }

    @Test
    @DisplayName("getDraft: Agent 不存在 → AGENT_NOT_FOUND")
    void getDraft_notFound() {
        when(agentMetadataRepository.findById("ag_test")).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getDraft("ag_test"));
        assertEquals(ErrorCode.AGENT_NOT_FOUND, ex.getErrorCode());
    }
}
