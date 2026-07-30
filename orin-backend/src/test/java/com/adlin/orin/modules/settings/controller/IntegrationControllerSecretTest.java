package com.adlin.orin.modules.settings.controller;

import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import com.adlin.orin.modules.knowledge.service.GraphExtractionService;
import com.adlin.orin.modules.knowledge.service.RAGFlowIntegrationService;
import com.adlin.orin.modules.settings.support.SecretConfigSanitizer;
import com.adlin.orin.modules.system.entity.SystemConfigEntity;
import com.adlin.orin.modules.system.repository.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationControllerSecretTest {

    private SystemConfigRepository repository;
    private IntegrationController controller;
    private final Map<String, SystemConfigEntity> persisted = new HashMap<>();

    @BeforeEach
    void setUp() {
        repository = mock(SystemConfigRepository.class);
        when(repository.findByConfigKey(any())).thenAnswer(invocation ->
                Optional.ofNullable(persisted.get(invocation.getArgument(0))));
        when(repository.save(any())).thenAnswer(invocation -> {
            SystemConfigEntity entity = invocation.getArgument(0);
            persisted.put(entity.getConfigKey(), entity);
            return entity;
        });
        controller = new IntegrationController(
                mock(DifyIntegrationService.class),
                mock(RAGFlowIntegrationService.class),
                repository,
                mock(GraphExtractionService.class)
        );
    }

    @Test
    void neverReturnsDifyOrNeo4jSecrets() {
        put("dify.apiKey", "real-dify-key");
        put("neo4j.password", "real-neo4j-password");

        assertThat(controller.getDifyConfig().getBody())
                .containsEntry("apiKey", SecretConfigSanitizer.MASKED_VALUE)
                .doesNotContainValue("real-dify-key");
        assertThat(controller.getNeo4jConfig().getBody())
                .containsEntry("password", SecretConfigSanitizer.MASKED_VALUE)
                .doesNotContainValue("real-neo4j-password");
    }

    @Test
    void submittingTheMaskPreservesExistingSecrets() {
        put("dify.apiKey", "real-dify-key");
        put("neo4j.password", "real-neo4j-password");

        controller.saveDifyConfig(Map.of(
                "apiUrl", "https://dify.example.test/v1",
                "apiKey", SecretConfigSanitizer.MASKED_VALUE,
                "enabled", true
        ));
        controller.saveNeo4jConfig(Map.of(
                "password", SecretConfigSanitizer.MASKED_VALUE,
                "enabled", true
        ));

        assertThat(persisted.get("dify.apiKey").getConfigValue()).isEqualTo("real-dify-key");
        assertThat(persisted.get("neo4j.password").getConfigValue()).isEqualTo("real-neo4j-password");
        verify(repository, never()).save(persisted.get("dify.apiKey"));
        verify(repository, never()).save(persisted.get("neo4j.password"));
    }

    private void put(String key, String value) {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        persisted.put(key, entity);
    }
}
