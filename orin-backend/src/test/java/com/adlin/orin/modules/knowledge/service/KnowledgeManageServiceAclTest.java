package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentChunkRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.service.meta.MetaKnowledgeService;
import com.adlin.orin.modules.knowledge.service.sync.DifyKnowledgeSyncService;
import com.adlin.orin.modules.model.service.ModelConfigService;
import com.adlin.orin.modules.model.service.SiliconFlowIntegrationService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 资源级 ACL 第 1 刀 (KnowledgeBase) 行为测试。
 *
 * 覆盖:
 * - getAllKnowledgeBases: admin / operator / 普通用户 读路径
 * - createKnowledgeBase: 自动绑定当前用户为 owner
 * - updateKnowledgeBase: 非 owner 抛 FORBIDDEN, owner 成功
 * - deleteKnowledgeBase: 非 owner 抛 FORBIDDEN
 *
 * 运行方式: mvn test -Dtest=KnowledgeManageServiceAclTest
 */
class KnowledgeManageServiceAclTest {

    private static final Long USER_ID = 100L;
    private static final Long OTHER_USER_ID = 200L;
    private static final Long ADMIN_ID = 1L;
    private static final Long OPERATOR_ID = 2L;

    // 仅 mock 真正会被调用的依赖, 其他传 mock(...) 占位以满足构造器签名
    private final KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
    private final KnowledgeOwnershipResolver ownershipResolver = mock(KnowledgeOwnershipResolver.class);
    private final KnowledgeGraphService knowledgeGraphService = mock(KnowledgeGraphService.class);
    private final DocumentManageService documentService = mock(DocumentManageService.class);
    private final StructuredService structuredService = mock(StructuredService.class);
    private final ProceduralService proceduralService = mock(ProceduralService.class);
    private final MetaKnowledgeService metaKnowledgeService = mock(MetaKnowledgeService.class);
    private final MilvusVectorService milvusVectorService = mock(MilvusVectorService.class);

    private KnowledgeManageService service;

