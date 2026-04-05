package com.adlin.orin.gateway.service;

import com.adlin.orin.gateway.adapter.impl.DifyProviderAdapter;
import com.adlin.orin.gateway.adapter.impl.OllamaProviderAdapter;
import com.adlin.orin.gateway.adapter.impl.OpenAIProviderAdapter;
import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import com.adlin.orin.modules.model.entity.ModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 统一网关 Provider 刷新服务
 * 用于启动阶段与配置更新后的 Provider 重建
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayProviderRefreshService {

    private static final String PROVIDER_OLLAMA = "local-ollama";
    private static final String PROVIDER_SILICONFLOW = "siliconflow";
    private static final String PROVIDER_DIFY = "dify";

    private final ProviderRegistry providerRegistry;
    private final DifyIntegrationService difyIntegrationService;
    private final RestTemplate restTemplate = new RestTemplate();

    public void refreshFromConfig(ModelConfig config) {
        if (config == null) {
            log.warn("Skip provider refresh because model config is null");
            return;
        }

        // 清理由统一网关托管的 provider，再按新配置重建
        providerRegistry.unregisterProvider(PROVIDER_OLLAMA);
        providerRegistry.unregisterProvider(PROVIDER_SILICONFLOW);
        providerRegistry.unregisterProvider(PROVIDER_DIFY);

        registerOllama(config);
        registerSiliconFlow(config);
        registerDify(config);

        log.info("Gateway providers refreshed, total={}", providerRegistry.getAllProviders().size());
    }

    private void registerOllama(ModelConfig config) {
        if (!isConfigured(config.getOllamaEndpoint())) {
            return;
        }

        String baseUrl = config.getOllamaEndpoint().trim();
        OllamaProviderAdapter ollamaAdapter = new OllamaProviderAdapter(PROVIDER_OLLAMA, baseUrl, restTemplate);
        providerRegistry.registerProvider(PROVIDER_OLLAMA, ollamaAdapter);
        log.info("Registered Ollama provider at {}", baseUrl);
    }

    private void registerSiliconFlow(ModelConfig config) {
        if (!isConfigured(config.getSiliconFlowEndpoint()) || !isConfigured(config.getSiliconFlowApiKey())) {
            return;
        }

        OpenAIProviderAdapter siliconFlowAdapter = new OpenAIProviderAdapter(
                PROVIDER_SILICONFLOW,
                config.getSiliconFlowApiKey().trim(),
                config.getSiliconFlowEndpoint().trim(),
                restTemplate);
        providerRegistry.registerProvider(PROVIDER_SILICONFLOW, siliconFlowAdapter);
        log.info("Registered OpenAI-compatible provider: {} at {}", PROVIDER_SILICONFLOW, config.getSiliconFlowEndpoint());
    }

    private void registerDify(ModelConfig config) {
        if (!isConfigured(config.getDifyEndpoint()) || !isConfigured(config.getDifyApiKey())) {
            return;
        }

        DifyProviderAdapter difyAdapter = new DifyProviderAdapter(
                PROVIDER_DIFY,
                config.getDifyEndpoint().trim(),
                config.getDifyApiKey().trim(),
                difyIntegrationService);
        providerRegistry.registerProvider(PROVIDER_DIFY, difyAdapter);
        log.info("Registered Dify provider at {}", config.getDifyEndpoint());
    }

    private boolean isConfigured(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
