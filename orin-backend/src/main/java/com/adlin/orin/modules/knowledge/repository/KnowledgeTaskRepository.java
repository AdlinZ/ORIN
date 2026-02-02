package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.KnowledgeTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeTaskRepository extends JpaRepository<KnowledgeTask, String> {
    List<KnowledgeTask> findByStatus(String status);

    List<KnowledgeTask> findByAssetIdAndAssetType(String assetId, String assetType);
}
