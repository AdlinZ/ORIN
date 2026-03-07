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
     * 获取文档的所有分片
     */
    List<KnowledgeDocumentChunk> findByDocumentIdOrderByChunkIndex(String documentId);

    /**
     * 根据 ID 列表查询分片
     */
    List<KnowledgeDocumentChunk> findByIdIn(List<String> ids);

    /**
     * 根据文档 ID 和父分片 ID 列表查询 (用于 Parent-Child 检索)
     */
    @Query("SELECT c FROM KnowledgeDocumentChunk c WHERE c.documentId = :docId AND c.parentId IN :parentIds")
    List<KnowledgeDocumentChunk> findByDocumentIdAndParentIdIn(
            @Param("docId") String documentId,
            @Param("parentIds") List<String> parentIds);

    /**
     * 关键词检索 (简化版)
     * 实际生产中应使用 Full Text Search (如 PostgreSQL tsvector 或 MySQL FULLTEXT)
     * 为兼容性暂用 LIKE
     */
    @Query("SELECT c FROM KnowledgeDocumentChunk c WHERE c.content LIKE %:keyword% AND c.documentId IN " +
            "(SELECT d.id FROM KnowledgeDocument d WHERE d.knowledgeBaseId = :kbId)")
    List<KnowledgeDocumentChunk> searchByKeyword(@Param("kbId") String kbId, @Param("keyword") String keyword);

    /**
     * 全局关键词检索 (跨所有知识库)
     */
    @Query("SELECT c FROM KnowledgeDocumentChunk c WHERE c.content LIKE %:keyword%")
    List<KnowledgeDocumentChunk> searchAllByKeyword(@Param("keyword") String keyword);

    /**
     * 删除文档的所有分片
     */
    void deleteByDocumentId(String documentId);

    /**
     * 根据文档 ID 查询指定类型的 chunks
     */
    List<KnowledgeDocumentChunk> findByDocumentIdAndChunkType(String documentId, String chunkType);
}
