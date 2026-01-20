package com.adlin.orin.modules.knowledge.repository.meta;

import com.adlin.orin.modules.knowledge.entity.meta.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, String> {
    List<PromptTemplate> findByAgentId(String agentId);
}
