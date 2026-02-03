package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.KnowledgeSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface KnowledgeSkillRepository extends JpaRepository<KnowledgeSkill, String> {
    List<KnowledgeSkill> findByAgentId(String agentId);

    Optional<KnowledgeSkill> findByAgentIdAndTriggerName(String agentId, String triggerName);

    void deleteByAgentId(String agentId);
}
