package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.GraphRelation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface GraphRelationRepository extends JpaRepository<GraphRelation, String> {
    Page<GraphRelation> findByGraphId(String graphId, Pageable pageable);
    List<GraphRelation> findByGraphId(String graphId);
    List<GraphRelation> findByGraphIdAndRelationType(String graphId, String relationType);
    long countByGraphId(String graphId);
    @Transactional
    void deleteByGraphId(String graphId);
}
