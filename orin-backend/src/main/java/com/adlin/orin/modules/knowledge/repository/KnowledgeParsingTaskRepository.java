package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.KnowledgeParsingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 知识库解析任务 Repository
 */
@Repository
public interface KnowledgeParsingTaskRepository extends JpaRepository<KnowledgeParsingTask, String> {

    /**
     * 根据文档 ID 查找任务
     */
    List<KnowledgeParsingTask> findByDocumentId(String documentId);

    /**
     * 根据知识库 ID 查找任务
     */
    List<KnowledgeParsingTask> findByKnowledgeBaseId(String knowledgeBaseId);

    /**
     * 根据状态查找待处理任务（按优先级和创建时间排序）
     */
    @Query("SELECT t FROM KnowledgeParsingTask t WHERE t.status = :status ORDER BY t.priority DESC, t.createdAt ASC")
    List<KnowledgeParsingTask> findPendingTasks(@Param("status") String status);

    /**
     * 根据状态和知识库查找待处理任务
     */
    @Query("SELECT t FROM KnowledgeParsingTask t WHERE t.status = :status AND t.knowledgeBaseId = :kbId ORDER BY t.priority DESC, t.createdAt ASC")
    List<KnowledgeParsingTask> findPendingTasksByKnowledgeBase(
            @Param("status") String status,
            @Param("kbId") String knowledgeBaseId);

    /**
     * 统计某状态的任务数量
     */
    long countByStatus(String status);

    /**
     * 更新任务状态
     */
    @Modifying
    @Query("UPDATE KnowledgeParsingTask t SET t.status = :status WHERE t.id = :id")
    void updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 删除文档关联的任务
     */
    void deleteByDocumentId(String documentId);

    /**
     * 删除知识库关联的所有任务
     */
    void deleteByKnowledgeBaseId(String knowledgeBaseId);
}
