package com.adlin.orin.modules.agent.freeze.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.entity.AgentVersion;
import com.adlin.orin.modules.agent.freeze.audit.AgentVersionAuditWriter;
import com.adlin.orin.modules.agent.freeze.dto.AgentVersionDetailResponse;
import com.adlin.orin.modules.agent.freeze.repository.AgentVersionSecretRefRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.repository.AgentVersionRepository;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.repository.GatewaySecretRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentVersionLifecycleService 关键路径单测（ADR-002 v4.1 §D-2.1.1 / §D-2.2）。
 */
@ExtendWith(MockitoExtension.class)
class AgentVersionLifecycleServiceTest {

    @Mock AgentMetadataRepository agentMetadataRepository;
    @Mock AgentVersionRepository agentVersionRepository;
    @Mock AgentVersionSecretRefRepository secretRefRepository;
    @Mock GatewaySecretRepository gatewaySecretRepository;
    @Mock EntityManager entityManager;
    @Mock AgentVersionAuditWriter auditWriter;

    AgentVersionLifecycleService service;

    @BeforeEach
    void setup() {
        service = new AgentVersionLifecycleService(agentMetadataRepository, agentVersionRepository,
                secretRefRepository, gatewaySecretRepository, auditWriter, entityManager);
    }

    private AgentMetadata meta(String activeId) {
        return AgentMetadata.builder().agentId("ag_test").name("n").activeVersionId(activeId).build();
    }

    private AgentVersion version(String id, int n, AgentVersion.Status status) {
        return AgentVersion.builder()
                .id(id)
                .agentId("ag_test")
                .versionNumber(n)
                .status(status)
                .contentDigest("a".repeat(64))
                .snapshotSchemaVersion((short) 1)
                .frozenAt(java.time.LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("switchActive：FROZEN 目标 → 写入 active 指针 + audit，不写 idempotent audit")
    void switch_activeFrozen_success() {
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(meta(null));
        AgentVersion target = version("ver_target", 5, AgentVersion.Status.FROZEN);
        when(agentVersionRepository.findByIdAndAgentId("ver_target", "ag_test"))
                .thenReturn(Optional.of(target));
        when(secretRefRepository.findByAgentVersionIdOrderByAliasAsc("ver_target"))
                .thenReturn(List.of());

        AgentVersionDetailResponse resp = service.switchActiveVersion("ag_test", "ver_target", "user1");
        assertEquals("ver_target", resp.getAgentVersionId());
        assertTrue(resp.isActive());
        verify(auditWriter).logActiveSwitched(eq("user1"), eq("ag_test"), eq(null), eq("ver_target"));
    }

    @Test
    @DisplayName("switchActive：DEPRECATED 目标 → RUN_VERSION_RETIRED 409，不写 audit")
    void switch_deprecated_throws() {
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(meta("ver_current"));
        AgentVersion target = version("ver_dep", 3, AgentVersion.Status.DEPRECATED);
        when(agentVersionRepository.findByIdAndAgentId("ver_dep", "ag_test"))
                .thenReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.switchActiveVersion("ag_test", "ver_dep", "user1"));
        assertEquals(ErrorCode.RUN_VERSION_RETIRED, ex.getErrorCode());
        verify(auditWriter).logActiveSwitchRejected(eq("user1"), eq("ag_test"), eq("ver_dep"),
                eq(ErrorCode.RUN_VERSION_RETIRED.getCode()));
        verify(agentMetadataRepository, never()).save(any());
    }

    @Test
    @DisplayName("switchActive：与现有 active 相同 → noop，不写 audit")
    void switch_sameActive_isNoop() {
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(meta("ver_same"));
        AgentVersion target = version("ver_same", 2, AgentVersion.Status.FROZEN);
        when(agentVersionRepository.findByIdAndAgentId("ver_same", "ag_test"))
                .thenReturn(Optional.of(target));
        when(secretRefRepository.findByAgentVersionIdOrderByAliasAsc("ver_same")).thenReturn(List.of());

        service.switchActiveVersion("ag_test", "ver_same", "user1");
        verify(auditWriter, never()).logActiveSwitched(any(), any(), any(), any());
        verify(agentMetadataRepository, never()).save(any());
    }

    @Test
    @DisplayName("deprecate：FROZEN 非 active → DEPRECATED + 审计")
    void deprecate_nonActive_success() {
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(meta("ver_active"));
        AgentVersion target = version("ver_old", 4, AgentVersion.Status.FROZEN);
        when(agentVersionRepository.findByIdAndAgentId("ver_old", "ag_test"))
                .thenReturn(Optional.of(target));
        when(secretRefRepository.findByAgentVersionIdOrderByAliasAsc("ver_old")).thenReturn(List.of());

        AgentVersionDetailResponse resp = service.deprecateVersion("ag_test", "ver_old", "superseded", "user1");
        assertEquals("DEPRECATED", resp.getStatus());
        assertFalse(resp.isActive());
        verify(auditWriter).logDeprecated(eq("user1"), eq("ag_test"), eq("ver_old"), eq("superseded"));
    }

    @Test
    @DisplayName("deprecate：active 版本 → RUN_VERSION_RETIRED 409，不修改状态")
    void deprecate_active_rejected() {
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(meta("ver_active"));
        AgentVersion target = version("ver_active", 1, AgentVersion.Status.FROZEN);
        when(agentVersionRepository.findByIdAndAgentId("ver_active", "ag_test"))
                .thenReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deprecateVersion("ag_test", "ver_active", "trying to retire active", "user1"));
        assertEquals(ErrorCode.RUN_VERSION_RETIRED, ex.getErrorCode());
        verify(auditWriter).logDeprecateRejected(eq("user1"), eq("ag_test"), eq("ver_active"),
                eq(ErrorCode.RUN_VERSION_RETIRED.getCode()));
    }

