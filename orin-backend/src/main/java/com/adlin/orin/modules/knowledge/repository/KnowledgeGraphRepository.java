package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.KnowledgeGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeGraphRepository extends JpaRepository<KnowledgeGraph, String> {
    List<KnowledgeGraph> findByNameContainingIgnoreCase(String name);
    List<KnowledgeGraph> findByKnowledgeBaseId(String knowledgeBaseId);
    boolean existsByKnowledgeBaseId(String knowledgeBaseId);
}
