package com.adlin.orin.gateway.service;

import com.adlin.orin.gateway.adapter.impl.OllamaProviderAdapter;
import com.adlin.orin.modules.model.entity.ModelConfig;
import com.adlin.orin.modules.model.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Provider 初始化器
 * 在应用启动时，根据系统配置自动注册 Provider 适配器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderInitializer implements CommandLineRunner {

    private final ProviderRegistry providerRegistry;
    private final ModelConfigService modelConfigService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void run(String... args) {
        log.info("Initializing providers from system configuration...");

        try {
            ModelConfig config = modelConfigService.getConfig();

            // 1. Initialize Ollama
            if (config.getOllamaEndpoint() != null && !config.getOllamaEndpoint().isEmpty()) {
                String baseUrl = config.getOllamaEndpoint();

                OllamaProviderAdapter ollamaAdapter = new OllamaProviderAdapter(
                        "local-ollama",
                        baseUrl,
                        restTemplate);

                providerRegistry.registerProvider("local-ollama", ollamaAdapter);
                log.info("Registered Ollama provider at {}", baseUrl);
            }

            // TODO: Initialize other providers (e.g. vLLM, local OpenAI proxies) here

        } catch (Exception e) {
            log.error("Failed to initialize providers during startup: {}", e.getMessage());
        }
    }
}