    @Test
    @DisplayName("deprecate：已 DEPRECATED 不可再 deprecate")
    void deprecate_alreadyDeprecated_rejected() {
        when(entityManager.find(AgentMetadata.class, "ag_test", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(meta("ver_active"));
        AgentVersion target = version("ver_dep", 5, AgentVersion.Status.DEPRECATED);
        when(agentVersionRepository.findByIdAndAgentId("ver_dep", "ag_test"))
                .thenReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deprecateVersion("ag_test", "ver_dep", "double deprecate", "user1"));
        assertEquals(ErrorCode.RUN_VERSION_RETIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("listVersions：返回 is_active 标记当前 active 的版本")
    void list_marksActive() {
        when(agentMetadataRepository.existsById("ag_test")).thenReturn(true);
        when(agentMetadataRepository.findById("ag_test")).thenReturn(Optional.of(meta("ver_active")));
        when(agentVersionRepository.findByAgentIdOrderByVersionNumberDesc("ag_test"))
                .thenReturn(List.of(
                        version("ver_active", 2, AgentVersion.Status.FROZEN),
                        version("ver_old", 1, AgentVersion.Status.FROZEN)));

        List<com.adlin.orin.modules.agent.freeze.dto.AgentVersionListItem> out = service.listVersions("ag_test");
        assertEquals(2, out.size());
        assertTrue(out.get(0).isActive());
        assertFalse(out.get(1).isActive());
    }

    @Test
    @DisplayName("listActiveGatewaySecrets：仅 ACTIVE 状态的 secret 才返回")
    void listActiveSecrets_filtersActive() {
        GatewaySecret active = GatewaySecret.builder()
                .id("u1").secretId("gsec_active").status(GatewaySecret.SecretStatus.ACTIVE)
                .keyPrefix("sk-").last4("1234").secretType(GatewaySecret.SecretType.PROVIDER_CREDENTIAL).build();
        GatewaySecret disabled = GatewaySecret.builder()
                .id("u2").secretId("gsec_disabled").status(GatewaySecret.SecretStatus.DISABLED).build();
        when(gatewaySecretRepository.findAll()).thenReturn(List.of(active, disabled));

        List<com.adlin.orin.modules.agent.freeze.dto.AgentSecretSummary> out = service.listActiveGatewaySecrets();
        assertEquals(1, out.size());
        assertEquals("gsec_active", out.get(0).getSecretId());
    }
}
