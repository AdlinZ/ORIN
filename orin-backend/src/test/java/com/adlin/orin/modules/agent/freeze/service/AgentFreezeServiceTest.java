package com.adlin.orin.modules.agent.freeze.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.entity.AgentVersion;
import com.adlin.orin.modules.agent.freeze.audit.AgentVersionAuditWriter;
import com.adlin.orin.modules.agent.freeze.dto.FreezeAgentResponse;
import com.adlin.orin.modules.agent.freeze.dto.FreezeSecretRefItem;
import com.adlin.orin.modules.agent.freeze.entity.AgentVersionFreezeIdempotency;
import com.adlin.orin.modules.agent.freeze.repository.AgentVersionFreezeIdempotencyRepository;
import com.adlin.orin.modules.agent.freeze.repository.AgentVersionSecretRefRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.repository.AgentVersionRepository;
import com.adlin.orin.modules.agent.service.AgentOwnershipResolver;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.repository.GatewaySecretRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentFreezeService 关键路径单测（ADR-002 v4.1 §D-2.3.1）。
 * <p>F02 R3：secret refs 来自草稿 {@code pending_secret_refs}，不再走 FreezeAgentRequest。
 */
@ExtendWith(MockitoExtension.class)
class AgentFreezeServiceTest {

    @Mock AgentMetadataRepository agentMetadataRepository;
    @Mock AgentVersionRepository agentVersionRepository;
    @Mock AgentVersionSecretRefRepository secretRefRepository;
    @Mock AgentVersionFreezeIdempotencyRepository idempotencyRepository;
    @Mock GatewaySecretRepository gatewaySecretRepository;
    @Mock EntityManager entityManager;
    @Mock AgentVersionAuditWriter auditWriter;
    @Mock AgentDraftService draftService;
    @Mock AgentOwnershipResolver ownershipResolver;

    ObjectMapper objectMapper = new ObjectMapper();
    AgentFreezeService service;

    @BeforeEach
    void setup() {
        service = new AgentFreezeService(agentMetadataRepository, agentVersionRepository,
                secretRefRepository, idempotencyRepository, gatewaySecretRepository,
                auditWriter, draftService, entityManager, objectMapper, ownershipResolver);
    }

    private List<FreezeSecretRefItem> sampleRefs() {
        return List.of(FreezeSecretRefItem.builder()
                .alias("openai.primary")
                .source("CONTROL_PLANE")
                .secretId("gsec_test")
                .required(true)
                .injectAs("OPENAI_API_KEY")
                .build());
    }

    private AgentMetadata sampleMeta(String activeVersionId) {
        return AgentMetadata.builder()
                .agentId("ag_test")
                .name("Test Agent")
                .description("desc")
                .mode("agent")
                .modelName("gpt-4o")
                .providerType("OPENAI")
                .systemPrompt("You are helpful")
                .temperature(0.7)
                .topP(1.0)
                .maxTokens(2048)
                .ownerUserId(1L)
                .activeVersionId(activeVersionId)
                .build();
    }

    private GatewaySecret sampleSecret(String id) {
        return GatewaySecret.builder()
                .id("internal_uuid")
                .secretId(id)
                .secretType(GatewaySecret.SecretType.PROVIDER_CREDENTIAL)
                .status(GatewaySecret.SecretStatus.ACTIVE)
                .keyPrefix("sk-orin")
                .last4("1234")
                .build();
    }

