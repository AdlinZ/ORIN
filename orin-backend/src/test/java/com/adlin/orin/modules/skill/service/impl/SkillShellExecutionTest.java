package com.adlin.orin.modules.skill.service.impl;

import com.adlin.orin.modules.skill.entity.SkillEntity;
import com.adlin.orin.modules.skill.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillShellExecutionTest {

    @Mock
    private SkillRepository skillRepository;

    @InjectMocks
    private SkillServiceImplEnhanced skillService;

    @BeforeEach
    void setUp() {
        // Mocks for other dependencies are not strictly needed for executeShellSkill
        // providing they are not called.
        // shellService uses: skillRepository, restTemplate, milvusVectorService,
        // flowExecutor
        // They are all mocked by @InjectMocks if defined as @Mock.
        // Check if I need to define them to avoid null pointers during context loading?
        // With MockitoExtension, @Mock creates mocks and @InjectMocks injects them.
        // It should be fine.
    }

    @Test
    void testExecuteShellSkill_Success() {
        // Given
        Long skillId = 1L;
        String command = "echo Hello ${name}";
        SkillEntity skill = SkillEntity.builder()
                .id(skillId)
                .skillName("Echo Skill")
                .skillType(SkillEntity.SkillType.SHELL)
                .shellCommand(command)
                .build();

        when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("name", "World");

        // When
        Map<String, Object> result = skillService.executeSkill(skillId, inputs);

        // Then
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("exitCode")).isEqualTo(0);
        String stdout = (String) result.get("stdout");
        assertThat(stdout).contains("Hello World");
    }

    @Test
    void testExecuteShellSkill_CommandFailed() {
        // Given
        Long skillId = 2L;
        // Basic command that fails
        String command = "exit 1";
        SkillEntity skill = SkillEntity.builder()
                .id(skillId)
                .skillName("Fail Skill")
                .skillType(SkillEntity.SkillType.SHELL)
                .shellCommand(command)
                .build();

        when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));

        // When
        Map<String, Object> result = skillService.executeSkill(skillId, new HashMap<>());

        // Then
        // Usually exit code is non-zero (e.g. 1 or 2)
        // success might be false depending on implementation.
        // My implementation sets success=true only if exitCode==0.
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("exitCode")).isNotEqualTo(0);
        // assertThat((String) result.get("stderr")).contains("No such file");
    }
}
