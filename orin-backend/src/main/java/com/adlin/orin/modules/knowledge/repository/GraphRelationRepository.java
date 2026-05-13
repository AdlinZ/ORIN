package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.GraphRelation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Repository
public interface GraphRelationRepository extends JpaRepository<GraphRelation, String> {
    Page<GraphRelation> findByGraphId(String graphId, Pageable pageable);
    List<GraphRelation> findByGraphId(String graphId);
    List<GraphRelation> findByGraphIdAndRelationType(String graphId, String relationType);
    @Query("SELECT r FROM GraphRelation r WHERE r.graphId = :graphId AND (r.sourceEntityId IN :entityIds OR r.targetEntityId IN :entityIds)")
    List<GraphRelation> findByGraphIdAndEntityIds(
            @Param("graphId") String graphId,
            @Param("entityIds") Set<String> entityIds,
            Pageable pageable);
    long countByGraphId(String graphId);
    @Transactional
    void deleteByGraphId(String graphId);
}
