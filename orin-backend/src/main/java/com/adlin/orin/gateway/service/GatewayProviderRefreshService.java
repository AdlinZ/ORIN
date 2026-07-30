package com.adlin.orin.gateway.service;

import com.adlin.orin.gateway.adapter.impl.DifyProviderAdapter;
import com.adlin.orin.gateway.adapter.impl.OllamaProviderAdapter;
import com.adlin.orin.gateway.adapter.impl.OpenAIProviderAdapter;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
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
    private static final String DEFAULT_SILICONFLOW_ENDPOINT = "https://api.siliconflow.cn/v1";

    private final ProviderRegistry providerRegistry;
    private final DifyIntegrationService difyIntegrationService;
    private final GatewaySecretService gatewaySecretService;
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

        log.info("UnifiedGateway providers refreshed, total={}", providerRegistry.getAllProviders().size());
    }

    private void registerOllama(ModelConfig config) {
        if (!isConfigured(config.getOllamaEndpoint())) {
            return;
        }

        String baseUrl = config.getOllamaEndpoint().trim();
        var ollamaCredential = gatewaySecretService.resolveProviderCredential(PROVIDER_OLLAMA)
                .or(() -> gatewaySecretService.resolveProviderCredential("ollama"))
                .orElse(null);
        if (ollamaCredential != null && isConfigured(ollamaCredential.getBaseUrl())) {
            baseUrl = ollamaCredential.getBaseUrl().trim();
        }
        String apiKey = ollamaCredential != null ? ollamaCredential.getApiKey() : "";
        OllamaProviderAdapter ollamaAdapter = new OllamaProviderAdapter(PROVIDER_OLLAMA, apiKey, baseUrl, restTemplate);
        providerRegistry.registerProvider(PROVIDER_OLLAMA, ollamaAdapter);
        log.info("Registered Ollama provider at {}", baseUrl);
    }

    private void registerSiliconFlow(ModelConfig config) {
        var credentialOpt = gatewaySecretService.resolveProviderCredential(PROVIDER_SILICONFLOW);
        if (credentialOpt.isEmpty() || !isConfigured(credentialOpt.get().getApiKey())) {
            log.warn("Skip siliconflow provider registration: missing credential in gateway secret center");
            return;
        }
        String apiKey = credentialOpt.get().getApiKey().trim();
        String endpoint = isConfigured(credentialOpt.get().getBaseUrl())
                ? credentialOpt.get().getBaseUrl().trim()
                : isConfigured(config.getSiliconFlowEndpoint())
                        ? config.getSiliconFlowEndpoint().trim()
                        : DEFAULT_SILICONFLOW_ENDPOINT;

        OpenAIProviderAdapter siliconFlowAdapter = new OpenAIProviderAdapter(
                PROVIDER_SILICONFLOW,
                apiKey,
                endpoint,
                restTemplate);
        providerRegistry.registerProvider(PROVIDER_SILICONFLOW, siliconFlowAdapter);
        log.info("Registered OpenAI-compatible provider: {} at {}", PROVIDER_SILICONFLOW, endpoint);
    }

    private void registerDify(ModelConfig config) {
        if (!isConfigured(config.getDifyEndpoint())) {
            return;
        }
        var credentialOpt = gatewaySecretService.resolveProviderCredential(PROVIDER_DIFY);
        if (credentialOpt.isEmpty() || !isConfigured(credentialOpt.get().getApiKey())) {
            log.warn("Skip dify provider registration: missing credential in gateway secret center");
            return;
        }

        String endpoint = isConfigured(credentialOpt.get().getBaseUrl())
                ? credentialOpt.get().getBaseUrl().trim()
                : config.getDifyEndpoint().trim();
        DifyProviderAdapter difyAdapter = new DifyProviderAdapter(
                PROVIDER_DIFY,
                endpoint,
                credentialOpt.get().getApiKey().trim(),
                difyIntegrationService);
        providerRegistry.registerProvider(PROVIDER_DIFY, difyAdapter);
        log.info("Registered Dify provider at {}", endpoint);
    }

    private boolean isConfigured(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
