package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, String> {
    List<KnowledgeBase> findBySourceAgentId(String sourceAgentId);

    /**
     * 资源级 ACL 第 1 刀:按 owner 过滤知识库(普通用户列表场景)。
     * 现有 NULL owner 的 KB 视为"系统级",普通用户不可见,需 service 层再加 OR 逻辑或本方法扩展为
     * findByOwnerUserIdOrOwnerUserIdIsNull(currentUserId) (这里不混合,语义由 service 决定)。
     */
    List<KnowledgeBase> findByOwnerUserId(Long ownerUserId);
}