    @BeforeEach
    void setUp() {
        // mapToDTO 内部走 UNSTRUCTURED 分支, 需要 documentService 返回非空 stats
        when(documentService.getKnowledgeBaseStats(anyString()))
                .thenReturn(new com.adlin.orin.modules.knowledge.service.DocumentManageService.DocumentStats(0, 0));
        service = new KnowledgeManageService(
                mock(AgentAccessProfileRepository.class),
                knowledgeBaseRepository,
                knowledgeGraphService,
                mock(RestTemplate.class),
                mock(DifyKnowledgeSyncService.class),
                mock(com.adlin.orin.modules.knowledge.component.VectorStoreProvider.class),
                milvusVectorService,
                documentService,
                structuredService,
                proceduralService,
                metaKnowledgeService,
                mock(KnowledgeDocumentChunkRepository.class),
                mock(KnowledgeDocumentRepository.class),
                mock(ModelConfigService.class),
                mock(SiliconFlowIntegrationService.class),
                ownershipResolver
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ============================================================
    // getAllKnowledgeBases 读路径
    // ============================================================

    @Test
    @DisplayName("ACL-read: 普通用户 getAll 仅按 owner 过滤,看不到 NULL owner 与他人的 KB")
    void getAll_regularUser_filtersByOwner() {
        when(ownershipResolver.isCurrentUserPrivileged()).thenReturn(false);
        when(ownershipResolver.resolveFromCurrentRequest()).thenReturn(USER_ID);
        when(knowledgeBaseRepository.findByOwnerUserId(USER_ID))
                .thenReturn(List.of(kb("kb-1", USER_ID)));

        var result = service.getAllKnowledgeBases();

        assertEquals(1, result.size());
        verify(knowledgeBaseRepository).findByOwnerUserId(USER_ID);
        verify(knowledgeBaseRepository, never()).findAll();
    }

    @Test
    @DisplayName("ACL-read: 管理员 getAll 走 findAll,不受 owner 限制")
    void getAll_admin_seesAll() {
        when(ownershipResolver.isCurrentUserPrivileged()).thenReturn(true);
        when(knowledgeBaseRepository.findAll()).thenReturn(List.of(
                kb("kb-1", USER_ID), kb("kb-system", null), kb("kb-other", OTHER_USER_ID)));

        var result = service.getAllKnowledgeBases();

        assertEquals(3, result.size());
        verify(knowledgeBaseRepository).findAll();
        verify(knowledgeBaseRepository, never()).findByOwnerUserId(any());
    }

    @Test
    @DisplayName("ACL-read: 运维 getAll 走 findAll,豁免 owner 限制(与 TODO.md 角色矩阵一致)")
    void getAll_operator_seesAll() {
        when(ownershipResolver.isCurrentUserPrivileged()).thenReturn(true);
        when(knowledgeBaseRepository.findAll()).thenReturn(List.of(
                kb("kb-1", USER_ID), kb("kb-system", null)));

        var result = service.getAllKnowledgeBases();

        assertEquals(2, result.size());
        verify(knowledgeBaseRepository).findAll();
    }

    // ============================================================
    // createKnowledgeBase 写路径
    // ============================================================

    @Test
    @DisplayName("ACL-create: owner 为 null 时自动绑定当前用户;不信任请求体里的 owner")
    void create_autoBindsCurrentUserAsOwner() {
        when(ownershipResolver.resolveFromCurrentRequest()).thenReturn(USER_ID);
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        KnowledgeBase input = KnowledgeBase.builder()
                .name("new-kb")
                .description("test")
                .build();
        // 即便请求体里塞了 other_user_id, 也不应该被信任
        input.setOwnerUserId(OTHER_USER_ID);

        KnowledgeBase saved = service.createKnowledgeBase(input);

        assertEquals(USER_ID, saved.getOwnerUserId(),
                "owner 已被强制覆盖为当前请求用户,不信任请求体里的 owner_user_id");
    }

    // ============================================================
    // updateKnowledgeBase 写路径
    // ============================================================

    @Test
    @DisplayName("ACL-update: 非 owner 调用 update 抛 BusinessException(FORBIDDEN),不进入字段更新")
    void update_nonOwner_throwsForbidden() {
        KnowledgeBase existing = kb("kb-1", OTHER_USER_ID);
        when(knowledgeBaseRepository.findById("kb-1")).thenReturn(java.util.Optional.of(existing));
        doThrow(new BusinessException(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, "无权操作该知识库"))
                .when(ownershipResolver).assertCanManage(existing);

        KnowledgeBase updates = KnowledgeBase.builder().name("hacked").build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateKnowledgeBase("kb-1", updates));
        assertEquals(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(knowledgeBaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("ACL-update: owner 调用 update 正常进入字段更新与 save")
    void update_owner_succeeds() {
        KnowledgeBase existing = kb("kb-1", USER_ID);
        when(knowledgeBaseRepository.findById("kb-1")).thenReturn(java.util.Optional.of(existing));
        // owner 校验通过, 不抛异常
        doNothing().when(ownershipResolver).assertCanManage(existing);
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        KnowledgeBase updates = KnowledgeBase.builder().name("renamed").build();
        KnowledgeBase saved = service.updateKnowledgeBase("kb-1", updates);

        assertEquals("renamed", saved.getName());
        verify(ownershipResolver).assertCanManage(existing);
    }

    // ============================================================
    // deleteKnowledgeBase 写路径
    // ============================================================

    @Test
    @DisplayName("ACL-delete: 非 owner 调用 delete 抛 BusinessException(FORBIDDEN),不进入删除流程")
    void delete_nonOwner_throwsForbidden() {
        KnowledgeBase existing = kb("kb-1", OTHER_USER_ID);
        when(knowledgeBaseRepository.findById("kb-1")).thenReturn(java.util.Optional.of(existing));
        doThrow(new BusinessException(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, "无权操作该知识库"))
                .when(ownershipResolver).assertCanManage(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteKnowledgeBase("kb-1"));
        assertEquals(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(documentService, never()).deleteByKnowledgeBaseId(any());
    }

    // ============================================================
    // helpers
    // ============================================================

    private KnowledgeBase kb(String id, Long ownerUserId) {
        return KnowledgeBase.builder()
                .id(id)
                .name("kb-" + id)
                .status("ENABLED")
                .ownerUserId(ownerUserId)
                .build();
    }

    /**
     * 工具方法: 在 SecurityContextHolder 中放入一个带 principal + authorities 的 Authentication。
     * 当前测试主要通过 mock {@link KnowledgeOwnershipResolver} 来覆盖 ACL 路径, 不依赖真实 SecurityContext。
     * 这里保留以备未来需要端到端验证时使用。
     */
    @SuppressWarnings("unused")
    private void setCurrentUser(Long userId, String... roles) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                String.valueOf(userId),
                null,
                java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
