package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.GraphEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GraphEntityRepository extends JpaRepository<GraphEntity, String> {
    Page<GraphEntity> findByGraphId(String graphId, Pageable pageable);
    List<GraphEntity> findByGraphId(String graphId);
    List<GraphEntity> findByGraphIdAndEntityType(String graphId, String entityType);
    List<GraphEntity> findByGraphIdAndNameContainingIgnoreCase(String graphId, String name);
    long countByGraphId(String graphId);
    void deleteByGraphId(String graphId);
}
