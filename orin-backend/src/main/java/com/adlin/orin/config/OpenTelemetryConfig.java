package com.adlin.orin.config;

import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry 配置 - 暂时禁用，等待依赖问题修复
 * 如需启用 OTLP 导出，请添加正确的依赖并配置
 */
@Configuration
public class OpenTelemetryConfig {
    // TODO: 后续需要时重新实现 OpenTelemetry 配置
    // 当前移除以避免依赖问题影响编译
}