package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.GraphRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GraphRelationRepository extends JpaRepository<GraphRelation, String> {
    List<GraphRelation> findByGraphId(String graphId);
    List<GraphRelation> findByGraphIdAndRelationType(String graphId, String relationType);
    long countByGraphId(String graphId);
}