    @Test
    @DisplayName("happy path：首次 freeze 创建 FROZEN version 与 active 指针；secret refs 来自草稿")
    void freeze_happyPath_firstTime() {
        AgentMetadata meta = sampleMeta(null);
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(meta);
        when(draftService.readPendingSecretRefs("ag_test")).thenReturn(sampleRefs());
        when(idempotencyRepository.findByAgentIdAndIdempotencyKeyHash(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(gatewaySecretRepository.findBySecretId("gsec_test")).thenReturn(Optional.of(sampleSecret("gsec_test")));
        when(agentVersionRepository.findMaxVersionNumber("ag_test")).thenReturn(Optional.of(0));
        when(agentVersionRepository.saveAndFlush(any(AgentVersion.class)))
                .thenAnswer(inv -> {
                    AgentVersion v = inv.getArgument(0);
                    if (v.getId() == null) v.setId("ver_test_1");
                    return v;
                });

        FreezeAgentResponse resp = service.freeze("ag_test", "keyhash123", "user1");

        assertNotNull(resp);
        assertEquals("FROZEN", resp.getStatus());
        assertEquals("ver_test_1", resp.getAgentVersionId());
        assertEquals(64, resp.getContentDigest().length());
        assertFalse(resp.isIdempotentReplay());
        assertEquals("ver_test_1", meta.getActiveVersionId()); // first freeze 自动 active
        verify(secretRefRepository).saveAll(any());
        verify(idempotencyRepository).saveAndFlush(any(AgentVersionFreezeIdempotency.class));
        verify(auditWriter).logFrozen(eq("user1"), eq("ag_test"), eq("ver_test_1"), eq(1), anyString(),
                eq((short) 1), eq(false), anyString());
    }

    @Test
    @DisplayName("草稿允许无 secret refs：freeze 成功；empty list saveAll(空) 也允许（无副作用）")
    void freeze_noRefs_allowed() {
        AgentMetadata meta = sampleMeta(null);
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(meta);
        when(draftService.readPendingSecretRefs("ag_test")).thenReturn(List.of()); // no refs
        when(idempotencyRepository.findByAgentIdAndIdempotencyKeyHash(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(agentVersionRepository.findMaxVersionNumber("ag_test")).thenReturn(Optional.of(0));
        when(agentVersionRepository.saveAndFlush(any(AgentVersion.class)))
                .thenAnswer(inv -> {
                    AgentVersion v = inv.getArgument(0);
                    if (v.getId() == null) v.setId("ver_no_refs");
                    return v;
                });

        FreezeAgentResponse resp = service.freeze("ag_test", "kh", "user1");
        assertEquals("FROZEN", resp.getStatus());
        // saveAll(emptyList) 是无副作用实现；这里仅验证 freeze 整体成功。
    }

    @Test
    @DisplayName("idempotent replay（同 key + 同 payload）→ 返回历史 versionId")
    void freeze_idempotentReplay_samePayload() throws Exception {
        AgentMetadata meta = sampleMeta(null);
        AgentVersion historical = AgentVersion.builder()
                .id("ver_existing").agentId("ag_test").versionNumber(7)
                .status(AgentVersion.Status.FROZEN)
                .contentDigest("a".repeat(64))
                .snapshotSchemaVersion((short) 1)
                .frozenAt(java.time.LocalDateTime.now())
                .frozenBy("user1")
                .build();
        String expectedDigest = service.calculateContentDigest(meta, sampleRefs());
        AgentVersionFreezeIdempotency idem = AgentVersionFreezeIdempotency.builder()
                .agentId("ag_test").idempotencyKeyHash("keyhash123")
                .requestDigest(expectedDigest)
                .agentVersionId("ver_existing")
                .createdAt(java.time.LocalDateTime.now())
                .expiresAt(java.time.LocalDateTime.now().plusHours(24))
                .build();
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(meta);
        when(draftService.readPendingSecretRefs("ag_test")).thenReturn(sampleRefs());
        when(idempotencyRepository.findByAgentIdAndIdempotencyKeyHash("ag_test", "keyhash123"))
                .thenReturn(Optional.of(idem));
        when(agentVersionRepository.findById("ver_existing")).thenReturn(Optional.of(historical));

        FreezeAgentResponse resp = service.freeze("ag_test", "keyhash123", "user1");
        assertTrue(resp.isIdempotentReplay());
        assertEquals("ver_existing", resp.getAgentVersionId());
        verify(agentVersionRepository, never()).saveAndFlush(any(AgentVersion.class));
        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("同 key 修改 Prompt（secret refs 不变）→ IDEMPOTENCY_KEY_CONFLICT")
    void freeze_idempotencyKeyConflict_whenDraftConfigChanged() {
        AgentMetadata meta = sampleMeta(null);
        String originalDigest = service.calculateContentDigest(meta, sampleRefs());
        meta.setSystemPrompt("changed prompt with the same secret refs");
        AgentVersionFreezeIdempotency idem = AgentVersionFreezeIdempotency.builder()
                .agentId("ag_test").idempotencyKeyHash("keyhash123")
                .requestDigest(originalDigest)
                .agentVersionId("ver_existing")
                .createdAt(java.time.LocalDateTime.now())
                .expiresAt(java.time.LocalDateTime.now().plusHours(24))
                .build();
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(meta);
        when(draftService.readPendingSecretRefs("ag_test")).thenReturn(sampleRefs());
        when(idempotencyRepository.findByAgentIdAndIdempotencyKeyHash("ag_test", "keyhash123"))
                .thenReturn(Optional.of(idem));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.freeze("ag_test", "keyhash123", "user1"));
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, ex.getErrorCode());
        verify(auditWriter).logFreezeRejected(eq("user1"), eq("ag_test"), eq("keyhash123"),
                eq("30010"), anyString());
    }

    @Test
    @DisplayName("草稿 secretId 引用不存在的 secret → SECRET_REFERENCE_NOT_FOUND")
    void freeze_secretReferenceNotFound() {
        AgentMetadata meta = sampleMeta(null);
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(meta);
        when(draftService.readPendingSecretRefs("ag_test")).thenReturn(sampleRefs());
        when(idempotencyRepository.findByAgentIdAndIdempotencyKeyHash(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(gatewaySecretRepository.findBySecretId("gsec_test")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.freeze("ag_test", "keyhash123", "user1"));
        assertEquals(ErrorCode.SECRET_REFERENCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("草稿 secretId 引用但 secret 非 ACTIVE → SECRET_REFERENCE_NOT_FOUND")
    void freeze_secretNotActive() {
        AgentMetadata meta = sampleMeta(null);
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(meta);
        when(draftService.readPendingSecretRefs("ag_test")).thenReturn(sampleRefs());
        when(idempotencyRepository.findByAgentIdAndIdempotencyKeyHash(anyString(), anyString()))
                .thenReturn(Optional.empty());
        GatewaySecret disabled = sampleSecret("gsec_test");
        disabled.setStatus(GatewaySecret.SecretStatus.DISABLED);
        when(gatewaySecretRepository.findBySecretId("gsec_test")).thenReturn(Optional.of(disabled));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.freeze("ag_test", "keyhash123", "user1"));
        assertEquals(ErrorCode.SECRET_REFERENCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("RUNNER_LOCAL source → MVP 拒绝冻结 RUNNER_LOCAL_SECRET_MISSING")
    void freeze_runnerLocalRejected() {
        AgentMetadata meta = sampleMeta(null);
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(meta);
        when(draftService.readPendingSecretRefs("ag_test")).thenReturn(List.of(
                FreezeSecretRefItem.builder()
                        .alias("provider.env").source("RUNNER_LOCAL")
                        .localKey("OPENAI_API_KEY").required(true)
                        .injectAs("OPENAI_API_KEY").build()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.freeze("ag_test", "keyhash123", "user1"));
        assertEquals(ErrorCode.RUNNER_LOCAL_SECRET_MISSING, ex.getErrorCode());
        verify(agentVersionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("草稿不存在 → AGENT_NOT_FOUND（先于 idempotency 写）")
    void freeze_agentNotFound() {
        // 不 stub idempotencyRepository：lockDraft 已经先抛 AGENT_NOT_FOUND，不应触 idempotency。
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.freeze("ag_test", "keyhash123", "user1"));
        assertEquals(ErrorCode.AGENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("active_version_id 已存在时 freeze 仍允许（生成新版本）但不修改 active 指针")
    void freeze_newVersionWhileActiveExists_keepsActive() {
        AgentMetadata meta = sampleMeta("ver_active_existing");
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(meta);
        when(draftService.readPendingSecretRefs("ag_test")).thenReturn(sampleRefs());
        when(idempotencyRepository.findByAgentIdAndIdempotencyKeyHash(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(gatewaySecretRepository.findBySecretId("gsec_test")).thenReturn(Optional.of(sampleSecret("gsec_test")));
        when(agentVersionRepository.findMaxVersionNumber("ag_test")).thenReturn(Optional.of(3));
        when(agentVersionRepository.saveAndFlush(any(AgentVersion.class)))
                .thenAnswer(inv -> {
                    AgentVersion v = inv.getArgument(0);
                    if (v.getId() == null) v.setId("ver_new_4");
                    return v;
                });

        FreezeAgentResponse resp = service.freeze("ag_test", "keyhash4", "user1");
        assertEquals(4, resp.getVersionNumber());
        assertEquals("ver_active_existing", meta.getActiveVersionId()); // unchanged
    }
}
