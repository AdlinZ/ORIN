package com.adlin.orin.modules.knowledge.service.impl;

import com.adlin.orin.modules.knowledge.component.KnowledgeWorkflowEngine;
import com.adlin.orin.modules.knowledge.entity.KnowledgeSkill;
import com.adlin.orin.modules.knowledge.repository.KnowledgeSkillRepository;
import com.adlin.orin.modules.knowledge.service.ProceduralService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProceduralServiceImpl implements ProceduralService {

    private final KnowledgeSkillRepository skillRepository;
    private final KnowledgeWorkflowEngine workflowExecutor;

    @Override
    @Transactional
    public void deleteAgentSkills(String agentId) {
        log.info("Deleting all skills for agent {}", agentId);
        skillRepository.deleteByAgentId(agentId);
    }

    @Override
    public List<KnowledgeSkill> getAgentSkills(String agentId) {
        return skillRepository.findByAgentId(agentId);
    }

    @Override
    @Transactional
    public void registerSkill(String agentId, String workflowDsl, String triggerName) {
        log.info("Registering skill '{}' for agent {}", triggerName, agentId);

        KnowledgeSkill skill = skillRepository.findByAgentIdAndTriggerName(agentId, triggerName)
                .orElse(KnowledgeSkill.builder()
                        .agentId(agentId)
                        .triggerName(triggerName)
                        .name(triggerName) // Default Name
                        .build());

        skill.setDefinition(workflowDsl);
        skill.setUpdatedAt(LocalDateTime.now());

        skillRepository.save(skill);
    }

    @Override
    public Map<String, Object> triggerSkill(String agentId, String triggerName, Map<String, Object> inputParams) {
        log.info("Triggering skill '{}'", triggerName);

        KnowledgeSkill skill = skillRepository.findByAgentIdAndTriggerName(agentId, triggerName)
                .orElseThrow(() -> new RuntimeException("Skill not found: " + triggerName));

        String dsl = skill.getDefinition();
        if (dsl == null || dsl.isEmpty()) {
            throw new RuntimeException("Skill has no definition logic");
        }

        // Delegate execution to Engine
        return workflowExecutor.execute(dsl, inputParams);
    }

    @Override
    public Map<String, Object> getSkillDefinition(String agentId, String triggerName) {
        KnowledgeSkill skill = skillRepository.findByAgentIdAndTriggerName(agentId, triggerName)
                .orElseThrow(() -> new RuntimeException("Skill not found: " + triggerName));

        // Return JSON Schema for tool calling
        Map<String, Object> definition = new HashMap<>();
        definition.put("name", skill.getTriggerName());
        definition.put("description",
                skill.getDescription() != null ? skill.getDescription() : "Dynamically registered skill");
        definition.put("parameters", new HashMap<>());
        return definition;
    }

    @Override
    @Transactional
    public void deleteSkill(String skillId) {
        log.info("Deleting skill {}", skillId);
        skillRepository.deleteById(skillId);
    }

    @Override
    @Transactional
    public void updateSkill(KnowledgeSkill skill) {
        log.info("Updating skill {} ({})", skill.getName(), skill.getId());
        if (skill.getCreatedAt() == null) {
            skill.setCreatedAt(LocalDateTime.now());
        }
        skill.setUpdatedAt(LocalDateTime.now());
        skillRepository.save(skill);
    }
}
