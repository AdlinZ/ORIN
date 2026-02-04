package com.adlin.orin.modules.skill.component;

import com.adlin.orin.modules.skill.entity.SkillEntity;
import com.adlin.orin.modules.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillInitializer implements CommandLineRunner {

    private final SkillRepository skillRepository;
    private final com.adlin.orin.modules.monitor.job.AgentStatusUpdater agentStatusUpdater;

    @Override
    public void run(String... args) throws Exception {
        initializeShellSkills();
        agentStatusUpdater.heartbeatAgents(); // Force immediate update
    }

    private void initializeShellSkills() {
        createSkillIfNotFound("ListFiles",
                "List files in a directory. Usage: ListFiles path=/tmp",
                "ls -la ${path}",
                "{\"path\": \"directory path (default: .)\"}");

        createSkillIfNotFound("ReadFile",
                "Read file content. Usage: ReadFile path=/tmp/file.txt",
                "cat ${path}",
                "{\"path\": \"absolute file path\"}");

        createSkillIfNotFound("CheckDisk",
                "Check disk usage.",
                "df -h",
                "{}");

        createSkillIfNotFound("CheckProcesses",
                "List top running processes.",
                "ps aux | head -n 20",
                "{}");

        createSkillIfNotFound("GrepSearch",
                "Search for text in files.",
                "grep -r \"${text}\" ${path}",
                "{\"text\": \"text to search\", \"path\": \"directory to search\"}");
    }

    private void createSkillIfNotFound(String name, String description, String command, String inputSchemaJson) {
        if (!skillRepository.existsBySkillName(name)) {
            log.info("Seeding SHELL skill: {}", name);
            try {
                // Parse simple JSON for schema (mocking map creation for simplicity or use
                // ObjectMapper)
                // For initializer simplicity, we will just rely on the entity builder

                SkillEntity skill = SkillEntity.builder()
                        .skillName(name)
                        .skillType(SkillEntity.SkillType.SHELL)
                        .description(description)
                        .shellCommand(command)
                        .status(SkillEntity.SkillStatus.ACTIVE)
                        .version("1.0.0")
                        .createdBy("system")
                        .build();

                // We'd ideally parse inputSchemaJson to Map, but SkillEntity uses Map.
                // For now, let's leave inputSchema empty or basic map in real implementation.

                skillRepository.save(skill);
            } catch (Exception e) {
                log.error("Failed to seed skill {}: {}", name, e.getMessage());
            }
        }
    }
}
