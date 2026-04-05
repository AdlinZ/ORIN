package com.adlin.orin.gateway.service;

import com.adlin.orin.modules.model.entity.ModelConfig;
import com.adlin.orin.modules.model.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Provider 初始化器
 * 在应用启动时，根据系统配置自动注册 Provider 适配器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderInitializer implements CommandLineRunner {

    private final ModelConfigService modelConfigService;
    private final GatewayProviderRefreshService gatewayProviderRefreshService;

    @Override
    public void run(String... args) {
        log.info("Initializing providers from system configuration...");

        try {
            ModelConfig config = modelConfigService.getConfig();
            gatewayProviderRefreshService.refreshFromConfig(config);

        } catch (Exception e) {
            log.error("Failed to initialize providers during startup: {}", e.getMessage());
        }
    }
}
