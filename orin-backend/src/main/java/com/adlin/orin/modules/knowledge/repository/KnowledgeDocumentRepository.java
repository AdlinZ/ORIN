package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {

    /**
     * 获取指定知识库的所有文档
     */
    List<KnowledgeDocument> findByKnowledgeBaseIdOrderByUploadTimeDesc(String knowledgeBaseId);

    /**
     * 按向量化状态查询文档
     */
    List<KnowledgeDocument> findByVectorStatus(String vectorStatus);

    /**
     * 统计知识库的文档数量
     */
    long countByKnowledgeBaseId(String knowledgeBaseId);

    /**
     * 统计知识库的总字符数
     */
    @Query("SELECT SUM(d.charCount) FROM KnowledgeDocument d WHERE d.knowledgeBaseId = :kbId")
    Long sumCharCountByKnowledgeBaseId(@Param("kbId") String kbId);

    /**
     * 统计所有知识库的文档数量（有 chunkCount 的文档）
     */
    @Query("SELECT COUNT(d) FROM KnowledgeDocument d WHERE d.chunkCount > 0")
    long countIndexedDocuments();

    /**
     * 统计所有知识库的总向量数（chunkCount 之和）
     */
    @Query("SELECT COALESCE(SUM(d.chunkCount), 0) FROM KnowledgeDocument d WHERE d.chunkCount > 0")
    long sumTotalChunks();

    /**
     * 统计每个知识库的文档数和向量数
     */
    @Query("SELECT d.knowledgeBaseId, COUNT(d), COALESCE(SUM(d.chunkCount), 0) FROM KnowledgeDocument d WHERE d.chunkCount > 0 GROUP BY d.knowledgeBaseId")
    List<Object[]> countByKnowledgeBase();

    /**
     * 删除知识库的所有文档
     */
    void deleteByKnowledgeBaseId(String knowledgeBaseId);

    /**
     * 查询待向量化的文档
     */
    List<KnowledgeDocument> findByVectorStatusIn(List<String> statuses);

    /**
     * 更新向量化状态
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE KnowledgeDocument d SET d.vectorStatus = :status, d.lastModified = CURRENT_TIMESTAMP WHERE d.id = :id")
    void updateVectorStatus(@Param("id") String id, @Param("status") String status);
}
