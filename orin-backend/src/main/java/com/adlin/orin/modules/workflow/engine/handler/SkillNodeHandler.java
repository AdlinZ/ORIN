package com.adlin.orin.modules.workflow.engine.handler;

import com.adlin.orin.modules.skill.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

@Slf4j
@Component("skillNodeHandler")
@RequiredArgsConstructor
public class SkillNodeHandler implements NodeHandler {

    private final SkillService skillService;

    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        Long skillId = getLongValue(nodeData, "skillId");
        if (skillId == null) {
            throw new IllegalArgumentException("Skill ID required for Skill Node");
        }

        log.info("SkillNode executing skillId={}", skillId);
        Map<String, Object> result = skillService.executeSkill(skillId, context);
        return NodeExecutionResult.success(result);
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null)
            return null;
        if (value instanceof Number)
            return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
