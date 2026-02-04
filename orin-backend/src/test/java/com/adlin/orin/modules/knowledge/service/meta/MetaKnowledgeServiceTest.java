package com.adlin.orin.modules.knowledge.service.meta;

import com.adlin.orin.modules.knowledge.repository.meta.AgentMemoryRepository;
import com.adlin.orin.modules.knowledge.repository.meta.PromptTemplateRepository;
import com.adlin.orin.modules.skill.entity.SkillEntity;
import com.adlin.orin.modules.skill.repository.SkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaKnowledgeServiceTest {

    @Mock
    private PromptTemplateRepository promptTemplateRepository;

    @Mock
    private AgentMemoryRepository agentMemoryRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SkillRepository skillRepository;

    @InjectMocks
    private MetaKnowledgeService metaKnowledgeService;

    @Test
    void testAssembleSystemPrompt_WithShellSkills() {
        // Given
        String agentId = "agent-123";

        // Mock Shell Skills
        SkillEntity shellSkill = SkillEntity.builder()
                .skillName("CheckDisk")
                .skillType(SkillEntity.SkillType.SHELL)
                .description("Checks disk space")
                .shellCommand("df -h")
                .build();

        when(skillRepository.findBySkillType(SkillEntity.SkillType.SHELL))
                .thenReturn(Collections.singletonList(shellSkill));

        when(promptTemplateRepository.findByAgentId(agentId)).thenReturn(Collections.emptyList());
        when(agentMemoryRepository.findByAgentId(agentId)).thenReturn(Collections.emptyList());

        // When
        String result = metaKnowledgeService.assembleSystemPrompt(agentId);

        // Then
        assertThat(result).contains("## System Capabilities");
        assertThat(result).contains("Suggest Command: <ToolName> <params>");
        assertThat(result).contains("- **CheckDisk**: Checks disk space");
        assertThat(result).contains("- Template: `df -h`");
    }

    @Test
    void testAssembleSystemPrompt_NoShellSkills() {
        // Given
        String agentId = "agent-456";

        when(skillRepository.findBySkillType(SkillEntity.SkillType.SHELL)).thenReturn(Collections.emptyList());
        when(promptTemplateRepository.findByAgentId(agentId)).thenReturn(Collections.emptyList());
        when(agentMemoryRepository.findByAgentId(agentId)).thenReturn(Collections.emptyList());

        // When
        String result = metaKnowledgeService.assembleSystemPrompt(agentId);

        // Then
        assertThat(result).doesNotContain("## System Capabilities");
    }
}
