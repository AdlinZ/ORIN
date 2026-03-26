package com.adlin.orin.modules.knowledge.service.sync;

import com.adlin.orin.modules.knowledge.entity.SyncChangeLog;
import com.adlin.orin.modules.knowledge.entity.SyncWebhook;
import com.adlin.orin.modules.knowledge.repository.SyncChangeLogRepository;
import com.adlin.orin.modules.knowledge.repository.SyncWebhookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端侧同步接口单元测试
 */
@DataJpaTest
@ActiveProfiles("test")
public class SideClientSyncServiceTest {

    @Autowired
    private SyncChangeLogRepository changeLogRepository;

    @Autowired
    private SyncWebhookRepository webhookRepository;

    @Test
    void testCreateChangeLog() {
        // 创建变更记录
        SyncChangeLog changeLog = SyncChangeLog.builder()
                .agentId("agent-001")
                .documentId("doc-001")
                .knowledgeBaseId("kb-001")
                .changeType("CREATE")
                .version(1)
                .contentHash("abc123")
                .changedAt(LocalDateTime.now())
                .synced(false)
                .build();

        SyncChangeLog saved = changeLogRepository.save(changeLog);

        assertNotNull(saved.getId());
        assertEquals("agent-001", saved.getAgentId());
        assertEquals("CREATE", saved.getChangeType());
    }

    @Test
    void testQueryChangesByAgentId() {
        // 创建多个变更记录
        LocalDateTime now = LocalDateTime.now();

        for (int i = 1; i <= 5; i++) {
            SyncChangeLog changeLog = SyncChangeLog.builder()
                    .agentId("agent-002")
                    .documentId("doc-00" + i)
                    .knowledgeBaseId("kb-001")
                    .changeType(i % 2 == 0 ? "UPDATE" : "CREATE")
                    .version(i)
                    .contentHash("hash-00" + i)
                    .changedAt(now.minusMinutes(i))
                    .synced(false)
                    .build();
            changeLogRepository.save(changeLog);
        }

        // 查询变更记录 (需要 Pageable)
        Page<SyncChangeLog> changes = changeLogRepository.findByAgentIdOrderByChangedAtDesc("agent-002", PageRequest.of(0, 10));
        assertEquals(5, changes.getTotalElements());
    }

    @Test
    void testQueryPendingChanges() {
        // 测试查询待同步变更
        SyncChangeLog change1 = SyncChangeLog.builder()
                .agentId("agent-003")
                .documentId("doc-001")
                .knowledgeBaseId("kb-001")
                .changeType("CREATE")
                .version(1)
                .contentHash("hash1")
                .changedAt(LocalDateTime.now())
                .synced(false)
                .build();
        changeLogRepository.save(change1);

        SyncChangeLog change2 = SyncChangeLog.builder()
                .agentId("agent-003")
                .documentId("doc-002")
                .knowledgeBaseId("kb-001")
                .changeType("UPDATE")
                .version(2)
                .contentHash("hash2")
                .changedAt(LocalDateTime.now())
                .synced(false)
                .build();
        changeLogRepository.save(change2);

        // 已同步的
        SyncChangeLog change3 = SyncChangeLog.builder()
                .agentId("agent-003")
                .documentId("doc-003")
                .knowledgeBaseId("kb-001")
                .changeType("DELETE")
                .version(3)
                .contentHash("hash3")
                .changedAt(LocalDateTime.now())
                .synced(true)
                .build();
        changeLogRepository.save(change3);

        // 查询待同步变更
        List<SyncChangeLog> pending = changeLogRepository.findByAgentIdAndSyncedFalseOrderByChangedAtAsc("agent-003");
        assertEquals(2, pending.size());
    }

    @Test
    void testMarkAsSynced() {
        // 测试标记为已同步
        SyncChangeLog change = SyncChangeLog.builder()
                .agentId("agent-004")
                .documentId("doc-001")
                .knowledgeBaseId("kb-001")
                .changeType("CREATE")
                .version(1)
                .contentHash("hash1")
                .changedAt(LocalDateTime.now())
                .synced(false)
                .build();
        change = changeLogRepository.save(change);

        // 标记为已同步
        change.setSynced(true);
        changeLogRepository.save(change);

        Optional<SyncChangeLog> updated = changeLogRepository.findById(change.getId());
        assertTrue(updated.isPresent());
        assertTrue(updated.get().getSynced());
    }

    @Test
    void testChangeTypeEnum() {
        // 测试变更类型枚举
        SyncChangeLog createLog = SyncChangeLog.builder()
                .agentId("agent-005")
                .documentId("doc-create")
                .knowledgeBaseId("kb-001")
                .changeType("CREATE")
                .version(1)
                .contentHash("hash1")
                .changedAt(LocalDateTime.now())
                .synced(false)
                .build();
        changeLogRepository.save(createLog);

        SyncChangeLog updateLog = SyncChangeLog.builder()
                .agentId("agent-005")
                .documentId("doc-update")
                .knowledgeBaseId("kb-001")
                .changeType("UPDATE")
                .version(1)
                .contentHash("hash2")
                .changedAt(LocalDateTime.now())
                .synced(false)
                .build();
        changeLogRepository.save(updateLog);

        SyncChangeLog deleteLog = SyncChangeLog.builder()
                .agentId("agent-005")
                .documentId("doc-delete")
                .knowledgeBaseId("kb-001")
                .changeType("DELETE")
                .version(1)
                .contentHash("hash3")
                .changedAt(LocalDateTime.now())
                .synced(false)
                .build();
        changeLogRepository.save(deleteLog);

        Page<SyncChangeLog> all = changeLogRepository.findByAgentIdOrderByChangedAtDesc("agent-005", PageRequest.of(0, 10));
        assertEquals(3, all.getTotalElements());
    }

    @Test
    void testCreateWebhook() {
        // 测试创建 Webhook
        SyncWebhook webhook = SyncWebhook.builder()
                .agentId("agent-006")
                .webhookUrl("https://example.com/webhook")
                .webhookSecret("secret-key")
                .enabled(true)
                .build();

        SyncWebhook saved = webhookRepository.save(webhook);

        assertNotNull(saved.getId());
        assertEquals("agent-006", saved.getAgentId());
        assertTrue(saved.getEnabled());
    }

    @Test
    void testFindWebhookByAgentId() {
        // 测试查询启用的 Webhook
        SyncWebhook webhook = SyncWebhook.builder()
                .agentId("agent-007")
                .webhookUrl("https://example.com/webhook")
                .webhookSecret("secret")
                .enabled(true)
                .build();
        webhookRepository.save(webhook);

        // 使用正确的方法: findByAgentIdAndEnabledTrue
        List<SyncWebhook> found = webhookRepository.findByAgentIdAndEnabledTrue("agent-007");
        assertEquals(1, found.size());
        assertEquals("https://example.com/webhook", found.get(0).getWebhookUrl());
    }
}
