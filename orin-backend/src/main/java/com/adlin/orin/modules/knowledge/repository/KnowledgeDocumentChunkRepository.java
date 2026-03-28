package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeDocumentChunkRepository extends JpaRepository<KnowledgeDocumentChunk, String> {

    /**
     * 根据文档ID查询分片（按索引排序）
     */
    List<KnowledgeDocumentChunk> findByDocumentIdOrderByChunkIndex(String documentId);

    /**
     * 根据文档ID和分片类型查询
     */
    List<KnowledgeDocumentChunk> findByDocumentIdAndChunkType(String documentId, String chunkType);

    /**
     * 根据父分片ID查询子分片
     */
    List<KnowledgeDocumentChunk> findByParentIdOrderByChunkIndex(String parentId);

    /**
     * 批量查询
     */
    List<KnowledgeDocumentChunk> findByIdIn(List<String> ids);

    /**
     * 统计文档的分块数
     */
    long countByDocumentId(String documentId);

    /**
     * 根据文档ID和父分片ID列表查询
     */
    List<KnowledgeDocumentChunk> findByDocumentIdAndParentIdIn(String documentId, List<String> parentIds);

    /**
     * 关键词检索 - 支持多个关键词（任意匹配）
     */
    @Query("SELECT c FROM KnowledgeDocumentChunk c WHERE c.documentId IN " +
            "(SELECT d.id FROM KnowledgeDocument d WHERE d.knowledgeBaseId = :kbId AND d.deletedFlag = false) " +
            "AND (LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<KnowledgeDocumentChunk> searchByKeyword(@Param("kbId") String kbId, @Param("keyword") String keyword);

    /**
     * 全局关键词检索 (跨所有知识库) - 支持多个关键词（任意匹配）
     */
    @Query("SELECT c FROM KnowledgeDocumentChunk c WHERE c.documentId IN " +
            "(SELECT d.id FROM KnowledgeDocument d WHERE d.deletedFlag = false) " +
            "AND (LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<KnowledgeDocumentChunk> searchAllByKeyword(@Param("keyword") String keyword);

    /**
     * 按文档ID列表过滤的关键词检索
     */
    @Query("SELECT c FROM KnowledgeDocumentChunk c WHERE c.documentId IN :docIds " +
            "AND (LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<KnowledgeDocumentChunk> searchByDocIds(@Param("docIds") List<String> docIds, @Param("keyword") String keyword);

    /**
     * 删除文档的所有分片
     */
    void deleteByDocumentId(String documentId);
}
